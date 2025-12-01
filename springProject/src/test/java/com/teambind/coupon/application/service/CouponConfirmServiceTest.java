package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ConfirmCouponCommand;
import com.teambind.coupon.application.port.in.ConfirmCouponUseCase.CouponConfirmResult;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SendEventPort;
import com.teambind.coupon.domain.event.CouponUsedEvent;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.fixture.CouponIssueFixture;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 확정 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("쿠폰 확정 서비스 테스트")
class CouponConfirmServiceTest {

    @InjectMocks
    private CouponConfirmService confirmService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SendEventPort sendEventPort;

    private CouponPolicy policy;
    private CouponIssue reservedCoupon;
    private String orderId;
    private String reservationId;

    @BeforeEach
    void setUp() {
        policy = CouponPolicyFixture.createCodePolicy();
        orderId = UUID.randomUUID().toString();
        reservationId = UUID.randomUUID().toString();
        reservedCoupon = CouponIssueFixture.createReservedCoupon(100L, policy, reservationId);
    }

    @Nested
    @DisplayName("정상 확정 케이스")
    class SuccessConfirmTest {

        @Test
        @DisplayName("예약된 쿠폰 사용 확정")
        void confirmReservedCoupon() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, reservedCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCouponId()).isEqualTo(reservedCoupon.getId());
            assertThat(result.getOrderId()).isEqualTo(orderId);
            assertThat(result.getActualDiscountAmount()).isEqualTo(new BigDecimal("5000"));

            verify(saveCouponIssuePort).save(argThat(coupon ->
                coupon.getStatus() == CouponStatus.USED &&
                coupon.getOrderId().equals(orderId) &&
                coupon.getActualDiscountAmount().equals(new BigDecimal("5000"))
            ));

            verify(sendEventPort).send(any(CouponUsedEvent.class));
        }

        @Test
        @DisplayName("멱등성 보장 - 이미 사용된 쿠폰")
        void idempotentConfirm() {
            // given
            CouponIssue usedCoupon = CouponIssueFixture.createUsedCoupon(100L, policy, orderId);
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, usedCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(usedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(usedCoupon));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("이미 사용됨");
            verify(saveCouponIssuePort, never()).save(any());
            verify(sendEventPort, never()).send(any());
        }

        @Test
        @DisplayName("할인 금액 계산 - 정률 할인")
        void calculatePercentageDiscount() {
            // given
            CouponPolicy percentPolicy = CouponPolicyFixture.createPercentagePolicy();
            CouponIssue coupon = CouponIssueFixture.createReservedCoupon(100L, percentPolicy, reservationId);

            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, coupon.getId(), orderId,
                    new BigDecimal("100000"), null
            );

            when(loadCouponIssuePort.loadByIdAndUserId(coupon.getId(), 100L))
                    .thenReturn(Optional.of(coupon));
            when(loadCouponPolicyPort.loadById(percentPolicy.getId()))
                    .thenReturn(Optional.of(percentPolicy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            // 10% 할인, 최대 5000원
            assertThat(result.getActualDiscountAmount()).isEqualTo(new BigDecimal("5000"));
        }

        @Test
        @DisplayName("할인 금액 계산 - 정액 할인")
        void calculateFixedDiscount() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, reservedCoupon.getId(), orderId,
                    new BigDecimal("30000"), null
            );

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            // 정액 3000원 할인
            assertThat(result.getActualDiscountAmount()).isEqualTo(new BigDecimal("3000"));
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureConfirmTest {

        @Test
        @DisplayName("쿠폰을 찾을 수 없음")
        void couponNotFound() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, 9999L, orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(9999L, 100L))
                    .thenReturn(Optional.empty());

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("찾을 수 없음");
            verify(saveCouponIssuePort, never()).save(any());
        }

        @Test
        @DisplayName("예약 ID 불일치")
        void reservationIdMismatch() {
            // given
            String wrongReservationId = "wrong-reservation";
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    wrongReservationId, 100L, reservedCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약 ID가 일치하지 않음");
        }

        @Test
        @DisplayName("예약되지 않은 쿠폰 사용 시도")
        void confirmNonReservedCoupon() {
            // given
            CouponIssue issuedCoupon = CouponIssueFixture.createIssuedCoupon(100L, policy);
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, issuedCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약된 상태가 아님");
        }

        @Test
        @DisplayName("최소 주문 금액 미달")
        void orderAmountBelowMinimum() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, reservedCoupon.getId(), orderId,
                    new BigDecimal("5000"), null // 최소 주문금액 10000원 미달
            );

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("최소 주문 금액");
        }

        @Test
        @DisplayName("만료된 쿠폰 사용 시도")
        void confirmExpiredCoupon() {
            // given
            CouponIssue expiredCoupon = CouponIssueFixture.createExpiredCoupon(100L, policy);
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, expiredCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(expiredCoupon.getId(), 100L))
                    .thenReturn(Optional.of(expiredCoupon));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("만료된 쿠폰");
        }

        @Test
        @DisplayName("취소된 쿠폰 사용 시도")
        void confirmCancelledCoupon() {
            // given
            CouponIssue cancelledCoupon = CouponIssueFixture.createCancelledCoupon(100L, policy);
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, cancelledCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(cancelledCoupon.getId(), 100L))
                    .thenReturn(Optional.of(cancelledCoupon));

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("취소된 쿠폰");
        }
    }

    @Nested
    @DisplayName("이벤트 발행 테스트")
    class EventPublishingTest {

        @Test
        @DisplayName("쿠폰 사용 이벤트 발행")
        void publishCouponUsedEvent() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, reservedCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            confirmService.confirmCoupon(command);

            // then
            verify(sendEventPort).send(argThat(event ->
                event instanceof CouponUsedEvent &&
                ((CouponUsedEvent) event).getCouponId().equals(reservedCoupon.getId()) &&
                ((CouponUsedEvent) event).getOrderId().equals(orderId) &&
                ((CouponUsedEvent) event).getUserId().equals(100L) &&
                ((CouponUsedEvent) event).getDiscountAmount().equals(new BigDecimal("5000"))
            ));
        }

        @Test
        @DisplayName("실패 시 이벤트 미발행")
        void noEventOnFailure() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, 9999L, orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(9999L, 100L))
                    .thenReturn(Optional.empty());

            // when
            confirmService.confirmCoupon(command);

            // then
            verify(sendEventPort, never()).send(any());
        }
    }

    @Nested
    @DisplayName("트랜잭션 롤백 시나리오")
    class TransactionRollbackTest {

        @Test
        @DisplayName("저장 실패 시 롤백")
        void rollbackOnSaveFailure() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, reservedCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenThrow(new RuntimeException("DB Error"));

            // when & then
            assertThatThrownBy(() ->
                confirmService.confirmCoupon(command)
            )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("DB Error");

            verify(sendEventPort, never()).send(any());
        }

        @Test
        @DisplayName("이벤트 발행 실패 처리")
        void handleEventPublishingFailure() {
            // given
            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, 100L, reservedCoupon.getId(), orderId,
                    new BigDecimal("50000"), new BigDecimal("5000")
            );

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Event Error"))
                    .when(sendEventPort).send(any());

            // when
            CouponConfirmResult result = confirmService.confirmCoupon(command);

            // then - 이벤트 발행 실패는 결과에 영향 없음 (비동기 처리)
            assertThat(result.isSuccess()).isTrue();
            verify(sendEventPort).sendAsync(any(CouponUsedEvent.class));
        }
    }
}
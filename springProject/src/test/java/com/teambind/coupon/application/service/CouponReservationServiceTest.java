package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ReserveCouponCommand;
import com.teambind.coupon.application.port.in.ReserveCouponUseCase.CouponReservationResult;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 예약 서비스 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("쿠폰 예약 서비스 테스트")
class CouponReservationServiceTest {

    @InjectMocks
    private CouponReservationService reservationService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    private CouponPolicy policy;
    private CouponIssue issuedCoupon;
    private ReserveCouponCommand command;

    @BeforeEach
    void setUp() {
        policy = CouponPolicyFixture.createCodePolicy();
        issuedCoupon = CouponIssueFixture.createIssuedCoupon(100L, policy);
    }

    @Nested
    @DisplayName("정상 예약 케이스")
    class SuccessCase {

        @Test
        @DisplayName("발급된 쿠폰 예약 성공")
        void reserveIssuedCoupon() {
            // given
            String reservationId = UUID.randomUUID().toString();
            command = ReserveCouponCommand.of(reservationId, 100L, issuedCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCouponId()).isEqualTo(issuedCoupon.getId());
            assertThat(result.getReservationId()).isEqualTo(reservationId);
            assertThat(result.getMessage()).contains("예약 성공");

            verify(saveCouponIssuePort).save(argThat(coupon ->
                    coupon.getStatus() == CouponStatus.RESERVED &&
                    coupon.getReservationId().equals(reservationId)
            ));
        }

        @Test
        @DisplayName("동일 예약 ID로 재시도시 멱등성 보장")
        void idempotentReservation() {
            // given
            String reservationId = UUID.randomUUID().toString();
            CouponIssue reservedCoupon = CouponIssueFixture.createReservedCoupon(100L, policy, reservationId);
            command = ReserveCouponCommand.of(reservationId, 100L, reservedCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));

            // when - 동일한 예약 ID로 재시도
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("이미 예약됨");
            verify(saveCouponIssuePort, never()).save(any()); // 저장하지 않음
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCase {

        @Test
        @DisplayName("존재하지 않는 쿠폰 예약 시도")
        void reserveNonExistentCoupon() {
            // given
            command = ReserveCouponCommand.of("reservation-123", 100L, 9999L);
            when(loadCouponIssuePort.loadByIdAndUserId(9999L, 100L))
                    .thenReturn(Optional.empty());

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("쿠폰을 찾을 수 없음");
            verify(saveCouponIssuePort, never()).save(any());
        }

        @Test
        @DisplayName("다른 사용자의 쿠폰 예약 시도")
        void reserveOtherUserCoupon() {
            // given
            CouponIssue otherUserCoupon = CouponIssueFixture.createIssuedCoupon(200L, policy);
            command = ReserveCouponCommand.of("reservation-123", 100L, otherUserCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(otherUserCoupon.getId(), 100L))
                    .thenReturn(Optional.empty()); // 다른 사용자의 쿠폰이므로 조회 안됨

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("쿠폰을 찾을 수 없음");
        }

        @Test
        @DisplayName("이미 다른 예약 ID로 예약된 쿠폰")
        void alreadyReservedWithDifferentId() {
            // given
            String existingReservationId = "existing-reservation";
            CouponIssue reservedCoupon = CouponIssueFixture.createReservedCoupon(100L, policy, existingReservationId);
            command = ReserveCouponCommand.of("new-reservation", 100L, reservedCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태입니다");
            verify(saveCouponIssuePort, never()).save(any());
        }

        @Test
        @DisplayName("이미 사용된 쿠폰 예약 시도")
        void reserveUsedCoupon() {
            // given
            CouponIssue usedCoupon = CouponIssueFixture.createUsedCoupon(100L, policy, "order-123");
            command = ReserveCouponCommand.of("reservation-123", 100L, usedCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(usedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(usedCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태입니다");
        }

        @Test
        @DisplayName("만료된 쿠폰 예약 시도")
        void reserveExpiredCoupon() {
            // given
            CouponIssue expiredCoupon = CouponIssueFixture.createExpiredCoupon(100L, policy);
            command = ReserveCouponCommand.of("reservation-123", 100L, expiredCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(expiredCoupon.getId(), 100L))
                    .thenReturn(Optional.of(expiredCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태입니다");
        }

        @Test
        @DisplayName("취소된 쿠폰 예약 시도")
        void reserveCancelledCoupon() {
            // given
            CouponIssue cancelledCoupon = CouponIssueFixture.createCancelledCoupon(100L, policy);
            command = ReserveCouponCommand.of("reservation-123", 100L, cancelledCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(cancelledCoupon.getId(), 100L))
                    .thenReturn(Optional.of(cancelledCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태입니다");
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동일 쿠폰에 대한 동시 예약 요청 - 단순화된 테스트")
        void concurrentReservation() throws InterruptedException {
            // 실제 동시성 테스트는 통합 테스트에서 수행
            // 단위 테스트에서는 동시성 로직의 순차적 동작만 검증

            // given - 첫 번째 예약
            String firstReservationId = "reservation-1";
            ReserveCouponCommand firstCommand = ReserveCouponCommand.of(firstReservationId, 100L, issuedCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when - 첫 번째 예약 성공
            CouponReservationResult firstResult = reservationService.reserveCoupon(firstCommand);

            // then
            assertThat(firstResult.isSuccess()).isTrue();

            // given - 두 번째 예약 시도 (이미 예약된 상태)
            issuedCoupon.reserve(firstReservationId); // 상태 변경
            String secondReservationId = "reservation-2";
            ReserveCouponCommand secondCommand = ReserveCouponCommand.of(secondReservationId, 100L, issuedCoupon.getId());

            // when - 두 번째 예약 실패
            CouponReservationResult secondResult = reservationService.reserveCoupon(secondCommand);

            // then
            assertThat(secondResult.isSuccess()).isFalse();
            assertThat(secondResult.getMessage()).contains("예약할 수 없는 상태");
        }

        @Test
        @DisplayName("여러 사용자의 서로 다른 쿠폰 예약 - 단순화된 테스트")
        void multipleCouponReservation() {
            // 실제 동시성 테스트는 통합 테스트에서 수행
            // 단위 테스트에서는 여러 사용자가 각자의 쿠폰을 예약하는 시나리오 검증

            // given & when & then - 여러 사용자가 각자의 쿠폰 예약
            for (int i = 0; i < 5; i++) {
                long userId = 100L + i;
                CouponIssue userCoupon = CouponIssueFixture.createIssuedCoupon(userId, policy);
                String reservationId = UUID.randomUUID().toString();

                when(loadCouponIssuePort.loadByIdAndUserId(userCoupon.getId(), userId))
                        .thenReturn(Optional.of(userCoupon));
                when(loadCouponPolicyPort.loadById(policy.getId()))
                        .thenReturn(Optional.of(policy));
                when(saveCouponIssuePort.save(any(CouponIssue.class)))
                        .thenAnswer(inv -> inv.getArgument(0));

                ReserveCouponCommand cmd = ReserveCouponCommand.of(reservationId, userId, userCoupon.getId());
                CouponReservationResult result = reservationService.reserveCoupon(cmd);

                // 각 사용자의 쿠폰 예약은 성공해야 함
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getMessage()).contains("예약 성공");
            }
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCase {

        @Test
        @DisplayName("빈 예약 ID 처리")
        void emptyReservationId() {
            // given
            command = ReserveCouponCommand.of("", 100L, issuedCoupon.getId());
            // 빈 예약 ID는 조기 반환되므로 mock 설정 불필요

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약 ID가 유효하지 않음");
            // 조기 반환되므로 loadCouponIssuePort는 호출되지 않음
            verify(loadCouponIssuePort, never()).loadByIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("null 예약 ID 처리")
        void nullReservationId() {
            // given
            command = ReserveCouponCommand.of(null, 100L, issuedCoupon.getId());
            // null 예약 ID는 조기 반환되므로 mock 설정 불필요

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약 ID가 유효하지 않음");
            // 조기 반환되므로 loadCouponIssuePort는 호출되지 않음
            verify(loadCouponIssuePort, never()).loadByIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("매우 긴 예약 ID 처리")
        void veryLongReservationId() {
            // given
            String longReservationId = "a".repeat(1000);
            command = ReserveCouponCommand.of(longReservationId, 100L, issuedCoupon.getId());
            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));
            when(loadCouponPolicyPort.loadById(policy.getId()))
                    .thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue(); // 길이 제한 없으면 성공
        }
    }

    @Nested
    @DisplayName("타임아웃 관련")
    class TimeoutRelated {

        @Test
        @DisplayName("타임아웃 직전 예약 성공")
        void reserveNearTimeout() {
            // given - 29분 59초 경과한 예약
            CouponIssue nearTimeoutCoupon = CouponIssueFixture.createNearTimeoutReservedCoupon(
                    100L, policy, "old-reservation"
            );
            command = ReserveCouponCommand.of("new-reservation", 100L, nearTimeoutCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(nearTimeoutCoupon.getId(), 100L))
                    .thenReturn(Optional.of(nearTimeoutCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then - 아직 타임아웃 안됨, 기존 예약 유지
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태입니다");
        }

        @Test
        @DisplayName("타임아웃된 예약 쿠폰 재예약")
        void reserveTimeoutCoupon() {
            // given - 31분 경과한 예약 (타임아웃 대상)
            CouponIssue timeoutCoupon = CouponIssueFixture.createTimeoutReservedCoupon(
                    100L, policy, "old-reservation"
            );
            command = ReserveCouponCommand.of("new-reservation", 100L, timeoutCoupon.getId());

            when(loadCouponIssuePort.loadByIdAndUserId(timeoutCoupon.getId(), 100L))
                    .thenReturn(Optional.of(timeoutCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then - 타임아웃 스케줄러가 처리하기 전까지는 예약 상태 유지
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태입니다");
        }
    }
}
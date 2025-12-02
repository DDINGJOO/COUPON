package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.LoadReservationPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveReservationPort;
import com.teambind.coupon.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponLockService 테스트
 * 트랜잭션 경계가 올바르게 적용되는지 검증
 */
@ExtendWith(MockitoExtension.class)
class CouponLockServiceTest {

    @InjectMocks
    private CouponLockService couponLockService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private LoadReservationPort loadReservationPort;

    @Mock
    private SaveReservationPort saveReservationPort;

    @Mock
    private RedisDistributedLock distributedLock;

    private CouponIssue couponIssue;
    private CouponPolicy couponPolicy;
    private CouponApplyRequest applyRequest;

    @BeforeEach
    void setUp() {
        couponIssue = CouponIssue.builder()
                .id(1001L)
                .policyId(1L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        couponPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .build();

        applyRequest = CouponApplyRequest.builder()
                .userId(100L)
                .productIds(Arrays.asList(1L, 2L))
                .orderAmount(50000L)
                .build();
    }

    @Test
    @DisplayName("트랜잭션이 적용되어 예외 발생 시 롤백되어야 한다")
    void tryLockAndApplyCoupon_shouldRollbackOnException() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(couponPolicy));

        // 저장 시 예외 발생
        doThrow(new RuntimeException("DB 오류"))
                .when(saveReservationPort).save(any(CouponReservation.class));

        // when & then
        assertThatThrownBy(() -> couponLockService.tryLockAndApplyCoupon(couponIssue, applyRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 오류");

        // 락이 해제되어야 함
        verify(distributedLock).unlock(anyString(), anyString());

        // 쿠폰 상태는 변경되지 않아야 함
        verify(saveCouponIssuePort, never()).save(any(CouponIssue.class));
    }

    @Test
    @DisplayName("정상적으로 쿠폰 적용 시 예약과 쿠폰 상태가 모두 저장되어야 한다")
    void tryLockAndApplyCoupon_shouldSaveBothReservationAndCoupon() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(couponPolicy));

        // when
        CouponApplyResponse response = couponLockService.tryLockAndApplyCoupon(couponIssue, applyRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCouponId()).isEqualTo("1001");
        assertThat(response.getCouponName()).isEqualTo("테스트 쿠폰");

        // 예약 저장 검증
        ArgumentCaptor<CouponReservation> reservationCaptor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(reservationCaptor.capture());

        CouponReservation savedReservation = reservationCaptor.getValue();
        assertThat(savedReservation.getCouponId()).isEqualTo(1001L);
        assertThat(savedReservation.getUserId()).isEqualTo(100L);
        assertThat(savedReservation.getLockValue()).isNotNull();

        // 쿠폰 저장 검증
        verify(saveCouponIssuePort).save(any(CouponIssue.class));
    }

    @Test
    @DisplayName("락 획득 실패 시 빈 응답을 반환해야 한다")
    void tryLockAndApplyCoupon_shouldReturnEmptyWhenLockFailed() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        // when
        CouponApplyResponse response = couponLockService.tryLockAndApplyCoupon(couponIssue, applyRequest);

        // then
        assertThat(response.isEmpty()).isTrue();

        // 아무것도 저장되지 않아야 함
        verify(saveReservationPort, never()).save(any());
        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("할인 금액이 올바르게 계산되어야 한다 - 정액 할인")
    void calculateDiscount_shouldCalculateFixedAmount() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(couponPolicy));

        // when
        CouponApplyResponse response = couponLockService.tryLockAndApplyCoupon(couponIssue, applyRequest);

        // then
        ArgumentCaptor<CouponReservation> captor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(captor.capture());

        CouponReservation reservation = captor.getValue();
        assertThat(reservation.getDiscountAmount()).isEqualTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("할인 금액이 올바르게 계산되어야 한다 - 퍼센트 할인")
    void calculateDiscount_shouldCalculatePercentage() {
        // given
        CouponPolicy percentPolicy = CouponPolicy.builder()
                .id(2L)
                .couponName("10% 할인 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .maxDiscountAmount(BigDecimal.valueOf(10000))
                .build();

        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(percentPolicy));

        // when
        couponLockService.tryLockAndApplyCoupon(couponIssue, applyRequest);

        // then
        ArgumentCaptor<CouponReservation> captor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(captor.capture());

        CouponReservation reservation = captor.getValue();
        // 50000 * 10% = 5000
        assertThat(reservation.getDiscountAmount()).isEqualTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("public 메서드여서 트랜잭션 어노테이션이 작동해야 한다")
    void shouldBePublicMethodForTransactionalProxy() {
        // CouponLockService의 tryLockAndApplyCoupon 메서드가 public인지 검증
        try {
            CouponLockService.class.getMethod("tryLockAndApplyCoupon", CouponIssue.class, CouponApplyRequest.class);
            // 메서드가 존재하고 public이면 통과
        } catch (NoSuchMethodException e) {
            throw new AssertionError("tryLockAndApplyCoupon 메서드가 public이 아닙니다");
        }

        // @Transactional 어노테이션이 있는지 검증
        try {
            var method = CouponLockService.class.getMethod("tryLockAndApplyCoupon", CouponIssue.class, CouponApplyRequest.class);
            assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("메서드를 찾을 수 없습니다");
        }
    }
}
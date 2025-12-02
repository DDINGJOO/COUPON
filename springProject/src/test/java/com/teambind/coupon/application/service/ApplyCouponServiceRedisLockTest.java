package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.out.*;
import com.teambind.coupon.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis 락 해제 버그 수정 테스트
 * Issue #39: Redis 락 키 불일치 문제 해결 검증
 */
@ExtendWith(MockitoExtension.class)
class ApplyCouponServiceRedisLockTest {

    @InjectMocks
    private ApplyCouponService applyCouponService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

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

    private CouponApplyRequest applyRequest;
    private CouponIssue couponIssue;
    private CouponPolicy couponPolicy;

    @BeforeEach
    void setUp() {
        applyRequest = CouponApplyRequest.builder()
                .userId(100L)
                .productIds(Arrays.asList(1L, 2L))
                .orderAmount(50000L)
                .build();

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
    }

    @Test
    @DisplayName("쿠폰 적용 시 락 value가 예약 정보에 저장되어야 한다")
    void applyCoupon_shouldSaveLockValueInReservation() {
        // given
        when(loadCouponIssuePort.findAvailableCouponsByUserId(100L))
                .thenReturn(Collections.singletonList(couponIssue));
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(couponPolicy));
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        // when
        CouponApplyResponse response = applyCouponService.applyCoupon(applyRequest);

        // then
        ArgumentCaptor<CouponReservation> reservationCaptor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(reservationCaptor.capture());

        CouponReservation savedReservation = reservationCaptor.getValue();
        assertThat(savedReservation.getLockValue()).isNotNull();
        assertThat(savedReservation.getCouponId()).isEqualTo(1001L);
        assertThat(response.getCouponId()).isEqualTo("1001");
    }

    @Test
    @DisplayName("락 해제 시 올바른 락 키와 value를 사용해야 한다")
    void releaseCouponLock_shouldUseCorrectLockKeyAndValue() {
        // given
        String reservationId = "RESV-2024-0001";
        String lockValue = "uuid-lock-value";

        CouponReservation reservation = CouponReservation.builder()
                .reservationId(reservationId)
                .couponId(1001L)
                .userId(100L)
                .status(ReservationStatus.PENDING)
                .lockValue(lockValue)
                .build();

        when(loadReservationPort.findById(reservationId))
                .thenReturn(Optional.of(reservation));
        when(loadCouponIssuePort.findById(1001L))
                .thenReturn(Optional.of(couponIssue));

        // when
        applyCouponService.releaseCouponLock(reservationId);

        // then
        // 올바른 락 키 사용 검증 (couponId 기반)
        String expectedLockKey = "coupon:apply:1001";
        verify(distributedLock).unlock(expectedLockKey, lockValue);

        // 쿠폰 상태 롤백 검증
        verify(saveCouponIssuePort).save(any(CouponIssue.class));

        // 예약 취소 검증
        verify(saveReservationPort).save(argThat(r ->
            r.getStatus() == ReservationStatus.CANCELLED
        ));
    }

    @Test
    @DisplayName("락 value가 없을 경우 경고 로그만 남기고 정상 처리되어야 한다")
    void releaseCouponLock_withoutLockValue_shouldLogWarning() {
        // given
        String reservationId = "RESV-2024-0002";

        CouponReservation reservation = CouponReservation.builder()
                .reservationId(reservationId)
                .couponId(1002L)
                .userId(100L)
                .status(ReservationStatus.PENDING)
                .lockValue(null) // 락 value 없음
                .build();

        when(loadReservationPort.findById(reservationId))
                .thenReturn(Optional.of(reservation));
        when(loadCouponIssuePort.findById(1002L))
                .thenReturn(Optional.of(couponIssue));

        // when
        applyCouponService.releaseCouponLock(reservationId);

        // then
        // Redis unlock이 호출되지 않아야 함
        verify(distributedLock, never()).unlock(anyString(), anyString());

        // 쿠폰과 예약 상태는 정상적으로 변경되어야 함
        verify(saveCouponIssuePort).save(any(CouponIssue.class));
        verify(saveReservationPort).save(any(CouponReservation.class));
    }

    @Test
    @DisplayName("존재하지 않는 예약 ID로 락 해제 시 예외가 발생해야 한다")
    void releaseCouponLock_withInvalidReservationId_shouldThrowException() {
        // given
        String invalidReservationId = "INVALID-RESV";
        when(loadReservationPort.findById(invalidReservationId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applyCouponService.releaseCouponLock(invalidReservationId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("쿠폰 락 해제 실패")
                .getCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("예약을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 ID로 락 해제 시 예외가 발생해야 한다")
    void releaseCouponLock_withInvalidCouponId_shouldThrowException() {
        // given
        String reservationId = "RESV-2024-0003";

        CouponReservation reservation = CouponReservation.builder()
                .reservationId(reservationId)
                .couponId(9999L) // 존재하지 않는 쿠폰
                .userId(100L)
                .status(ReservationStatus.PENDING)
                .lockValue("some-lock-value")
                .build();

        when(loadReservationPort.findById(reservationId))
                .thenReturn(Optional.of(reservation));
        when(loadCouponIssuePort.findById(9999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applyCouponService.releaseCouponLock(reservationId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("쿠폰 락 해제 실패")
                .getCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("락 키 생성 로직이 일관성을 유지해야 한다")
    void lockKey_shouldBeConsistent() {
        // given
        Long couponId = 1001L;
        String expectedLockKey = "coupon:apply:" + couponId;

        when(loadCouponIssuePort.findAvailableCouponsByUserId(100L))
                .thenReturn(Collections.singletonList(couponIssue));
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(couponPolicy));
        when(distributedLock.tryLock(eq(expectedLockKey), anyString(), any(Duration.class)))
                .thenReturn(true);

        // when
        applyCouponService.applyCoupon(applyRequest);

        // then - 적용 시 사용한 락 키 검증
        verify(distributedLock).tryLock(eq(expectedLockKey), anyString(), any(Duration.class));
    }
}
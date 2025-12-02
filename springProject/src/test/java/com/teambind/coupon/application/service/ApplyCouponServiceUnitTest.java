package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.out.*;
import com.teambind.coupon.domain.exception.*;
import com.teambind.coupon.domain.model.*;
import com.teambind.coupon.domain.model.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ApplyCouponService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApplyCouponService 단위 테스트")
class ApplyCouponServiceUnitTest {

    @InjectMocks
    private ApplyCouponService applyCouponService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private LoadReservationPort loadReservationPort;

    @Mock
    private SaveReservationPort saveReservationPort;

    @Mock
    private RedisDistributedLock distributedLock;

    @Mock
    private CouponLockService couponLockService;

    private CouponApplyRequest applyRequest;
    private CouponIssue validCoupon;
    private CouponPolicy couponPolicy;
    private CouponApplyResponse expectedResponse;
    private CouponReservation reservation;

    @BeforeEach
    void setUp() {
        applyRequest = CouponApplyRequest.builder()
                .couponId(1L)
                .userId(100L)
                .reservationId("RESV-123")
                .orderAmount(BigDecimal.valueOf(15000))
                .build();

        validCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(29))
                .couponName("테스트 쿠폰")
                .discountPolicy(DiscountPolicy.builder()
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(5000))
                        .build())
                .build();

        couponPolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        expectedResponse = CouponApplyResponse.builder()
                .couponId("1")
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .maxDiscountAmount(null)
                .build();

        reservation = CouponReservation.builder()
                .reservationId("RESV-123")
                .couponId(1L)
                .userId(100L)
                .reservationId("RESV-LOW")
                .status(ReservationStatus.PENDING)
                .lockValue("lock-value-123")
                .reservedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("쿠폰 적용 성공")
    void applyCoupon_Success() {
        // given
        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(validCoupon));
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(couponPolicy));
        when(couponLockService.tryLockAndApplyCoupon(any(CouponIssue.class), any(CouponApplyRequest.class)))
                .thenReturn(expectedResponse);

        // when
        CouponApplyResponse result = applyCouponService.applyCoupon(applyRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getCouponId()).isEqualTo("1");
        assertThat(result.getDiscountValue()).isEqualTo(BigDecimal.valueOf(5000));

        verify(loadCouponIssuePort).findById(1L);
        verify(loadCouponPolicyPort).loadById(10L);
        verify(couponLockService).tryLockAndApplyCoupon(eq(validCoupon), eq(applyRequest));
    }

    @Test
    @DisplayName("쿠폰 적용 실패 - 쿠폰을 찾을 수 없음")
    void applyCoupon_CouponNotFound() {
        // given
        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(applyRequest))
                .isInstanceOf(CouponNotFoundException.class)
                .hasMessage("쿠폰을 찾을 수 없습니다: 1");

        verify(loadCouponPolicyPort, never()).loadById(anyLong());
        verify(couponLockService, never()).tryLockAndApplyCoupon(any(), any());
    }

    @Test
    @DisplayName("쿠폰 적용 실패 - 다른 사용자의 쿠폰")
    void applyCoupon_UnauthorizedAccess() {
        // given
        CouponIssue anotherUserCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(200L) // 다른 사용자
                .status(CouponStatus.ISSUED)
                .build();

        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(anotherUserCoupon));

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(applyRequest))
                .isInstanceOf(UnauthorizedCouponAccessException.class)
                .hasMessage("해당 쿠폰에 대한 권한이 없습니다");

        verify(loadCouponPolicyPort, never()).loadById(anyLong());
        verify(couponLockService, never()).tryLockAndApplyCoupon(any(), any());
    }

    @Test
    @DisplayName("쿠폰 적용 실패 - 이미 사용된 쿠폰")
    void applyCoupon_AlreadyUsed() {
        // given
        CouponIssue usedCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.USED) // 이미 사용됨
                .build();

        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(usedCoupon));

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(applyRequest))
                .isInstanceOf(CouponAlreadyUsedException.class)
                .hasMessage("사용 가능한 상태가 아닙니다: USED");

        verify(loadCouponPolicyPort, never()).loadById(anyLong());
        verify(couponLockService, never()).tryLockAndApplyCoupon(any(), any());
    }

    @Test
    @DisplayName("쿠폰 적용 실패 - 정책을 찾을 수 없음")
    void applyCoupon_PolicyNotFound() {
        // given
        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(validCoupon));
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(applyRequest))
                .isInstanceOf(PolicyNotFoundException.class)
                .hasMessage("정책을 찾을 수 없습니다");

        verify(couponLockService, never()).tryLockAndApplyCoupon(any(), any());
    }

    @Test
    @DisplayName("쿠폰 적용 실패 - 최소 주문 금액 미충족")
    void applyCoupon_MinimumOrderNotMet() {
        // given
        CouponApplyRequest lowAmountRequest = CouponApplyRequest.builder()
                .couponId(1L)
                .userId(100L)
                .reservationId("RESV-LOW")
                .orderAmount(BigDecimal.valueOf(5000)) // 최소 금액(10000) 미만
                .build();

        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(validCoupon));
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(couponPolicy));

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(lowAmountRequest))
                .isInstanceOf(MinimumOrderNotMetException.class)
                .hasMessage("최소 주문 금액을 충족하지 않습니다");

        verify(couponLockService, never()).tryLockAndApplyCoupon(any(), any());
    }

    @Test
    @DisplayName("쿠폰 적용 - 최소 주문 금액 제한 없음")
    void applyCoupon_NoMinimumOrder() {
        // given
        CouponPolicy noMinimumPolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("제한 없는 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(1000))
                .minimumOrderAmount(null) // 최소 금액 제한 없음
                .isActive(true)
                .build();

        CouponApplyRequest lowAmountRequest = CouponApplyRequest.builder()
                .couponId(1L)
                .userId(100L)
                .reservationId("RESV-LOW")
                .orderAmount(BigDecimal.valueOf(1000))
                .build();

        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(validCoupon));
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(noMinimumPolicy));
        when(couponLockService.tryLockAndApplyCoupon(any(CouponIssue.class), any(CouponApplyRequest.class)))
                .thenReturn(expectedResponse);

        // when
        CouponApplyResponse result = applyCouponService.applyCoupon(lowAmountRequest);

        // then
        assertThat(result).isNotNull();
        verify(couponLockService).tryLockAndApplyCoupon(eq(validCoupon), eq(lowAmountRequest));
    }

    @Test
    @DisplayName("쿠폰 락 해제 성공")
    void releaseCouponLock_Success() {
        // given
        when(loadReservationPort.findById("RESV-123")).thenReturn(Optional.of(reservation));
        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(validCoupon));
        when(distributedLock.unlock("coupon:apply:1", "lock-value-123")).thenReturn(true);

        // when
        applyCouponService.releaseCouponLock("RESV-123");

        // then
        verify(loadReservationPort).findById("RESV-123");
        verify(loadCouponIssuePort).findById(1L);
        verify(saveCouponIssuePort).save(any(CouponIssue.class));
        verify(saveReservationPort).save(any(CouponReservation.class));
        verify(distributedLock).unlock("coupon:apply:1", "lock-value-123");
    }

    @Test
    @DisplayName("쿠폰 락 해제 - 예약을 찾을 수 없음")
    void releaseCouponLock_ReservationNotFound() {
        // given
        when(loadReservationPort.findById("RESV-999")).thenReturn(Optional.empty());

        // when
        applyCouponService.releaseCouponLock("RESV-999");

        // then
        verify(loadReservationPort).findById("RESV-999");
        verify(loadCouponIssuePort, never()).findById(anyLong());
        verify(saveCouponIssuePort, never()).save(any());
        verify(distributedLock, never()).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("쿠폰 락 해제 - 락 value가 없음")
    void releaseCouponLock_NoLockValue() {
        // given
        CouponReservation reservationWithoutLock = CouponReservation.builder()
                .reservationId("RESV-123")
                .couponId(1L)
                .userId(100L)
                .reservationId("RESV-LOW")
                .status(ReservationStatus.PENDING)
                .lockValue(null) // 락 value 없음
                .reservedAt(LocalDateTime.now())
                .build();

        when(loadReservationPort.findById("RESV-123")).thenReturn(Optional.of(reservationWithoutLock));
        when(loadCouponIssuePort.findById(1L)).thenReturn(Optional.of(validCoupon));

        // when
        applyCouponService.releaseCouponLock("RESV-123");

        // then
        verify(saveCouponIssuePort).save(any(CouponIssue.class));
        verify(saveReservationPort).save(any(CouponReservation.class));
        verify(distributedLock, never()).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("쿠폰 락 해제 - 예외 발생 시에도 실패 무시")
    void releaseCouponLock_ExceptionIgnored() {
        // given
        when(loadReservationPort.findById("RESV-123"))
                .thenThrow(new RuntimeException("데이터베이스 오류"));

        // when
        applyCouponService.releaseCouponLock("RESV-123");

        // then
        // 예외가 발생해도 메서드가 정상적으로 종료되어야 함
        verify(loadReservationPort).findById("RESV-123");
        verify(distributedLock, never()).unlock(anyString(), anyString());
    }
}
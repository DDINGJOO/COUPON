package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponLockService 단위 테스트
 * Redis 분산 락과 쿠폰 적용 로직 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponLockService 단위 테스트")
class CouponLockServiceUnitTest {

    @InjectMocks
    private CouponLockService couponLockService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private SaveReservationPort saveReservationPort;

    @Mock
    private RedisDistributedLock distributedLock;

    @Mock
    private com.teambind.coupon.application.port.out.LoadReservationPort loadReservationPort;

    private CouponIssue testCoupon;
    private CouponPolicy testPolicy;
    private CouponApplyRequest testRequest;

    @BeforeEach
    void setUp() {
        testCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        testPolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("단위 테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .build();

        testRequest = CouponApplyRequest.builder()
                .reservationId("RESV-UNIT-001")
                .userId(100L)
                .couponId(1L)
                .orderAmount(BigDecimal.valueOf(20000))
                .build();
    }

    @Test
    @DisplayName("쿠폰 락 획득 성공 시 쿠폰 적용")
    void tryLockAndApplyCoupon_Success() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(testPolicy));

        // when
        CouponApplyResponse response = couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCouponId()).isEqualTo("1");
        assertThat(response.getCouponName()).isEqualTo("단위 테스트 쿠폰");
        assertThat(response.getDiscountType()).isEqualTo(DiscountType.AMOUNT);
        assertThat(response.getDiscountValue()).isEqualByComparingTo("5000");

        // 예약 저장 검증
        ArgumentCaptor<CouponReservation> reservationCaptor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(reservationCaptor.capture());
        CouponReservation savedReservation = reservationCaptor.getValue();
        assertThat(savedReservation.getCouponId()).isEqualTo(1L);
        assertThat(savedReservation.getUserId()).isEqualTo(100L);
        assertThat(savedReservation.getOrderAmount()).isEqualByComparingTo("20000");
        assertThat(savedReservation.getDiscountAmount()).isEqualByComparingTo("5000");
        assertThat(savedReservation.getLockValue()).isNotNull();

        // 쿠폰 상태 변경 검증
        verify(saveCouponIssuePort).save(argThat(coupon ->
            coupon.getStatus() == CouponStatus.RESERVED
        ));
    }

    @Test
    @DisplayName("쿠폰 락 획득 실패 시 빈 응답 반환")
    void tryLockAndApplyCoupon_LockFailed() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        // when
        CouponApplyResponse response = couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest);

        // then
        assertThat(response.isEmpty()).isTrue();

        // 아무것도 저장되지 않아야 함
        verify(saveReservationPort, never()).save(any());
        verify(saveCouponIssuePort, never()).save(any());
        verify(loadCouponPolicyPort, never()).loadById(any());
    }

    @Test
    @DisplayName("정책 조회 실패 시 예외 발생 및 락 해제")
    void tryLockAndApplyCoupon_PolicyNotFound() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("쿠폰 정책을 찾을 수 없습니다");

        // 락이 해제되어야 함
        verify(distributedLock).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("정액 할인 금액 계산")
    void calculateDiscount_FixedAmount() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(testPolicy));

        // when
        couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest);

        // then
        ArgumentCaptor<CouponReservation> captor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(captor.capture());

        CouponReservation reservation = captor.getValue();
        // 5000원 정액 할인
        assertThat(reservation.getDiscountAmount()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("퍼센트 할인 금액 계산 (최대 할인 금액 제한)")
    void calculateDiscount_Percentage() {
        // given
        CouponPolicy percentagePolicy = CouponPolicy.builder()
                .id(20L)
                .couponName("20% 할인 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20)) // 20%
                .maxDiscountAmount(BigDecimal.valueOf(3000)) // 최대 3000원
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .build();

        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(percentagePolicy));

        // when
        couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest);

        // then
        ArgumentCaptor<CouponReservation> captor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(captor.capture());

        CouponReservation reservation = captor.getValue();
        // 20000 * 20% = 4000원이지만 최대 할인 3000원 제한
        assertThat(reservation.getDiscountAmount()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("예약 저장 실패 시 락 해제")
    void tryLockAndApplyCoupon_SaveFailed() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(testPolicy));
        doThrow(new RuntimeException("DB 오류"))
                .when(saveReservationPort).save(any(CouponReservation.class));

        // when & then
        assertThatThrownBy(() -> couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 오류");

        // 락이 해제되어야 함
        verify(distributedLock).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("예약 ID 생성 확인")
    void generateReservationId() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(testPolicy));

        // when
        couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest);

        // then
        ArgumentCaptor<CouponReservation> captor = ArgumentCaptor.forClass(CouponReservation.class);
        verify(saveReservationPort).save(captor.capture());

        CouponReservation reservation = captor.getValue();
        assertThat(reservation.getReservationId()).isNotNull();
        assertThat(reservation.getReservationId()).startsWith("RESV-");
    }

    @Test
    @DisplayName("락 키 형식 확인")
    void lockKeyFormat() {
        // given
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(testPolicy));

        // when
        couponLockService.tryLockAndApplyCoupon(testCoupon, testRequest);

        // then
        verify(distributedLock).tryLock(
                eq("coupon:apply:1"), // 쿠폰 ID 기반 락 키
                anyString(),
                any(Duration.class)
        );
    }
}
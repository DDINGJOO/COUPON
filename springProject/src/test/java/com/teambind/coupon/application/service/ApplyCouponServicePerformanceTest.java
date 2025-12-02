package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.LoadReservationPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveReservationPort;
import com.teambind.coupon.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 적용 성능 테스트
 * 단일 쿠폰 적용 방식의 성능 검증
 */
@ExtendWith(MockitoExtension.class)
class ApplyCouponServicePerformanceTest {

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

    @Mock
    private CouponLockService couponLockService;

    @BeforeEach
    void setUp() {
        // 기본 설정
    }

    @Test
    @DisplayName("단일 쿠폰 적용 시 성능 테스트")
    void applyCoupon_performanceTest() {
        // given
        Long userId = 100L;
        Long couponId = 1L;
        Long policyId = 1L;

        CouponIssue coupon = CouponIssue.builder()
                .id(couponId)
                .policyId(policyId)
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        CouponPolicy policy = CouponPolicy.builder()
                .id(policyId)
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .build();

        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-123")
                .userId(userId)
                .couponId(couponId)
                .orderAmount(BigDecimal.valueOf(50000))
                .build();

        when(loadCouponIssuePort.findById(couponId))
                .thenReturn(Optional.of(coupon));
        when(loadCouponPolicyPort.loadById(policyId))
                .thenReturn(Optional.of(policy));

        // CouponLockService mock - 쿠폰 적용 시도 시 응답 반환
        when(couponLockService.tryLockAndApplyCoupon(any(), any()))
                .thenReturn(CouponApplyResponse.builder()
                        .couponId(String.valueOf(couponId))
                        .couponName("테스트 쿠폰")
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(5000))
                        .build());

        // when
        long startTime = System.currentTimeMillis();
        CouponApplyResponse response = applyCouponService.applyCoupon(request);
        long endTime = System.currentTimeMillis();

        // then
        long executionTime = endTime - startTime;
        System.out.println("쿠폰 적용 실행 시간: " + executionTime + "ms");

        // 개별 조회만 사용
        verify(loadCouponIssuePort, times(1)).findById(couponId);
        verify(loadCouponPolicyPort, times(1)).loadById(policyId);

        // 성능 기준: 50ms 이내 완료
        assertThat(executionTime).isLessThan(50);
        assertThat(response.getCouponId()).isEqualTo(String.valueOf(couponId));
    }

    @Test
    @DisplayName("쿠폰 락 해제 성능 테스트")
    void releaseCouponLock_performanceTest() {
        // given
        String reservationId = "RESV-2024-0001";
        String lockValue = "uuid-lock-value";
        Long couponId = 1001L;

        CouponReservation reservation = CouponReservation.builder()
                .reservationId(reservationId)
                .couponId(couponId)
                .userId(100L)
                .status(ReservationStatus.PENDING)
                .lockValue(lockValue)
                .build();

        CouponIssue coupon = CouponIssue.builder()
                .id(couponId)
                .policyId(1L)
                .userId(100L)
                .status(CouponStatus.RESERVED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        when(loadReservationPort.findById(reservationId))
                .thenReturn(Optional.of(reservation));
        when(loadCouponIssuePort.findById(couponId))
                .thenReturn(Optional.of(coupon));

        // when
        long startTime = System.currentTimeMillis();
        applyCouponService.releaseCouponLock(reservationId);
        long endTime = System.currentTimeMillis();

        // then
        long executionTime = endTime - startTime;
        System.out.println("쿠폰 락 해제 실행 시간: " + executionTime + "ms");

        // Redis unlock 호출 검증
        verify(distributedLock).unlock("coupon:apply:" + couponId, lockValue);

        // 쿠폰과 예약 상태 변경 검증
        verify(saveCouponIssuePort).save(any(CouponIssue.class));
        verify(saveReservationPort).save(any(CouponReservation.class));

        // 성능 기준: 30ms 이내 완료
        assertThat(executionTime).isLessThan(30);
    }

    @Test
    @DisplayName("동시 쿠폰 적용 요청 시뮬레이션")
    void concurrent_couponApply_simulation() {
        // given
        Long userId = 100L;
        Long couponId = 1L;
        Long policyId = 1L;
        int requestCount = 10;

        CouponIssue coupon = CouponIssue.builder()
                .id(couponId)
                .policyId(policyId)
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        CouponPolicy policy = CouponPolicy.builder()
                .id(policyId)
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .build();

        when(loadCouponIssuePort.findById(couponId))
                .thenReturn(Optional.of(coupon));
        when(loadCouponPolicyPort.loadById(policyId))
                .thenReturn(Optional.of(policy));

        // 첫 번째 요청만 성공, 나머지는 빈 응답
        when(couponLockService.tryLockAndApplyCoupon(any(), any()))
                .thenReturn(CouponApplyResponse.builder()
                        .couponId(String.valueOf(couponId))
                        .couponName("테스트 쿠폰")
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(5000))
                        .build())
                .thenReturn(CouponApplyResponse.empty());

        // when
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < requestCount; i++) {
            CouponApplyRequest request = CouponApplyRequest.builder()
                    .reservationId("RESV-" + i)
                    .userId(userId)
                    .couponId(couponId)
                    .orderAmount(BigDecimal.valueOf(50000))
                    .build();

            applyCouponService.applyCoupon(request);
        }

        long endTime = System.currentTimeMillis();

        // then
        long totalTime = endTime - startTime;
        System.out.println("동시 요청 " + requestCount + "건 처리 시간: " + totalTime + "ms");
        System.out.println("평균 처리 시간: " + (totalTime / requestCount) + "ms");

        // 쿠폰 락 서비스가 정확히 requestCount 만큼 호출되었는지 검증
        verify(couponLockService, times(requestCount)).tryLockAndApplyCoupon(any(), any());

        // 성능 기준: 전체 처리 시간이 200ms 이내
        assertThat(totalTime).isLessThan(200);
    }
}
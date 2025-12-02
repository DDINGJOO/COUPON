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
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * N+1 쿼리 문제 해결 성능 테스트
 * Issue #42: 배치 조회를 통한 성능 개선 검증
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
    @DisplayName("배치 조회를 사용하여 N+1 문제가 해결되어야 한다")
    void applyCoupon_shouldUseBatchQueryToAvoidNPlusOneProblem() {
        // given
        Long userId = 100L;
        int couponCount = 10; // 사용자가 10개의 쿠폰 보유

        // 10개의 쿠폰 생성 (각각 다른 정책)
        List<CouponIssue> coupons = LongStream.rangeClosed(1, couponCount)
                .mapToObj(id -> CouponIssue.builder()
                        .id(id)
                        .policyId(id) // 각각 다른 정책 ID
                        .userId(userId)
                        .status(CouponStatus.ISSUED)
                        .issuedAt(LocalDateTime.now().minusDays(1))
                        .expiredAt(LocalDateTime.now().plusDays(30))
                        .build())
                .collect(Collectors.toList());

        // 정책 Map 생성
        Map<Long, CouponPolicy> policyMap = LongStream.rangeClosed(1, couponCount)
                .boxed()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> CouponPolicy.builder()
                                .id(id)
                                .couponName("쿠폰 " + id)
                                .discountType(DiscountType.AMOUNT)
                                .discountValue(BigDecimal.valueOf(1000 * id))
                                .minimumOrderAmount(BigDecimal.valueOf(10000))
                                .build()
                ));

        CouponApplyRequest request = CouponApplyRequest.builder()
                .userId(userId)
                .productIds(Arrays.asList(1L, 2L))
                .orderAmount(50000L)
                .build();

        when(loadCouponIssuePort.findAvailableCouponsByUserId(userId))
                .thenReturn(coupons);

        // 배치 조회 Mock - 한 번만 호출되어야 함
        when(loadCouponPolicyPort.loadByIds(anyList()))
                .thenReturn(policyMap);

        // CouponLockService mock - 쿠폰 적용 시도 시 응답 반환
        when(couponLockService.tryLockAndApplyCoupon(any(), any()))
                .thenReturn(CouponApplyResponse.builder()
                        .couponId("1")
                        .couponName("쿠폰 1")
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(1000))
                        .build());

        // when
        applyCouponService.applyCoupon(request);

        // then
        // loadByIds는 단 1번만 호출되어야 함 (N+1 문제 해결)
        verify(loadCouponPolicyPort, times(1)).loadByIds(anyList());

        // 개별 조회는 호출되지 않아야 함
        verify(loadCouponPolicyPort, never()).loadById(any());

        // 배치 조회에 전달된 ID 목록 검증
        verify(loadCouponPolicyPort).loadByIds(argThat(ids -> {
            assertThat(ids).hasSize(couponCount);
            assertThat(ids).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
            return true;
        }));
    }

    @Test
    @DisplayName("중복된 정책 ID는 한 번만 조회되어야 한다")
    void applyCoupon_shouldQueryEachPolicyOnlyOnce() {
        // given
        Long userId = 100L;
        Long sharedPolicyId = 1L;

        // 5개의 쿠폰이 모두 같은 정책 사용
        List<CouponIssue> coupons = LongStream.rangeClosed(1, 5)
                .mapToObj(id -> CouponIssue.builder()
                        .id(id)
                        .policyId(sharedPolicyId) // 모두 같은 정책
                        .userId(userId)
                        .status(CouponStatus.ISSUED)
                        .issuedAt(LocalDateTime.now().minusDays(1))
                        .expiredAt(LocalDateTime.now().plusDays(30))
                        .build())
                .collect(Collectors.toList());

        Map<Long, CouponPolicy> policyMap = Map.of(
                sharedPolicyId, CouponPolicy.builder()
                        .id(sharedPolicyId)
                        .couponName("공통 쿠폰")
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(BigDecimal.valueOf(10))
                        .minimumOrderAmount(BigDecimal.valueOf(10000))
                        .build()
        );

        CouponApplyRequest request = CouponApplyRequest.builder()
                .userId(userId)
                .productIds(Arrays.asList(1L))
                .orderAmount(50000L)
                .build();

        when(loadCouponIssuePort.findAvailableCouponsByUserId(userId))
                .thenReturn(coupons);
        when(loadCouponPolicyPort.loadByIds(anyList()))
                .thenReturn(policyMap);

        // CouponLockService mock
        when(couponLockService.tryLockAndApplyCoupon(any(), any()))
                .thenReturn(CouponApplyResponse.builder()
                        .couponId(String.valueOf(sharedPolicyId))
                        .couponName("공통 쿠폰")
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(BigDecimal.valueOf(10))
                        .build());

        // when
        applyCouponService.applyCoupon(request);

        // then
        // 중복 제거 후 1개의 ID만 조회
        verify(loadCouponPolicyPort).loadByIds(argThat(ids -> {
            assertThat(ids).hasSize(1);
            assertThat(ids).containsExactly(sharedPolicyId);
            return true;
        }));
    }

    @Test
    @DisplayName("정책이 없는 쿠폰은 건너뛰어야 한다")
    void applyCoupon_shouldSkipCouponWithoutPolicy() {
        // given
        Long userId = 100L;

        List<CouponIssue> coupons = Arrays.asList(
                CouponIssue.builder()
                        .id(1L)
                        .policyId(1L)
                        .userId(userId)
                        .status(CouponStatus.ISSUED)
                        .issuedAt(LocalDateTime.now().minusDays(1))
                        .expiredAt(LocalDateTime.now().plusDays(30))
                        .build(),
                CouponIssue.builder()
                        .id(2L)
                        .policyId(999L) // 존재하지 않는 정책
                        .userId(userId)
                        .status(CouponStatus.ISSUED)
                        .issuedAt(LocalDateTime.now().minusDays(1))
                        .expiredAt(LocalDateTime.now().plusDays(30))
                        .build()
        );

        // 정책 1만 존재
        Map<Long, CouponPolicy> policyMap = Map.of(
                1L, CouponPolicy.builder()
                        .id(1L)
                        .couponName("쿠폰 1")
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(5000))
                        .minimumOrderAmount(BigDecimal.valueOf(10000))
                        .build()
        );

        CouponApplyRequest request = CouponApplyRequest.builder()
                .userId(userId)
                .productIds(Arrays.asList(1L))
                .orderAmount(50000L)
                .build();

        when(loadCouponIssuePort.findAvailableCouponsByUserId(userId))
                .thenReturn(coupons);
        when(loadCouponPolicyPort.loadByIds(anyList()))
                .thenReturn(policyMap);

        // CouponLockService mock - 정책이 있는 쿠폰에 대해서만 호출됨
        when(couponLockService.tryLockAndApplyCoupon(any(), any()))
                .thenReturn(CouponApplyResponse.builder()
                        .couponId("1")
                        .couponName("쿠폰 1")
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(5000))
                        .build());

        // when
        applyCouponService.applyCoupon(request);

        // then
        // 정책이 있는 쿠폰만 처리되어야 함
        verify(couponLockService, atMostOnce()).tryLockAndApplyCoupon(any(), any());
    }

    @Test
    @DisplayName("성능 측정: 배치 조회 vs 개별 조회")
    void performanceComparison() {
        // given
        int couponCount = 100;
        Long userId = 100L;

        List<CouponIssue> coupons = LongStream.rangeClosed(1, couponCount)
                .mapToObj(id -> CouponIssue.builder()
                        .id(id)
                        .policyId(id % 20 + 1) // 20개의 정책을 순환 사용
                        .userId(userId)
                        .status(CouponStatus.ISSUED)
                        .issuedAt(LocalDateTime.now().minusDays(1))
                        .expiredAt(LocalDateTime.now().plusDays(30))
                        .build())
                .collect(Collectors.toList());

        Map<Long, CouponPolicy> policyMap = LongStream.rangeClosed(1, 20)
                .boxed()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> CouponPolicy.builder()
                                .id(id)
                                .couponName("정책 " + id)
                                .discountType(DiscountType.AMOUNT)
                                .discountValue(BigDecimal.valueOf(1000))
                                .minimumOrderAmount(BigDecimal.valueOf(10000))
                                .build()
                ));

        when(loadCouponIssuePort.findAvailableCouponsByUserId(userId))
                .thenReturn(coupons);
        when(loadCouponPolicyPort.loadByIds(anyList()))
                .thenReturn(policyMap);

        // CouponLockService mock
        when(couponLockService.tryLockAndApplyCoupon(any(), any()))
                .thenReturn(CouponApplyResponse.builder()
                        .couponId("1")
                        .couponName("정책 1")
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(1000))
                        .build());

        CouponApplyRequest request = CouponApplyRequest.builder()
                .userId(userId)
                .productIds(Arrays.asList(1L))
                .orderAmount(50000L)
                .build();

        // when
        long startTime = System.currentTimeMillis();
        applyCouponService.applyCoupon(request);
        long endTime = System.currentTimeMillis();

        // then
        long executionTime = endTime - startTime;
        System.out.println("배치 조회 실행 시간: " + executionTime + "ms");

        // 배치 조회는 1번만 실행
        verify(loadCouponPolicyPort, times(1)).loadByIds(anyList());

        // 20개의 고유한 정책 ID만 조회
        verify(loadCouponPolicyPort).loadByIds(argThat(ids -> {
            assertThat(ids).hasSize(20);
            return true;
        }));

        // 성능 기준: 100ms 이내 완료
        assertThat(executionTime).isLessThan(100);
    }
}
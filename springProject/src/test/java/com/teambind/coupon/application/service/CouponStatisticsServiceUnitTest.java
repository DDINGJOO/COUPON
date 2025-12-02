package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase.*;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponStatisticsService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponStatisticsService 단위 테스트")
class CouponStatisticsServiceUnitTest {

    @InjectMocks
    private CouponStatisticsService statisticsService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CouponPolicy testPolicy;

    @BeforeEach
    void setUp() {
        AtomicInteger currentIssueCount = new AtomicInteger(60);

        testPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .currentIssueCount(currentIssueCount)
                .maxUsagePerUser(1)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("실시간 통계 조회 - 캐시 미스")
    void getRealtimeStatistics_CacheMiss() {
        // given
        Long policyId = 1L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:realtime:1")).thenReturn(null);
        when(loadCouponPolicyPort.loadById(policyId)).thenReturn(Optional.of(testPolicy));

        // when
        RealtimeStatistics result = statisticsService.getRealtimeStatistics(policyId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPolicyId()).isEqualTo(policyId);
        assertThat(result.getPolicyName()).isEqualTo("테스트 쿠폰");
        assertThat(result.getMaxIssueCount()).isEqualTo(100);
        assertThat(result.getCurrentIssueCount()).isEqualTo(60);
        assertThat(result.getAvailableCount()).isEqualTo(40);

        verify(valueOperations).set(eq("stats:coupon:realtime:1"), any(RealtimeStatistics.class), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("실시간 통계 조회 - 캐시 히트")
    void getRealtimeStatistics_CacheHit() {
        // given
        Long policyId = 1L;
        RealtimeStatistics cachedStats = RealtimeStatistics.builder()
                .policyId(policyId)
                .policyName("캐시된 쿠폰")
                .maxIssueCount(100)
                .currentIssueCount(50)
                .usedCount(30)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:realtime:1")).thenReturn(cachedStats);

        // when
        RealtimeStatistics result = statisticsService.getRealtimeStatistics(policyId);

        // then
        assertThat(result).isEqualTo(cachedStats);
        assertThat(result.getPolicyName()).isEqualTo("캐시된 쿠폰");
        assertThat(result.getCurrentIssueCount()).isEqualTo(50);

        verify(loadCouponPolicyPort, never()).loadById(anyLong());
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("실시간 통계 조회 - 존재하지 않는 정책")
    void getRealtimeStatistics_PolicyNotFound() {
        // given
        Long policyId = 999L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:realtime:999")).thenReturn(null);
        when(loadCouponPolicyPort.loadById(policyId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> statisticsService.getRealtimeStatistics(policyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("쿠폰 정책을 찾을 수 없습니다: 999");

        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("전체 통계 조회 - 캐시 미스")
    void getGlobalStatistics_CacheMiss() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:global")).thenReturn(null);
        when(loadCouponPolicyPort.countAll()).thenReturn(10);

        // when
        GlobalStatistics result = statisticsService.getGlobalStatistics();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalPolicies()).isEqualTo(10);
        assertThat(result.getStatusDistribution()).isNotNull();
        assertThat(result.getTypeDistribution()).isNotNull();

        verify(valueOperations).set(eq("stats:coupon:global"), any(GlobalStatistics.class), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("전체 통계 조회 - 캐시 히트")
    void getGlobalStatistics_CacheHit() {
        // given
        GlobalStatistics cachedStats = GlobalStatistics.builder()
                .totalPolicies(5)
                .totalIssuedCoupons(1000L)
                .totalUsedCoupons(500L)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:global")).thenReturn(cachedStats);

        // when
        GlobalStatistics result = statisticsService.getGlobalStatistics();

        // then
        assertThat(result).isEqualTo(cachedStats);
        assertThat(result.getTotalPolicies()).isEqualTo(5);
        assertThat(result.getTotalIssuedCoupons()).isEqualTo(1000L);

        verify(loadCouponPolicyPort, never()).countAll();
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("사용자 통계 조회 - 캐시 미스")
    void getUserStatistics_CacheMiss() {
        // given
        Long userId = 100L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:user:100")).thenReturn(null);

        when(loadCouponIssuePort.countByUserIdAndStatus(userId, CouponStatus.ISSUED)).thenReturn(5);
        when(loadCouponIssuePort.countByUserIdAndStatus(userId, CouponStatus.USED)).thenReturn(3);
        when(loadCouponIssuePort.countByUserIdAndStatus(userId, CouponStatus.EXPIRED)).thenReturn(2);
        when(loadCouponIssuePort.countByUserIdAndStatus(userId, CouponStatus.RESERVED)).thenReturn(1);

        // when
        UserStatistics result = statisticsService.getUserStatistics(userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getTotalCoupons()).isEqualTo(11); // 5 + 3 + 2 + 1
        assertThat(result.getAvailableCoupons()).isEqualTo(5);
        assertThat(result.getUsedCoupons()).isEqualTo(3);
        assertThat(result.getExpiredCoupons()).isEqualTo(2);

        verify(valueOperations).set(eq("stats:coupon:user:100"), any(UserStatistics.class), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("사용자 통계 조회 - 캐시 히트")
    void getUserStatistics_CacheHit() {
        // given
        Long userId = 100L;
        UserStatistics cachedStats = UserStatistics.builder()
                .userId(userId)
                .totalCoupons(10)
                .availableCoupons(4)
                .usedCoupons(5)
                .expiredCoupons(1)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:user:100")).thenReturn(cachedStats);

        // when
        UserStatistics result = statisticsService.getUserStatistics(userId);

        // then
        assertThat(result).isEqualTo(cachedStats);
        assertThat(result.getTotalCoupons()).isEqualTo(10);
        assertThat(result.getAvailableCoupons()).isEqualTo(4);

        verify(loadCouponIssuePort, never()).countByUserIdAndStatus(anyLong(), any(CouponStatus.class));
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("사용자 통계 조회 - 쿠폰이 없는 사용자")
    void getUserStatistics_NoUserCoupons() {
        // given
        Long userId = 999L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("stats:coupon:user:999")).thenReturn(null);

        when(loadCouponIssuePort.countByUserIdAndStatus(anyLong(), any())).thenReturn(0);

        // when
        UserStatistics result = statisticsService.getUserStatistics(userId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getTotalCoupons()).isEqualTo(0);
        assertThat(result.getAvailableCoupons()).isEqualTo(0);
        assertThat(result.getUsedCoupons()).isEqualTo(0);
        assertThat(result.getExpiredCoupons()).isEqualTo(0);
    }
}
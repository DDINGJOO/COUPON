package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 통계 서비스
 * Redis 캐시를 활용한 실시간 통계 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponStatisticsService implements GetCouponStatisticsUseCase {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final LoadCouponIssuePort loadCouponIssuePort;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STATS_KEY_PREFIX = "stats:coupon:";
    private static final long CACHE_TTL_SECONDS = 60; // 1분 캐시

    @Override
    @Cacheable(value = "realtimeStats", key = "#policyId")
    public RealtimeStatistics getRealtimeStatistics(Long policyId) {
        log.info("실시간 통계 조회 - policyId: {}", policyId);
        
        String cacheKey = STATS_KEY_PREFIX + "realtime:" + policyId;
        RealtimeStatistics cached = (RealtimeStatistics) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            log.debug("캐시에서 통계 반환 - policyId: {}", policyId);
            return cached;
        }

        // 쿠폰 정책 조회
        CouponPolicy policy = loadCouponPolicyPort.loadById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 정책을 찾을 수 없습니다: " + policyId));

        // 상태별 카운트 조회
        int issuedCount = policy.getCurrentIssueCount().get();
        int usedCount = countByPolicyAndStatus(policyId, CouponStatus.USED);
        int reservedCount = countByPolicyAndStatus(policyId, CouponStatus.RESERVED);
        
        RealtimeStatistics statistics = RealtimeStatistics.builder()
                .policyId(policyId)
                .policyName(policy.getCouponName())
                .maxIssueCount(policy.getMaxIssueCount())
                .currentIssueCount(issuedCount)
                .usedCount(usedCount)
                .reservedCount(reservedCount)
                .availableCount(policy.getMaxIssueCount() - issuedCount)
                .usageRate(calculateUsageRate(usedCount, issuedCount))
                .lastIssuedAt(getLastActivityTime(policyId, "issued"))
                .lastUsedAt(getLastActivityTime(policyId, "used"))
                .build();

        // 캐시 저장
        redisTemplate.opsForValue().set(cacheKey, statistics, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        
        return statistics;
    }

    @Override
    @Cacheable(value = "globalStats")
    public GlobalStatistics getGlobalStatistics() {
        log.info("전체 통계 조회");
        
        String cacheKey = STATS_KEY_PREFIX + "global";
        GlobalStatistics cached = (GlobalStatistics) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }

        // 전체 정책 수
        int totalPolicies = loadCouponPolicyPort.countAll();
        
        // 상태별 분포
        Map<String, Long> statusDistribution = new HashMap<>();
        for (CouponStatus status : CouponStatus.values()) {
            long count = countAllByStatus(status);
            statusDistribution.put(status.name(), count);
        }
        
        long totalIssued = statusDistribution.values().stream().mapToLong(Long::longValue).sum();
        long totalUsed = statusDistribution.getOrDefault(CouponStatus.USED.name(), 0L);
        long totalReserved = statusDistribution.getOrDefault(CouponStatus.RESERVED.name(), 0L);
        long totalExpired = statusDistribution.getOrDefault(CouponStatus.EXPIRED.name(), 0L);
        
        GlobalStatistics statistics = GlobalStatistics.builder()
                .totalPolicies(totalPolicies)
                .totalIssuedCoupons(totalIssued)
                .totalUsedCoupons(totalUsed)
                .totalReservedCoupons(totalReserved)
                .totalExpiredCoupons(totalExpired)
                .overallUsageRate(calculateUsageRate(totalUsed, totalIssued))
                .statusDistribution(statusDistribution)
                .typeDistribution(getTypeDistribution())
                .build();

        // 캐시 저장 (5분)
        redisTemplate.opsForValue().set(cacheKey, statistics, 300, TimeUnit.SECONDS);
        
        return statistics;
    }

    @Override
    public UserStatistics getUserStatistics(Long userId) {
        log.info("사용자 통계 조회 - userId: {}", userId);
        
        String cacheKey = STATS_KEY_PREFIX + "user:" + userId;
        UserStatistics cached = (UserStatistics) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }

        // 사용자 쿠폰 통계
        Map<String, Integer> couponsByStatus = new HashMap<>();
        int totalCoupons = 0;
        int availableCoupons = 0;
        int usedCoupons = 0;
        int expiredCoupons = 0;
        
        for (CouponStatus status : CouponStatus.values()) {
            int count = loadCouponIssuePort.countByUserIdAndStatus(userId, status);
            couponsByStatus.put(status.name(), count);
            totalCoupons += count;
            
            switch (status) {
                case ISSUED -> availableCoupons += count;
                case USED -> usedCoupons += count;
                case EXPIRED -> expiredCoupons += count;
            }
        }
        
        UserStatistics statistics = UserStatistics.builder()
                .userId(userId)
                .totalCoupons(totalCoupons)
                .availableCoupons(availableCoupons)
                .usedCoupons(usedCoupons)
                .expiredCoupons(expiredCoupons)
                .firstIssuedAt(getFirstIssuedTime(userId))
                .lastUsedAt(getLastUsedTime(userId))
                .couponsByStatus(couponsByStatus)
                .build();

        // 캐시 저장 (2분)
        redisTemplate.opsForValue().set(cacheKey, statistics, 120, TimeUnit.SECONDS);
        
        return statistics;
    }

    /**
     * 정책별 상태별 카운트
     */
    private int countByPolicyAndStatus(Long policyId, CouponStatus status) {
        String countKey = String.format("%scount:%d:%s", STATS_KEY_PREFIX, policyId, status.name());
        Integer count = (Integer) redisTemplate.opsForValue().get(countKey);
        
        if (count == null) {
            // DB에서 조회
            count = 0; // TODO: Repository 메서드 추가 필요
            redisTemplate.opsForValue().set(countKey, count, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        
        return count;
    }

    /**
     * 전체 상태별 카운트
     */
    private long countAllByStatus(CouponStatus status) {
        // TODO: Repository 메서드 추가 필요
        return 0L;
    }

    /**
     * 타입별 분포
     */
    private Map<String, Long> getTypeDistribution() {
        Map<String, Long> distribution = new HashMap<>();
        distribution.put("CODE", 0L);
        distribution.put("DIRECT", 0L);
        // TODO: 실제 데이터 조회
        return distribution;
    }

    /**
     * 사용률 계산
     */
    private double calculateUsageRate(long used, long total) {
        return total > 0 ? (double) used / total * 100 : 0.0;
    }

    /**
     * 마지막 활동 시간 조회
     */
    private LocalDateTime getLastActivityTime(Long policyId, String activityType) {
        String key = String.format("%slast:%d:%s", STATS_KEY_PREFIX, policyId, activityType);
        LocalDateTime time = (LocalDateTime) redisTemplate.opsForValue().get(key);
        return time != null ? time : LocalDateTime.now();
    }

    /**
     * 첫 발급 시간 조회
     */
    private LocalDateTime getFirstIssuedTime(Long userId) {
        // TODO: Repository 메서드 추가 필요
        return LocalDateTime.now();
    }

    /**
     * 마지막 사용 시간 조회
     */
    private LocalDateTime getLastUsedTime(Long userId) {
        // TODO: Repository 메서드 추가 필요
        return LocalDateTime.now();
    }

    /**
     * 통계 캐시 초기화
     */
    public void invalidateCache(Long policyId) {
        String pattern = STATS_KEY_PREFIX + "*" + policyId + "*";
        redisTemplate.delete(redisTemplate.keys(pattern));
    }
}

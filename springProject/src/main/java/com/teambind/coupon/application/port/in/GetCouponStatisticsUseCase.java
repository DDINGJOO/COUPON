package com.teambind.coupon.application.port.in;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 쿠폰 통계 조회 UseCase
 * 실시간 쿠폰 통계 정보 제공
 */
public interface GetCouponStatisticsUseCase {

    /**
     * 실시간 쿠폰 통계 조회
     */
    RealtimeStatistics getRealtimeStatistics(Long policyId);

    /**
     * 전체 쿠폰 통계 조회
     */
    GlobalStatistics getGlobalStatistics();

    /**
     * 사용자별 쿠폰 통계 조회
     */
    UserStatistics getUserStatistics(Long userId);

    /**
     * 실시간 통계 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class RealtimeStatistics {
        private final Long policyId;
        private final String policyName;
        private final Integer maxIssueCount;
        private final Integer currentIssueCount;
        private final Integer usedCount;
        private final Integer reservedCount;
        private final Integer availableCount;
        private final Double usageRate;
        private final LocalDateTime lastIssuedAt;
        private final LocalDateTime lastUsedAt;
        
        public static RealtimeStatistics of(Long policyId, String name, int max, int issued, int used, int reserved) {
            return RealtimeStatistics.builder()
                    .policyId(policyId)
                    .policyName(name)
                    .maxIssueCount(max)
                    .currentIssueCount(issued)
                    .usedCount(used)
                    .reservedCount(reserved)
                    .availableCount(max - issued)
                    .usageRate(issued > 0 ? (double) used / issued * 100 : 0.0)
                    .lastIssuedAt(LocalDateTime.now())
                    .lastUsedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 전체 통계 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class GlobalStatistics {
        private final Integer totalPolicies;
        private final Long totalIssuedCoupons;
        private final Long totalUsedCoupons;
        private final Long totalReservedCoupons;
        private final Long totalExpiredCoupons;
        private final Double overallUsageRate;
        private final Map<String, Long> statusDistribution;
        private final Map<String, Long> typeDistribution;
    }

    /**
     * 사용자 통계 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class UserStatistics {
        private final Long userId;
        private final Integer totalCoupons;
        private final Integer availableCoupons;
        private final Integer usedCoupons;
        private final Integer expiredCoupons;
        private final LocalDateTime firstIssuedAt;
        private final LocalDateTime lastUsedAt;
        private final Map<String, Integer> couponsByStatus;
    }
}

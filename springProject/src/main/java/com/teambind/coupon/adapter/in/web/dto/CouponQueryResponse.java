package com.teambind.coupon.adapter.in.web.dto;

import com.teambind.coupon.adapter.out.persistence.projection.CouponIssueProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 쿠폰 조회 응답 DTO
 * 커서 기반 페이지네이션 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponQueryResponse {

    /**
     * 쿠폰 목록
     */
    private List<CouponItem> data;

    /**
     * 다음 페이지 커서
     * null이면 마지막 페이지
     */
    private Long nextCursor;

    /**
     * 다음 페이지 존재 여부
     */
    private boolean hasNext;

    /**
     * 조회된 쿠폰 개수
     */
    private int count;

    /**
     * 쿠폰 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponItem {
        // 쿠폰 발급 정보
        private Long couponIssueId;
        private Long userId;
        private Long policyId;
        private String status;
        private LocalDateTime issuedAt;
        private LocalDateTime expiresAt;
        private LocalDateTime usedAt;
        private LocalDateTime reservedAt;
        private String reservationId;
        private BigDecimal actualDiscountAmount;

        // 쿠폰 정책 정보
        private String couponName;
        private String couponCode;
        private String description;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal minimumOrderAmount;
        private BigDecimal maxDiscountAmount;
        private List<Long> applicableProductIds;
        private String distributionType;

        // 계산 필드
        private boolean isAvailable;
        private Long remainingDays;  // 만료까지 남은 일수

        /**
         * Projection에서 CouponItem으로 변환
         */
        public static CouponItem from(CouponIssueProjection projection) {
            return CouponItem.builder()
                    .couponIssueId(projection.getCouponIssueId())
                    .userId(projection.getUserId())
                    .policyId(projection.getPolicyId())
                    .status(projection.getStatus())
                    .issuedAt(projection.getIssuedAt())
                    .expiresAt(projection.getExpiresAt())
                    .usedAt(projection.getUsedAt())
                    .reservedAt(projection.getReservedAt())
                    .reservationId(projection.getReservationId())
                    .actualDiscountAmount(projection.getActualDiscountAmount())
                    .couponName(projection.getCouponName())
                    .couponCode(projection.getCouponCode())
                    .description(projection.getDescription())
                    .discountType(projection.getDiscountType())
                    .discountValue(projection.getDiscountValue())
                    .minimumOrderAmount(projection.getMinimumOrderAmount())
                    .maxDiscountAmount(projection.getMaxDiscountAmount())
                    .applicableProductIds(null) // TODO: JSONB 파싱 필요
                    .distributionType(projection.getDistributionType())
                    .isAvailable(projection.getIsAvailable() != null && projection.getIsAvailable())
                    .remainingDays(calculateRemainingDays(projection.getExpiresAt()))
                    .build();
        }

        /**
         * 만료까지 남은 일수 계산
         */
        private static Long calculateRemainingDays(LocalDateTime expiresAt) {
            if (expiresAt == null) {
                return null;
            }
            long days = java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays();
            return days >= 0 ? days : null;
        }
    }

    /**
     * 커서 기반 페이지네이션 응답 생성
     *
     * @param items 쿠폰 목록 (limit + 1개 조회)
     * @param limit 요청한 limit
     * @return 응답 DTO
     */
    public static CouponQueryResponse of(List<CouponIssueProjection> items, int limit) {
        boolean hasNext = items.size() > limit;
        List<CouponIssueProjection> data = hasNext
                ? items.subList(0, limit)
                : items;

        Long nextCursor = null;
        if (hasNext && !data.isEmpty()) {
            nextCursor = data.get(data.size() - 1).getCouponIssueId();
        }

        List<CouponItem> couponItems = data.stream()
                .map(CouponItem::from)
                .toList();

        return CouponQueryResponse.builder()
                .data(couponItems)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .count(couponItems.size())
                .build();
    }
}
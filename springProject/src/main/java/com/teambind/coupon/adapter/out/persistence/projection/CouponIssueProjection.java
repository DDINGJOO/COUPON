package com.teambind.coupon.adapter.out.persistence.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 조회 결과 Projection
 * Native Query 결과를 매핑하기 위한 인터페이스
 */
public interface CouponIssueProjection {

    // CouponIssue 필드
    Long getCouponIssueId();
    Long getUserId();
    Long getPolicyId();
    String getStatus();
    LocalDateTime getIssuedAt();
    LocalDateTime getExpiresAt();
    LocalDateTime getUsedAt();
    LocalDateTime getReservedAt();
    String getReservationId();
    BigDecimal getActualDiscountAmount();

    // CouponPolicy 필드
    String getCouponName();
    String getCouponCode();
    String getDescription();
    String getDiscountType();
    BigDecimal getDiscountValue();
    BigDecimal getMinimumOrderAmount();
    BigDecimal getMaxDiscountAmount();
    Long[] getApplicableProductIds();
    String getDistributionType();

    // 계산 필드
    Boolean getIsAvailable();
}
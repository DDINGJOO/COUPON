package com.teambind.coupon.adapter.in.web.dto;

import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.DiscountType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 발급된 쿠폰 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
public class CouponIssueResponse {

    private Long couponIssueId;
    private String couponName;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private String discountDisplay;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private Long remainingDays;

    /**
     * Domain 모델로부터 Response DTO 생성
     */
    public static CouponIssueResponse from(CouponIssue issue, LocalDateTime expiresAt) {
        String discountDisplay = issue.getDiscountPolicy() != null
            ? issue.getDiscountPolicy().toDisplayString()
            : "";

        Long remainingDays = null;
        if (expiresAt != null) {
            remainingDays = java.time.Duration.between(
                LocalDateTime.now(),
                expiresAt
            ).toDays();
        }

        return CouponIssueResponse.builder()
                .couponIssueId(issue.getId())
                .couponName(issue.getCouponName())
                .discountType(issue.getDiscountPolicy() != null
                    ? issue.getDiscountPolicy().getDiscountType()
                    : null)
                .discountValue(issue.getDiscountPolicy() != null
                    ? issue.getDiscountPolicy().getDiscountValue()
                    : null)
                .maxDiscountAmount(issue.getDiscountPolicy() != null
                    ? issue.getDiscountPolicy().getMaxDiscountAmount()
                    : null)
                .discountDisplay(discountDisplay)
                .issuedAt(issue.getIssuedAt())
                .expiresAt(expiresAt)
                .remainingDays(remainingDays)
                .build();
    }
}
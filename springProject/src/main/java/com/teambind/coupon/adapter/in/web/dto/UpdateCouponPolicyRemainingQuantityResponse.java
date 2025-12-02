package com.teambind.coupon.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 정책 남은 발급 수량 수정 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCouponPolicyRemainingQuantityResponse {

    private Long couponPolicyId;
    private Integer previousMaxIssueCount;
    private Integer newMaxIssueCount;
    private Integer currentIssuedCount;
    private Integer remainingCount; // 남은 발급 가능 수량
    private boolean success;
    private String message;
}
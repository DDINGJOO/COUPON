package com.teambind.coupon.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 쿠폰 발급 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponIssueRequest {

    @NotBlank(message = "쿠폰 코드는 필수입니다")
    private String couponCode;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    private Long policyId; // 선착순 발급 시 사용
}
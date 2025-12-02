package com.teambind.coupon.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 정책 남은 발급 수량 수정 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCouponPolicyRemainingQuantityRequest {

    @PositiveOrZero(message = "발급 수량은 0 이상이어야 합니다.")
    private Integer newMaxIssueCount; // null은 무제한을 의미

    @NotNull(message = "수정자 정보는 필수입니다.")
    private Long modifiedBy;

    private String reason; // 수정 사유 (선택적)
}
package com.teambind.coupon.application.port.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Value;



/**
 * 쿠폰 정책 남은 발급 수량 수정 커맨드
 */
@Value
@Builder
public class UpdateCouponPolicyRemainingQuantityCommand {

    @NotNull(message = "쿠폰 정책 ID는 필수입니다.")
    Long couponPolicyId;

    @PositiveOrZero(message = "발급 수량은 0 이상이어야 합니다.")
    Integer newMaxIssueCount; // null은 무제한을 의미

    @NotNull(message = "수정자 정보는 필수입니다.")
    Long modifiedBy;

    String reason; // 수정 사유 (선택적)
}

package com.teambind.coupon.application.port.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;


import java.util.List;

/**
 * 직접 쿠폰 발급 커맨드
 * 관리자가 특정 사용자에게 직접 쿠폰 발급
 */
@Getter
@Builder
public class DirectIssueCouponCommand {

    @NotNull(message = "쿠폰 정책 ID는 필수입니다")
    private final Long couponPolicyId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private final List<Long> userIds;

    @NotNull(message = "발급자 ID는 필수입니다")
    private final String issuedBy;

    private final String reason;  // 발급 사유

    @Positive(message = "발급 수량은 양수여야 합니다")
    @Builder.Default
    private final int quantityPerUser = 1;  // 사용자당 발급 수량

    private final boolean skipValidation;  // 검증 스킵 여부

    /**
     * 단일 사용자 발급용 팩토리 메서드
     */
    public static DirectIssueCouponCommand forSingleUser(
            Long couponPolicyId,
            Long userId,
            String issuedBy,
            String reason) {
        return DirectIssueCouponCommand.builder()
                .couponPolicyId(couponPolicyId)
                .userIds(List.of(userId))
                .issuedBy(issuedBy)
                .reason(reason)
                .quantityPerUser(1)
                .build();
    }

    /**
     * 다중 사용자 발급용 팩토리 메서드
     */
    public static DirectIssueCouponCommand forMultipleUsers(
            Long couponPolicyId,
            List<Long> userIds,
            String issuedBy,
            String reason,
            int quantityPerUser) {
        return DirectIssueCouponCommand.builder()
                .couponPolicyId(couponPolicyId)
                .userIds(userIds)
                .issuedBy(issuedBy)
                .reason(reason)
                .quantityPerUser(quantityPerUser)
                .build();
    }

    /**
     * 총 발급 예정 수량
     */
    public int getTotalQuantity() {
        return userIds.size() * quantityPerUser;
    }
}

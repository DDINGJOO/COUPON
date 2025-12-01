package com.teambind.coupon.application.port.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;

/**
 * 쿠폰 직접 발급 커맨드
 * 관리자가 특정 사용자들에게 직접 쿠폰을 발급
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class DirectIssueCouponCommand {

    @NotNull(message = "정책 ID는 필수입니다")
    @Positive(message = "정책 ID는 양수여야 합니다")
    private Long policyId;

    @NotNull(message = "사용자 ID 목록은 필수입니다")
    private List<Long> userIds;

    private String reason; // 발급 사유
    private Long issuedBy; // 발급 처리자 ID

    /**
     * 정적 팩토리 메서드
     */
    public static DirectIssueCouponCommand of(Long policyId, List<Long> userIds) {
        return DirectIssueCouponCommand.builder()
                .policyId(policyId)
                .userIds(userIds)
                .build();
    }

    public static DirectIssueCouponCommand of(Long policyId, List<Long> userIds, String reason, Long issuedBy) {
        return DirectIssueCouponCommand.builder()
                .policyId(policyId)
                .userIds(userIds)
                .reason(reason)
                .issuedBy(issuedBy)
                .build();
    }
}
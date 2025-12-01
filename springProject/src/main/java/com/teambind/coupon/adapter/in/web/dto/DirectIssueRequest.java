package com.teambind.coupon.adapter.in.web.dto;

import com.teambind.coupon.application.port.in.DirectIssueCouponCommand;
import lombok.*;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 직접 쿠폰 발급 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DirectIssueRequest {

    @NotNull(message = "쿠폰 정책 ID는 필수입니다")
    private Long couponPolicyId;

    @NotEmpty(message = "사용자 ID 목록은 필수입니다")
    @Size(max = 1000, message = "한 번에 최대 1000명까지 발급 가능합니다")
    private List<Long> userIds;

    @NotNull(message = "발급자 정보는 필수입니다")
    private String issuedBy;

    @Size(max = 500, message = "발급 사유는 최대 500자까지 입력 가능합니다")
    private String reason;

    @Positive(message = "발급 수량은 양수여야 합니다")
    @Builder.Default
    private int quantityPerUser = 1;

    @Builder.Default
    private boolean skipValidation = false;

    /**
     * Command 객체로 변환
     */
    public DirectIssueCouponCommand toCommand() {
        return DirectIssueCouponCommand.builder()
                .couponPolicyId(couponPolicyId)
                .userIds(userIds)
                .issuedBy(issuedBy)
                .reason(reason)
                .quantityPerUser(quantityPerUser)
                .skipValidation(skipValidation)
                .build();
    }

    /**
     * 단일 사용자 발급 요청 생성
     */
    public static DirectIssueRequest forSingleUser(
            Long couponPolicyId,
            Long userId,
            String issuedBy,
            String reason) {
        return DirectIssueRequest.builder()
                .couponPolicyId(couponPolicyId)
                .userIds(List.of(userId))
                .issuedBy(issuedBy)
                .reason(reason)
                .quantityPerUser(1)
                .skipValidation(false)
                .build();
    }
}
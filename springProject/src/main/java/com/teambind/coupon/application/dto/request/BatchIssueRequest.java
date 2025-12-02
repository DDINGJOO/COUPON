package com.teambind.coupon.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder.Default;
import java.util.List;

/**
 * 배치 발급 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchIssueRequest {

    @NotNull(message = "정책 ID는 필수입니다")
    private Long policyId;

    @NotNull(message = "사용자 ID 목록은 필수입니다")
    @Size(min = 1, max = 1000, message = "사용자는 1명 이상 1000명 이하여야 합니다")
    private List<Long> userIds;

    @Default
    private Integer batchSize = 100;

    @Default
    private Boolean useDistributedLock = true;
}
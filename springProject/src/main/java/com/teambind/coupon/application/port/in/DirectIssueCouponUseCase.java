package com.teambind.coupon.application.port.in;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 쿠폰 직접 발급 유스케이스
 */
public interface DirectIssueCouponUseCase {

    DirectIssueResult issueCouponDirectly(DirectIssueCouponCommand command);

    /**
     * 직접 발급 결과
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class DirectIssueResult {
        private final Long policyId;
        private final int totalRequested;
        private final int successCount;
        private final int failureCount;
        private final List<Long> failedUserIds;
        private final List<String> errors;

        public static DirectIssueResult success(Long policyId, int count) {
            return DirectIssueResult.builder()
                    .policyId(policyId)
                    .totalRequested(count)
                    .successCount(count)
                    .failureCount(0)
                    .failedUserIds(List.of())
                    .errors(List.of())
                    .build();
        }

        public static DirectIssueResult partial(Long policyId, int total, int success,
                                               List<Long> failedUserIds, List<String> errors) {
            return DirectIssueResult.builder()
                    .policyId(policyId)
                    .totalRequested(total)
                    .successCount(success)
                    .failureCount(total - success)
                    .failedUserIds(failedUserIds)
                    .errors(errors)
                    .build();
        }

        public boolean isCompleteSuccess() {
            return totalRequested == successCount;
        }

        public boolean hasFailures() {
            return failureCount > 0;
        }
    }
}
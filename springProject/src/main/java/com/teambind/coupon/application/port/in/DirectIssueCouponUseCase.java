package com.teambind.coupon.application.port.in;

import com.teambind.coupon.domain.model.CouponIssue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 직접 쿠폰 발급 UseCase
 * DIRECT 타입 쿠폰을 관리자가 직접 발급
 */
public interface DirectIssueCouponUseCase {

    /**
     * 직접 쿠폰 발급
     *
     * @param command 발급 커맨드
     * @return 발급 결과
     */
    DirectIssueResult directIssue(DirectIssueCouponCommand command);

    /**
     * 직접 쿠폰 발급 (별칭)
     */
    default DirectIssueResult issueCoupon(DirectIssueCouponCommand command) {
        return directIssue(command);
    }

    /**
     * 직접 쿠폰 발급 (별칭)
     */
    default DirectIssueResult issueCouponDirectly(DirectIssueCouponCommand command) {
        return directIssue(command);
    }

    /**
     * 일괄 발급
     */
    default List<DirectIssueResult> batchIssue(List<Long> userIds, Long policyId, String issuedBy) {
        return userIds.stream()
                .map(userId -> directIssue(DirectIssueCouponCommand.of(userId, policyId, issuedBy)))
                .toList();
    }

    /**
     * 발급 결과 DTO (Builder 패턴 지원)
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class DirectIssueResult {
        private final Long policyId;
        private final Long userId;
        private final Long couponId;
        private final int requestedCount;      // 요청 수량
        private final int successCount;        // 성공 수량
        private final int failedCount;         // 실패 수량
        private final boolean success;
        private final String message;
        private final List<CouponIssue> issuedCoupons;  // 발급된 쿠폰 목록
        private final List<IssueFailure> failures;       // 실패 목록
        private final List<Long> failedUserIds;
        private final List<String> errors;

        // 호환성을 위한 메서드들
        public int getTotalRequested() {
            return requestedCount;
        }

        public int getFailureCount() {
            return failedCount;
        }

        public boolean isFullySuccessful() {
            return failedCount == 0 && success;
        }

        public boolean isPartiallySuccessful() {
            return successCount > 0 && failedCount > 0;
        }

        public boolean isCompleteSuccess() {
            return requestedCount == successCount;
        }

        public boolean hasFailures() {
            return failedCount > 0;
        }

        // 정적 팩토리 메서드
        public static DirectIssueResult success(Long policyId, Long userId, Long couponId, String message) {
            return DirectIssueResult.builder()
                    .policyId(policyId)
                    .userId(userId)
                    .couponId(couponId)
                    .requestedCount(1)
                    .successCount(1)
                    .failedCount(0)
                    .success(true)
                    .message(message)
                    .failedUserIds(List.of())
                    .errors(List.of())
                    .build();
        }

        public static DirectIssueResult success(Long policyId, int count) {
            return DirectIssueResult.builder()
                    .policyId(policyId)
                    .requestedCount(count)
                    .successCount(count)
                    .failedCount(0)
                    .success(true)
                    .message("발급 완료")
                    .failedUserIds(List.of())
                    .errors(List.of())
                    .build();
        }

        public static DirectIssueResult failure(Long policyId, Long userId, String message) {
            return DirectIssueResult.builder()
                    .policyId(policyId)
                    .userId(userId)
                    .requestedCount(1)
                    .successCount(0)
                    .failedCount(1)
                    .success(false)
                    .message(message)
                    .failedUserIds(List.of(userId))
                    .errors(List.of(message))
                    .build();
        }

        public static DirectIssueResult partial(Long policyId, int total, int success,
                                               List<Long> failedUserIds, List<String> errors) {
            return DirectIssueResult.builder()
                    .policyId(policyId)
                    .requestedCount(total)
                    .successCount(success)
                    .failedCount(total - success)
                    .success(success > 0)
                    .message(String.format("부분 성공: %d/%d", success, total))
                    .failedUserIds(failedUserIds)
                    .errors(errors)
                    .build();
        }
    }

    /**
     * 발급 실패 정보
     */
    record IssueFailure(
            Long userId,
            String reason,
            String errorCode
    ) {}
}
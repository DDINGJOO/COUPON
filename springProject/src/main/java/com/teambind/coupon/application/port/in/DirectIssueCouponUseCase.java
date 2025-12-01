package com.teambind.coupon.application.port.in;

import com.teambind.coupon.domain.model.CouponIssue;

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
     * 발급 결과 DTO
     */
    record DirectIssueResult(
            int requestedCount,      // 요청 수량
            int successCount,        // 성공 수량
            int failedCount,         // 실패 수량
            List<CouponIssue> issuedCoupons,  // 발급된 쿠폰 목록
            List<IssueFailure> failures       // 실패 목록
    ) {
        public boolean isFullySuccessful() {
            return failedCount == 0;
        }

        public boolean isPartiallySuccessful() {
            return successCount > 0 && failedCount > 0;
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
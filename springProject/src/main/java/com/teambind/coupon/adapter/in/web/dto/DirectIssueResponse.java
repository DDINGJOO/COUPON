package com.teambind.coupon.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase.DirectIssueResult;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase.IssueFailure;
import com.teambind.coupon.domain.model.CouponIssue;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 직접 쿠폰 발급 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DirectIssueResponse {

    private boolean success;
    private int requestedCount;
    private int successCount;
    private int failedCount;
    private List<IssuedCouponInfo> issuedCoupons;
    private List<FailureInfo> failures;
    private String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime issuedAt;

    /**
     * DirectIssueResult로부터 응답 생성
     */
    public static DirectIssueResponse from(DirectIssueResult result) {
        String message;
        if (result.isFullySuccessful()) {
            message = String.format("모든 쿠폰이 성공적으로 발급되었습니다. (총 %d개)", result.successCount());
        } else if (result.isPartiallySuccessful()) {
            message = String.format("쿠폰 발급이 부분적으로 성공했습니다. (성공: %d개, 실패: %d개)",
                    result.successCount(), result.failedCount());
        } else {
            message = String.format("쿠폰 발급에 실패했습니다. (실패: %d개)", result.failedCount());
        }

        return DirectIssueResponse.builder()
                .success(result.failedCount() == 0)
                .requestedCount(result.requestedCount())
                .successCount(result.successCount())
                .failedCount(result.failedCount())
                .issuedCoupons(mapToIssuedCouponInfo(result.issuedCoupons()))
                .failures(mapToFailureInfo(result.failures()))
                .message(message)
                .issuedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 발급된 쿠폰 정보 매핑
     */
    private static List<IssuedCouponInfo> mapToIssuedCouponInfo(List<CouponIssue> coupons) {
        return coupons.stream()
                .map(IssuedCouponInfo::from)
                .collect(Collectors.toList());
    }

    /**
     * 실패 정보 매핑
     */
    private static List<FailureInfo> mapToFailureInfo(List<IssueFailure> failures) {
        return failures.stream()
                .map(FailureInfo::from)
                .collect(Collectors.toList());
    }

    /**
     * 발급된 쿠폰 정보
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Builder
    public static class IssuedCouponInfo {
        private Long couponId;
        private Long userId;
        private String couponName;
        private String status;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime issuedAt;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime expiresAt;

        public static IssuedCouponInfo from(CouponIssue couponIssue) {
            return IssuedCouponInfo.builder()
                    .couponId(couponIssue.getId())
                    .userId(couponIssue.getUserId())
                    .couponName(couponIssue.getCouponName())
                    .status(couponIssue.getStatus().name())
                    .issuedAt(couponIssue.getIssuedAt())
                    .expiresAt(couponIssue.getExpiredAt())
                    .build();
        }
    }

    /**
     * 실패 정보
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Builder
    public static class FailureInfo {
        private Long userId;
        private String reason;
        private String errorCode;

        public static FailureInfo from(IssueFailure failure) {
            return FailureInfo.builder()
                    .userId(failure.userId())
                    .reason(failure.reason())
                    .errorCode(failure.errorCode())
                    .build();
        }
    }
}
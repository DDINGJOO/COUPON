package com.teambind.coupon.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쿠폰 발급 완료 이벤트
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponIssuedEvent {
    private Long couponIssueId;
    private Long policyId;
    private Long userId;
    private String couponName;
    private String couponCode;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
}
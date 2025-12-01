package com.teambind.coupon.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 사용 완료 이벤트
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponUsedEvent {
    private Long couponIssueId;
    private String reservationId;
    private String orderId;
    private Long userId;
    private BigDecimal actualDiscountAmount;
    private LocalDateTime usedAt;
}
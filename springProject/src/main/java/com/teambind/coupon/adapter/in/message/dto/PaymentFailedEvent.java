package com.teambind.coupon.adapter.in.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 결제 실패 이벤트 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent {

    private String paymentId;
    private String orderId;
    private Long userId;
    private String reservationId; // 쿠폰 예약 ID
    private LocalDateTime failedAt;
    private String failureReason;
    private String errorCode;

    /**
     * 쿠폰 예약 해제 필요 여부
     */
    public boolean needsCouponRelease() {
        return reservationId != null && !reservationId.isEmpty();
    }
}
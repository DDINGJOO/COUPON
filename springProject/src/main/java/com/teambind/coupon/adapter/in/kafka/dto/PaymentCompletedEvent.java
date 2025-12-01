package com.teambind.coupon.adapter.in.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 완료 이벤트 DTO
 * 결제 서비스로부터 수신하는 이벤트
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString
public class PaymentCompletedEvent {

    private String paymentId;
    private String reservationId;  // 예약 ID (쿠폰 매칭용)
    private String orderId;
    private String paymentKey;
    private BigDecimal amount;
    private String method;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paidAt;

    /**
     * 이벤트 유효성 검증
     */
    public boolean isValid() {
        return paymentId != null && !paymentId.isEmpty()
                && reservationId != null && !reservationId.isEmpty()
                && orderId != null && !orderId.isEmpty()
                && amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                && paidAt != null;
    }
}
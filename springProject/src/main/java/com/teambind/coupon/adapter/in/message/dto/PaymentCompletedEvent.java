package com.teambind.coupon.adapter.in.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 완료 이벤트 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentCompletedEvent {

    private String paymentId;
    private String orderId;
    private Long userId;
    private String reservationId; // 쿠폰 예약 ID
    private BigDecimal paymentAmount;
    private BigDecimal discountAmount;
    private LocalDateTime paymentAt;
    private String paymentMethod;

    /**
     * 쿠폰 사용 여부 확인
     */
    public boolean hasCouponReservation() {
        return reservationId != null && !reservationId.isEmpty();
    }
}
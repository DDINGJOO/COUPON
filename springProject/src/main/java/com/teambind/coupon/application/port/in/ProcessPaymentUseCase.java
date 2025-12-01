package com.teambind.coupon.application.port.in;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 결제 이벤트 처리 유스케이스
 */
public interface ProcessPaymentUseCase {

    PaymentResult processPaymentCompleted(PaymentCompletedCommand command);
    PaymentResult processPaymentFailed(PaymentFailedCommand command);

    /**
     * 결제 완료 명령
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class PaymentCompletedCommand {
        private final String orderId;
        private final String reservationId;
        private final Long userId;
        private final BigDecimal discountAmount;
    }

    /**
     * 결제 실패 명령
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class PaymentFailedCommand {
        private final String orderId;
        private final String reservationId;
        private final Long userId;
    }

    /**
     * 결제 처리 결과
     */
    @Getter
    @Builder
    @AllArgsConstructor
    class PaymentResult {
        private final boolean success;
        private final String reservationId;
        private final String orderId;
        private final String message;

        public static PaymentResult success(String reservationId, String orderId) {
            return PaymentResult.builder()
                    .success(true)
                    .reservationId(reservationId)
                    .orderId(orderId)
                    .message("결제 처리 완료")
                    .build();
        }

        public static PaymentResult failure(String reservationId, String message) {
            return PaymentResult.builder()
                    .success(false)
                    .reservationId(reservationId)
                    .message(message)
                    .build();
        }
    }
}
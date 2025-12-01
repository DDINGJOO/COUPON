package com.teambind.coupon.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

/**
 * 쿠폰 사용 확정 커맨드
 * 결제 완료 이벤트를 받아 쿠폰을 사용 처리
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class ConfirmCouponUseCommand {

    @NotBlank(message = "예약 ID는 필수입니다")
    private String reservationId;

    @NotBlank(message = "주문 ID는 필수입니다")
    private String orderId;

    @NotNull(message = "결제 금액은 필수입니다")
    private BigDecimal paymentAmount;

    /**
     * 정적 팩토리 메서드
     */
    public static ConfirmCouponUseCommand of(String reservationId, String orderId, BigDecimal paymentAmount) {
        return ConfirmCouponUseCommand.builder()
                .reservationId(reservationId)
                .orderId(orderId)
                .paymentAmount(paymentAmount)
                .build();
    }
}
package com.teambind.coupon.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 쿠폰 사용 확정 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponConfirmRequest {

    @NotBlank(message = "예약 ID는 필수입니다")
    private String reservationId;

    @NotBlank(message = "주문 ID는 필수입니다")
    private String orderId;

    @NotNull(message = "실제 할인 금액은 필수입니다")
    @Positive(message = "할인 금액은 양수여야 합니다")
    private BigDecimal actualDiscountAmount;
}
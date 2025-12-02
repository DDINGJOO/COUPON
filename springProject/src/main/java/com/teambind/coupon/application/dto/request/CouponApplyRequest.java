package com.teambind.coupon.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 쿠폰 적용 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponApplyRequest {

    /**
     * 예약 ID
     */
    @NotBlank(message = "예약 ID는 필수입니다")
    private String reservationId;

    /**
     * 사용자 ID
     */
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    /**
     * 쿠폰 ID
     */
    @NotNull(message = "쿠폰 ID는 필수입니다")
    private Long couponId;

    /**
     * 주문 총액 (쿠폰 적용 전)
     */
    @NotNull(message = "주문 금액은 필수입니다")
    @Positive(message = "주문 금액은 양수여야 합니다")
    private BigDecimal orderAmount;
}
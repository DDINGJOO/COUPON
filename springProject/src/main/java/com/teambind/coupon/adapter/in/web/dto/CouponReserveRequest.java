package com.teambind.coupon.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 예약 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponReserveRequest {

    @NotBlank(message = "예약 ID는 필수입니다")
    private String reservationId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "쿠폰 ID는 필수입니다")
    private Long couponId;
}
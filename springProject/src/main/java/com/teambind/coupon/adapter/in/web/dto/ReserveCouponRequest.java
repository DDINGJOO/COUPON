package com.teambind.coupon.adapter.in.web.dto;

import com.teambind.coupon.application.port.in.ReserveCouponCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 예약 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class ReserveCouponRequest {

    @NotBlank(message = "예약 ID는 필수입니다")
    private String reservationId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "쿠폰 ID는 필수입니다")
    private Long couponId;

    /**
     * Command 객체로 변환
     */
    public ReserveCouponCommand toCommand() {
        return ReserveCouponCommand.of(reservationId, userId, couponId);
    }
}
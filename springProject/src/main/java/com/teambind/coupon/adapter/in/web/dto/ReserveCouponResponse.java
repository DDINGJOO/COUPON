package com.teambind.coupon.adapter.in.web.dto;

import com.teambind.coupon.application.port.in.ReserveCouponUseCase;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 예약 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
public class ReserveCouponResponse {

    private boolean success;
    private String reservationId;
    private Long couponId;
    private BigDecimal discountAmount;
    private String message;
    private LocalDateTime reservedUntil;

    /**
     * CouponReservationResult로부터 Response DTO 생성
     */
    public static ReserveCouponResponse from(ReserveCouponUseCase.CouponReservationResult result) {
        return ReserveCouponResponse.builder()
                .success(result.isSuccess())
                .reservationId(result.getReservationId())
                .couponId(result.getCouponId())
                .discountAmount(result.getDiscountAmount())
                .message(result.getMessage())
                .reservedUntil(result.getReservedUntil())
                .build();
    }
}
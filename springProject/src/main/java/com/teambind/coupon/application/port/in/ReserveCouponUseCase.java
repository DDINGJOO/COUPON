package com.teambind.coupon.application.port.in;

import com.teambind.coupon.domain.model.CouponIssue;

/**
 * 쿠폰 예약 UseCase
 * 결제 전 쿠폰을 예약 상태로 변경하는 기능
 */
public interface ReserveCouponUseCase {

    /**
     * 쿠폰 예약
     *
     * @param command 예약 요청 정보
     * @return 예약된 쿠폰 정보
     * @throws com.teambind.coupon.domain.exception.CouponDomainException 쿠폰 관련 예외
     */
    CouponReservationResult reserveCoupon(ReserveCouponCommand command);

    /**
     * 쿠폰 예약 결과
     */
    @lombok.Value
    @lombok.Builder
    class CouponReservationResult {
        boolean success;
        String reservationId;
        Long couponId;
        java.math.BigDecimal discountAmount;
        String message;
        java.time.LocalDateTime reservedUntil;
    }
}
package com.teambind.coupon.application.port.out;

import com.teambind.coupon.domain.model.CouponReservation;

/**
 * 쿠폰 예약 저장 Output Port
 */
public interface SaveReservationPort {

    /**
     * 예약 정보 저장
     *
     * @param reservation 예약 정보
     * @return 저장된 예약 정보
     */
    CouponReservation save(CouponReservation reservation);
}
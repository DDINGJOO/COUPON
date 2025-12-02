package com.teambind.coupon.application.port.out;

import com.teambind.coupon.domain.model.CouponReservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 예약 조회 Output Port
 */
public interface LoadReservationPort {

    /**
     * 예약 ID로 조회
     *
     * @param reservationId 예약 ID
     * @return 예약 정보
     */
    Optional<CouponReservation> findById(String reservationId);

    /**
     * 만료된 예약 조회
     *
     * @param now 현재 시간
     * @return 만료된 예약 목록
     */
    List<CouponReservation> findExpiredReservations(LocalDateTime now);
}
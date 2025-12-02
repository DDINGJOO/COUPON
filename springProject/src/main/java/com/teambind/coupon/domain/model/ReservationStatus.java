package com.teambind.coupon.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 예약 상태
 */
@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
    PENDING("대기 중"),
    CONFIRMED("확정됨"),
    CANCELLED("취소됨"),
    EXPIRED("만료됨");

    private final String description;
}
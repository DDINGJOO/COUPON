package com.teambind.coupon.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 발급된 쿠폰의 상태
 */
@Getter
@RequiredArgsConstructor
public enum CouponStatus {
    ISSUED("발급됨 - 사용 가능"),
    RESERVED("예약됨 - 결제 대기중"),
    USED("사용 완료"),
    EXPIRED("만료됨"),
    CANCELLED("취소됨");

    private final String description;

    /**
     * 쿠폰이 사용 가능한 상태인지 확인
     */
    public boolean isUsable() {
        return this == ISSUED || this == RESERVED;
    }

    /**
     * 쿠폰이 예약 가능한 상태인지 확인
     */
    public boolean isReservable() {
        return this == ISSUED;
    }
}
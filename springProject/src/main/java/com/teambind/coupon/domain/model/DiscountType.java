package com.teambind.coupon.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 할인 타입
 */
@Getter
@RequiredArgsConstructor
public enum DiscountType {
    AMOUNT("고정 금액 할인"),
    FIXED_AMOUNT("고정 금액 할인"),
    PERCENTAGE("퍼센트 할인");

    private final String description;
}
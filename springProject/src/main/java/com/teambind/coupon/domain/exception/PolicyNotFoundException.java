package com.teambind.coupon.domain.exception;

/**
 * 쿠폰 정책을 찾을 수 없을 때 발생하는 예외
 */
public class PolicyNotFoundException extends CouponDomainException {

    public PolicyNotFoundException(String message) {
        super(message);
    }

    public PolicyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
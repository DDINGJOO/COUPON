package com.teambind.coupon.domain.exception;

/**
 * 쿠폰을 찾을 수 없을 때 발생하는 예외
 */
public class CouponNotFoundException extends CouponDomainException {

    public CouponNotFoundException(String message) {
        super(message);
    }

    public CouponNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
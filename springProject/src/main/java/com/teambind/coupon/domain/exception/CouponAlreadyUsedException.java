package com.teambind.coupon.domain.exception;

/**
 * 이미 사용된 쿠폰일 때 발생하는 예외
 */
public class CouponAlreadyUsedException extends CouponDomainException {

    public CouponAlreadyUsedException(String message) {
        super(message);
    }

    public CouponAlreadyUsedException(String message, Throwable cause) {
        super(message, cause);
    }
}
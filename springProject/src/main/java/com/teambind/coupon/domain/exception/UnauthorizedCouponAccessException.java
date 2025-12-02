package com.teambind.coupon.domain.exception;

/**
 * 쿠폰에 대한 접근 권한이 없을 때 발생하는 예외
 */
public class UnauthorizedCouponAccessException extends CouponDomainException {

    public UnauthorizedCouponAccessException(String message) {
        super(message);
    }

    public UnauthorizedCouponAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
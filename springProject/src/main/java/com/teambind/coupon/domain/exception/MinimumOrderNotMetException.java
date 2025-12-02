package com.teambind.coupon.domain.exception;

/**
 * 최소 주문 금액을 충족하지 못했을 때 발생하는 예외
 */
public class MinimumOrderNotMetException extends CouponDomainException {

    public MinimumOrderNotMetException(String message) {
        super(message);
    }

    public MinimumOrderNotMetException(String message, Throwable cause) {
        super(message, cause);
    }
}
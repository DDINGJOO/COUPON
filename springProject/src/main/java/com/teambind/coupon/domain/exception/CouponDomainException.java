package com.teambind.coupon.domain.exception;

/**
 * 쿠폰 도메인 관련 예외
 */
public class CouponDomainException extends RuntimeException {

    public CouponDomainException(String message) {
        super(message);
    }

    public CouponDomainException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class CouponNotFound extends CouponDomainException {
        public CouponNotFound(Long couponId) {
            super("쿠폰을 찾을 수 없습니다: " + couponId);
        }
    }

    public static class CouponAlreadyUsed extends CouponDomainException {
        public CouponAlreadyUsed(Long couponId) {
            super("이미 사용된 쿠폰입니다: " + couponId);
        }
    }

    public static class CouponExpired extends CouponDomainException {
        public CouponExpired(Long couponId) {
            super("만료된 쿠폰입니다: " + couponId);
        }
    }

    public static class CouponStockExhausted extends CouponDomainException {
        public CouponStockExhausted(String couponCode) {
            super("쿠폰 재고가 모두 소진되었습니다: " + couponCode);
        }
    }

    public static class UserCouponLimitExceeded extends CouponDomainException {
        public UserCouponLimitExceeded(Long userId, String couponCode) {
            super(String.format("사용자 쿠폰 발급 한도 초과 - userId: %d, couponCode: %s", userId, couponCode));
        }
    }

    public static class InvalidRequest extends CouponDomainException {
        public InvalidRequest(String message) {
            super(message);
        }
    }

    public static class CouponNotIssuable extends CouponDomainException {
        public CouponNotIssuable(String message) {
            super(message);
        }
    }

    public static class ExceedMaxUsage extends CouponDomainException {
        public ExceedMaxUsage(String message) {
            super(message);
        }
    }

    public static class StockExhausted extends CouponDomainException {
        public StockExhausted(String message) {
            super(message);
        }
    }
}
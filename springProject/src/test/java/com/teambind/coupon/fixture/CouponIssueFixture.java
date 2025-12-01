package com.teambind.coupon.fixture;

import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountPolicy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 발급 테스트 픽스처
 * 테스트용 CouponIssue 객체 생성 팩토리
 */
public class CouponIssueFixture {

    private static long idCounter = 1000L;

    /**
     * 발급된 상태의 쿠폰 생성
     */
    public static CouponIssue createIssuedCoupon(Long userId, CouponPolicy policy) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 예약된 상태의 쿠폰 생성
     */
    public static CouponIssue createReservedCoupon(Long userId, CouponPolicy policy, String reservationId) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.RESERVED)
                .reservationId(reservationId)
                .issuedAt(LocalDateTime.now().minusMinutes(10))
                .reservedAt(LocalDateTime.now())
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 사용된 상태의 쿠폰 생성
     */
    public static CouponIssue createUsedCoupon(Long userId, CouponPolicy policy, String orderId) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.USED)
                .orderId(orderId)
                .issuedAt(LocalDateTime.now().minusHours(2))
                .reservedAt(LocalDateTime.now().minusHours(1))
                .usedAt(LocalDateTime.now())
                .actualDiscountAmount(new BigDecimal("5000"))
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 만료된 상태의 쿠폰 생성
     */
    public static CouponIssue createExpiredCoupon(Long userId, CouponPolicy policy) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.EXPIRED)
                .issuedAt(LocalDateTime.now().minusDays(31))
                .expiredAt(LocalDateTime.now().minusDays(1))
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 취소된 상태의 쿠폰 생성
     */
    public static CouponIssue createCancelledCoupon(Long userId, CouponPolicy policy) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.CANCELLED)
                .issuedAt(LocalDateTime.now().minusDays(7))
                .reservedAt(LocalDateTime.now().minusDays(7))
                .usedAt(LocalDateTime.now().minusDays(6))
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .actualDiscountAmount(new BigDecimal("3000"))
                .build();
    }

    /**
     * 타임아웃 직전의 예약 쿠폰 생성 (29분 경과)
     */
    public static CouponIssue createNearTimeoutReservedCoupon(Long userId, CouponPolicy policy, String reservationId) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.RESERVED)
                .reservationId(reservationId)
                .issuedAt(LocalDateTime.now().minusMinutes(30))
                .reservedAt(LocalDateTime.now().minusMinutes(29))
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 타임아웃 대상 예약 쿠폰 생성 (31분 경과)
     */
    public static CouponIssue createTimeoutReservedCoupon(Long userId, CouponPolicy policy, String reservationId) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.RESERVED)
                .reservationId(reservationId)
                .issuedAt(LocalDateTime.now().minusMinutes(35))
                .reservedAt(LocalDateTime.now().minusMinutes(31))
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 대량 발급 테스트용 쿠폰 리스트 생성
     */
    public static CouponIssue[] createBulkIssuedCoupons(Long[] userIds, CouponPolicy policy) {
        CouponIssue[] coupons = new CouponIssue[userIds.length];
        for (int i = 0; i < userIds.length; i++) {
            coupons[i] = createIssuedCoupon(userIds[i], policy);
        }
        return coupons;
    }

    /**
     * 커스텀 할인 정책을 가진 쿠폰 생성
     */
    public static CouponIssue createCustomDiscountCoupon(Long userId, Long policyId, DiscountPolicy discountPolicy) {
        return CouponIssue.builder()
                .id(generateId())
                .policyId(policyId)
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("커스텀 할인 쿠폰")
                .discountPolicy(discountPolicy)
                .build();
    }

    /**
     * ID 생성기
     */
    private static synchronized Long generateId() {
        return idCounter++;
    }

    /**
     * ID 카운터 리셋 (테스트 간 격리를 위해)
     */
    public static void resetIdCounter() {
        idCounter = 1000L;
    }
}
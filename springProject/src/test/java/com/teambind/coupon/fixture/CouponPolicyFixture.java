package com.teambind.coupon.fixture;

import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.DiscountPolicy;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import com.teambind.coupon.domain.model.ItemApplicableRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 쿠폰 정책 테스트 픽스처
 * 테스트용 CouponPolicy 객체 생성 팩토리
 */
public class CouponPolicyFixture {

    /**
     * 기본 CODE 타입 쿠폰 정책 생성
     */
    public static CouponPolicy createCodePolicy() {
        return createCodePolicy("SUMMER2024", "여름 특별 할인 쿠폰");
    }

    /**
     * 커스텀 CODE 타입 쿠폰 정책 생성
     */
    public static CouponPolicy createCodePolicy(String couponCode, String couponName) {
        CouponPolicy policy = CouponPolicy.builder()
                .id(1L)
                .couponName(couponName)
                .couponCode(couponCode)
                .description("테스트용 CODE 타입 쿠폰")
                .discountPolicy(createPercentageDiscount(10))
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(100)
                .maxUsagePerUser(1)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        policy.setCurrentIssueCount(new AtomicInteger(0));
        return policy;
    }

    /**
     * DIRECT 타입 쿠폰 정책 생성
     */
    public static CouponPolicy createDirectPolicy() {
        return createDirectPolicy("관리자 직접 발급 쿠폰", 1000);
    }

    /**
     * 커스텀 DIRECT 타입 쿠폰 정책 생성
     */
    public static CouponPolicy createDirectPolicy(String couponName, Integer maxIssueCount) {
        CouponPolicy policy = CouponPolicy.builder()
                .id(2L)
                .couponName(couponName)
                .description("테스트용 DIRECT 타입 쿠폰")
                .discountPolicy(createAmountDiscount(5000))
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(maxIssueCount)
                .maxUsagePerUser(3)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        policy.setCurrentIssueCount(new AtomicInteger(0));
        return policy;
    }

    /**
     * EVENT 타입 쿠폰 정책 생성
     */
    public static CouponPolicy createEventPolicy() {
        CouponPolicy policy = CouponPolicy.builder()
                .id(3L)
                .couponName("이벤트 쿠폰")
                .description("테스트용 EVENT 타입 쿠폰")
                .discountPolicy(createPercentageDiscount(20))
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.EVENT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(7))
                .maxIssueCount(50)
                .maxUsagePerUser(1)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        policy.setCurrentIssueCount(new AtomicInteger(0));
        return policy;
    }

    /**
     * 만료된 쿠폰 정책 생성
     */
    public static CouponPolicy createExpiredPolicy() {
        CouponPolicy policy = CouponPolicy.builder()
                .id(4L)
                .couponName("만료된 쿠폰")
                .couponCode("EXPIRED2024")
                .description("테스트용 만료된 쿠폰")
                .discountPolicy(createPercentageDiscount(15))
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(30))
                .validUntil(LocalDateTime.now().minusDays(1))
                .maxIssueCount(100)
                .maxUsagePerUser(1)
                .isActive(true)
                .createdAt(LocalDateTime.now().minusDays(31))
                .updatedAt(LocalDateTime.now().minusDays(31))
                .createdBy(1L)
                .build();

        policy.setCurrentIssueCount(new AtomicInteger(0));
        return policy;
    }

    /**
     * 재고가 소진된 쿠폰 정책 생성
     */
    public static CouponPolicy createSoldOutPolicy() {
        CouponPolicy policy = CouponPolicy.builder()
                .id(5L)
                .couponName("품절 쿠폰")
                .couponCode("SOLDOUT2024")
                .description("테스트용 품절 쿠폰")
                .discountPolicy(createPercentageDiscount(30))
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(10)
                .maxUsagePerUser(1)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        // 재고 소진 상태로 설정
        policy.setCurrentIssueCount(new AtomicInteger(10));
        return policy;
    }

    /**
     * 무제한 재고 쿠폰 정책 생성
     */
    public static CouponPolicy createUnlimitedPolicy() {
        CouponPolicy policy = CouponPolicy.builder()
                .id(6L)
                .couponName("무제한 쿠폰")
                .couponCode("UNLIMITED")
                .description("테스트용 무제한 쿠폰")
                .discountPolicy(createAmountDiscount(1000))
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(365))
                .maxIssueCount(null) // 무제한
                .maxUsagePerUser(5)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        policy.setCurrentIssueCount(new AtomicInteger(0));
        return policy;
    }

    /**
     * 비활성화된 쿠폰 정책 생성
     */
    public static CouponPolicy createInactivePolicy() {
        CouponPolicy policy = CouponPolicy.builder()
                .id(7L)
                .couponName("비활성 쿠폰")
                .couponCode("INACTIVE")
                .description("테스트용 비활성 쿠폰")
                .discountPolicy(createPercentageDiscount(25))
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(100)
                .maxUsagePerUser(1)
                .isActive(false) // 비활성화
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        policy.setCurrentIssueCount(new AtomicInteger(0));
        return policy;
    }

    /**
     * 퍼센트 할인 정책 생성
     */
    private static DiscountPolicy createPercentageDiscount(int percentage) {
        return DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal(percentage))
                .minOrderAmount(new BigDecimal("10000"))
                .maxDiscountAmount(new BigDecimal("100000"))
                .build();
    }

    /**
     * 금액 할인 정책 생성
     */
    private static DiscountPolicy createAmountDiscount(int amount) {
        return DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal(amount))
                .minOrderAmount(new BigDecimal("30000"))
                .maxDiscountAmount(null)
                .build();
    }
}
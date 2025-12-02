package com.teambind.coupon.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DiscountPolicy 도메인 모델 테스트
 */
@DisplayName("DiscountPolicy 도메인 모델 테스트")
class DiscountPolicyTest {

    @Test
    @DisplayName("고정 금액 할인 계산")
    void calculateFixedAmountDiscount() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .build();

        BigDecimal orderAmount = BigDecimal.valueOf(30000);

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("고정 금액 할인 - 주문 금액보다 할인이 큰 경우")
    void calculateFixedAmountDiscount_ExceedsOrderAmount() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(10000))
                .build();

        BigDecimal orderAmount = BigDecimal.valueOf(5000);

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000)); // 주문 금액 이상 할인 불가
    }

    @Test
    @DisplayName("퍼센트 할인 계산")
    void calculatePercentageDiscount() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10)) // 10%
                .build();

        BigDecimal orderAmount = BigDecimal.valueOf(50000);

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("퍼센트 할인 - 최대 할인 금액 제한")
    void calculatePercentageDiscount_WithMaxLimit() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20)) // 20%
                .maxDiscountAmount(BigDecimal.valueOf(10000)) // 최대 10000원
                .build();

        BigDecimal orderAmount = BigDecimal.valueOf(100000); // 20% = 20000원

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(10000)); // 최대 할인 금액으로 제한
    }

    @Test
    @DisplayName("최소 주문 금액 미충족")
    void calculateDiscount_BelowMinimumOrder() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minOrderAmount(BigDecimal.valueOf(30000))
                .build();

        BigDecimal orderAmount = BigDecimal.valueOf(20000);

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("최소 주문 금액 충족")
    void calculateDiscount_MeetsMinimumOrder() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minOrderAmount(BigDecimal.valueOf(30000))
                .build();

        BigDecimal orderAmount = BigDecimal.valueOf(30000);

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @ParameterizedTest
    @CsvSource({
            "100000, 10, , 10000",      // 10% 할인, 제한 없음
            "100000, 10, 5000, 5000",    // 10% 할인, 5000원 제한
            "50000, 15, , 7500",         // 15% 할인, 제한 없음
            "50000, 15, 10000, 7500",    // 15% 할인, 10000원 제한 (미달)
            "30000, 50, , 15000",        // 50% 할인, 제한 없음
            "30000, 50, 10000, 10000"    // 50% 할인, 10000원 제한
    })
    @DisplayName("퍼센트 할인 다양한 케이스")
    void calculatePercentageDiscount_VariousCases(
            String orderAmountStr,
            String discountValueStr,
            String maxDiscountStr,
            String expectedStr) {

        // given
        BigDecimal maxDiscount = maxDiscountStr != null && !maxDiscountStr.isEmpty()
                ? new BigDecimal(maxDiscountStr) : null;

        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal(discountValueStr))
                .maxDiscountAmount(maxDiscount)
                .build();

        BigDecimal orderAmount = new BigDecimal(orderAmountStr);

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(new BigDecimal(expectedStr));
    }

    @Test
    @DisplayName("할인 정보 문자열 변환 - 고정 금액")
    void toDisplayString_FixedAmount() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .build();

        // when
        String display = policy.toDisplayString();

        // then
        assertThat(display).isEqualTo("5,000원 할인");
    }

    @Test
    @DisplayName("할인 정보 문자열 변환 - 퍼센트")
    void toDisplayString_Percentage() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .build();

        // when
        String display = policy.toDisplayString();

        // then
        assertThat(display).isEqualTo("10% 할인");
    }

    @Test
    @DisplayName("할인 정보 문자열 변환 - 퍼센트 (최대 할인 금액 포함)")
    void toDisplayString_PercentageWithMax() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(15))
                .maxDiscountAmount(BigDecimal.valueOf(10000))
                .build();

        // when
        String display = policy.toDisplayString();

        // then
        assertThat(display).isEqualTo("15% 할인 (최대 10,000원)");
    }

    @Test
    @DisplayName("정적 팩토리 메서드 - 고정 금액")
    void staticFactory_FixedAmount() {
        // when
        DiscountPolicy policy = DiscountPolicy.fixedAmount(BigDecimal.valueOf(3000));

        // then
        assertThat(policy.getDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
        assertThat(policy.getDiscountValue()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(policy.getMinOrderAmount()).isNull();
        assertThat(policy.getMaxDiscountAmount()).isNull();
    }

    @Test
    @DisplayName("정적 팩토리 메서드 - 퍼센트")
    void staticFactory_Percentage() {
        // when
        DiscountPolicy policy = DiscountPolicy.percentage(
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(15000)
        );

        // then
        assertThat(policy.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(policy.getDiscountValue()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(policy.getMaxDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(15000));
    }

    @Test
    @DisplayName("주문 금액이 0원인 경우")
    void calculateDiscount_ZeroOrderAmount() {
        // given
        DiscountPolicy amountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .build();

        DiscountPolicy percentPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .build();

        // when
        BigDecimal amountDiscount = amountPolicy.calculateDiscountAmount(BigDecimal.ZERO);
        BigDecimal percentDiscount = percentPolicy.calculateDiscountAmount(BigDecimal.ZERO);

        // then
        assertThat(amountDiscount).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(percentDiscount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("소수점 포함 퍼센트 할인")
    void calculatePercentageDiscount_WithDecimals() {
        // given
        DiscountPolicy policy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("12.5")) // 12.5%
                .build();

        BigDecimal orderAmount = new BigDecimal("40000");

        // when
        BigDecimal discount = policy.calculateDiscountAmount(orderAmount);

        // then
        assertThat(discount).isEqualByComparingTo(new BigDecimal("5000"));
    }
}
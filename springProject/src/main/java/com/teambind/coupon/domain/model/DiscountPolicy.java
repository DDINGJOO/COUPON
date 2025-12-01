package com.teambind.coupon.domain.model;

import lombok.*;

import java.math.BigDecimal;

/**
 * 할인 정책 Value Object
 * 쿠폰의 할인 방식과 금액/비율을 정의
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DiscountPolicy {

    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount; // 최소 주문 금액
    private BigDecimal maxDiscountAmount; // 퍼센트 할인시 최대 할인 금액

    /**
     * 실제 할인 금액 계산
     * @param originalPrice 원래 가격
     * @return 할인 금액
     */
    public BigDecimal calculateDiscountAmount(BigDecimal originalPrice) {
        // 최소 주문 금액 체크
        if (minOrderAmount != null && originalPrice.compareTo(minOrderAmount) < 0) {
            return BigDecimal.ZERO;
        }

        if (discountType == DiscountType.FIXED_AMOUNT || discountType == DiscountType.AMOUNT) {
            return discountValue.min(originalPrice);
        } else { // PERCENTAGE
            BigDecimal percentDiscount = originalPrice
                    .multiply(discountValue)
                    .divide(BigDecimal.valueOf(100));

            // 최대 할인 금액 제한 적용
            if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
                percentDiscount = percentDiscount.min(maxDiscountAmount);
            }

            return percentDiscount.min(originalPrice);
        }
    }

    /**
     * 할인 정보를 사용자에게 표시할 문자열로 변환
     */
    public String toDisplayString() {
        if (discountType == DiscountType.FIXED_AMOUNT || discountType == DiscountType.AMOUNT) {
            return String.format("%,d원 할인", discountValue.longValue());
        } else {
            String base = discountValue.stripTrailingZeros().toPlainString() + "% 할인";
            if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
                base += String.format(" (최대 %,d원)", maxDiscountAmount.longValue());
            }
            return base;
        }
    }

    /**
     * 고정 금액 할인 정책 생성
     */
    public static DiscountPolicy fixedAmount(BigDecimal amount) {
        return new DiscountPolicy(DiscountType.FIXED_AMOUNT, amount, null, null);
    }

    /**
     * 퍼센트 할인 정책 생성
     */
    public static DiscountPolicy percentage(BigDecimal percent, BigDecimal maxAmount) {
        return new DiscountPolicy(DiscountType.PERCENTAGE, percent, null, maxAmount);
    }
}
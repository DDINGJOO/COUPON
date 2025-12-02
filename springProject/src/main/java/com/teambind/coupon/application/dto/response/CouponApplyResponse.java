package com.teambind.coupon.application.dto.response;

import com.teambind.coupon.domain.model.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 쿠폰 적용 응답 DTO (CouponRequest)
 * Gateway에서 쿠폰 서버를 통해 검증된 쿠폰 정보를 전달받습니다.
 * 이미 검증이 완료된 정보이므로 추가 검증 없이 적용합니다.
 */
@Getter
@Builder
public class CouponApplyResponse {

    /**
     * 쿠폰 ID
     */
    private final String couponId;

    /**
     * 쿠폰 이름 (예: "신규 회원 5000원 할인")
     */
    private final String couponName;

    /**
     * 할인 타입 (AMOUNT: 금액 할인, PERCENTAGE: 퍼센트 할인)
     */
    private final DiscountType discountType;

    /**
     * 할인 수치
     * - AMOUNT: 할인 금액 (예: 5000)
     * - PERCENTAGE: 할인율 (예: 10)
     */
    private final BigDecimal discountValue;

    /**
     * 최대 할인 금액 (PERCENTAGE 타입일 때만 사용)
     */
    private final BigDecimal maxDiscountAmount;

    /**
     * 쿠폰 정보가 비어있는지 확인
     *
     * @return 쿠폰 정보가 없으면 true
     */
    public boolean isEmpty() {
        return couponId == null || couponId.trim().isEmpty();
    }

    /**
     * 빈 응답 생성 (적용 가능한 쿠폰이 없을 때)
     */
    public static CouponApplyResponse empty() {
        return CouponApplyResponse.builder().build();
    }
}
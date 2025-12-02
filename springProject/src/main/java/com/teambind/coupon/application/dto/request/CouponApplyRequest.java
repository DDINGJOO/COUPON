package com.teambind.coupon.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 쿠폰 적용 요청 DTO
 * 사용자 ID와 상품 ID 목록을 받아 쿠폰 적용 가능 여부를 확인합니다.
 */
@Getter
@Builder
public class CouponApplyRequest {

    /**
     * 사용자 ID
     */
    @NotNull(message = "사용자 ID는 필수입니다")
    private final Long userId;

    /**
     * 상품 ID 목록 (최대 2개)
     */
    @NotNull(message = "상품 ID는 필수입니다")
    @Size(min = 1, max = 2, message = "상품 ID는 1개 이상 2개 이하여야 합니다")
    private final List<Long> productIds;

    /**
     * 주문 총액 (쿠폰 적용 전)
     */
    @NotNull(message = "주문 금액은 필수입니다")
    private final Long orderAmount;
}
package com.teambind.coupon.application.port.out;

import com.teambind.coupon.domain.model.CouponPolicy;

/**
 * 쿠폰 정책 저장 Output Port
 */
public interface SaveCouponPolicyPort {

    /**
     * 쿠폰 정책 저장
     */
    CouponPolicy save(CouponPolicy policy);

    /**
     * 쿠폰 정책 업데이트
     */
    void update(CouponPolicy policy);

    /**
     * 재고 차감 (원자적 업데이트)
     * @return 차감 성공 여부
     */
    boolean decrementStock(Long policyId);
}
package com.teambind.coupon.application.port.in;

import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;

/**
 * 쿠폰 적용 UseCase
 * 결제 전 쿠폰을 적용하고 락을 잡는 기능
 */
public interface ApplyCouponUseCase {

    /**
     * 쿠폰 적용 및 락 획득
     *
     * @param request 쿠폰 적용 요청 (userId, productIds, orderAmount)
     * @return 적용 가능한 쿠폰 정보 (없으면 empty response)
     */
    CouponApplyResponse applyCoupon(CouponApplyRequest request);

    /**
     * 쿠폰 락 해제
     *
     * @param reservationId 예약 ID
     */
    void releaseCouponLock(String reservationId);
}
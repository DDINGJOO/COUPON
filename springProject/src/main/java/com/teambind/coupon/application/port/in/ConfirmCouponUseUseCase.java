package com.teambind.coupon.application.port.in;

/**
 * 쿠폰 사용 확정 UseCase
 * 결제 완료 시 예약된 쿠폰을 사용 완료 상태로 변경
 */
public interface ConfirmCouponUseUseCase {

    /**
     * 쿠폰 사용 확정
     *
     * @param command 사용 확정 요청 정보
     */
    void confirmCouponUse(ConfirmCouponUseCommand command);
}
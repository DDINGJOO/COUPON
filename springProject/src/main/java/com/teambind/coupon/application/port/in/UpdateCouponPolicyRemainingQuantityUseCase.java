package com.teambind.coupon.application.port.in;

import com.teambind.coupon.application.service.CouponPolicyManagementService;

/**
 * 쿠폰 정책 남은 발급 수량 수정 UseCase
 * 생성된 쿠폰 정책의 유일하게 수정 가능한 필드 처리
 */
public interface UpdateCouponPolicyRemainingQuantityUseCase {

    /**
     * 쿠폰 정책의 남은 발급 수량 업데이트
     *
     * @param command 수정 요청 커맨드
     * @return 수정 결과
     */
    CouponPolicyManagementService.UpdateResult updateRemainingQuantity(
            UpdateCouponPolicyRemainingQuantityCommand command);
}
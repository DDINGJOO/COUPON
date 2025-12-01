package com.teambind.coupon.application.port.in;

import com.teambind.coupon.domain.model.CouponIssue;

/**
 * 쿠폰 다운로드 UseCase
 * CODE 타입 쿠폰을 사용자가 다운로드하는 기능
 */
public interface DownloadCouponUseCase {

    /**
     * 쿠폰 코드를 통한 쿠폰 다운로드
     *
     * @param command 다운로드 요청 정보
     * @return 발급된 쿠폰 정보
     * @throws com.teambind.coupon.domain.exception.CouponDomainException 쿠폰 관련 예외
     */
    CouponIssue downloadCoupon(DownloadCouponCommand command);
}
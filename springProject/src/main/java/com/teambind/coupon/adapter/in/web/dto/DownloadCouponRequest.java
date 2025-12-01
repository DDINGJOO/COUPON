package com.teambind.coupon.adapter.in.web.dto;

import com.teambind.coupon.application.port.in.DownloadCouponCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 다운로드 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class DownloadCouponRequest {

    @NotBlank(message = "쿠폰 코드는 필수입니다")
    private String couponCode;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    /**
     * Command 객체로 변환
     */
    public DownloadCouponCommand toCommand() {
        return DownloadCouponCommand.of(couponCode, userId);
    }
}
package com.teambind.coupon.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 쿠폰 다운로드 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponDownloadRequest {

    @NotBlank(message = "쿠폰 코드는 필수입니다")
    private String couponCode;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
}
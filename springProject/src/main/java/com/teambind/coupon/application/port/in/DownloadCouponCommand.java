package com.teambind.coupon.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 쿠폰 다운로드 커맨드
 * CODE 타입 쿠폰을 사용자가 다운로드하기 위한 요청
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class DownloadCouponCommand {

    @NotBlank(message = "쿠폰 코드는 필수입니다")
    private String couponCode;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    /**
     * 정적 팩토리 메서드
     */
    public static DownloadCouponCommand of(String couponCode, Long userId) {
        return DownloadCouponCommand.builder()
                .couponCode(couponCode)
                .userId(userId)
                .build();
    }
}
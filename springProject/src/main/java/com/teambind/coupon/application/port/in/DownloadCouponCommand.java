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
        // 유효성 검증
        if (couponCode == null || couponCode.trim().isEmpty()) {
            throw new IllegalArgumentException("쿠폰 코드는 필수입니다");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("유효한 사용자 ID가 필요합니다");
        }

        return DownloadCouponCommand.builder()
                .couponCode(couponCode)
                .userId(userId)
                .build();
    }
}
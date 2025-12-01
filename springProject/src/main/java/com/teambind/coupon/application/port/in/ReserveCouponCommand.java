package com.teambind.coupon.application.port.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 쿠폰 예약 커맨드
 * 게이트웨이에서 전달받은 예약 정보로 쿠폰을 예약 상태로 변경
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class ReserveCouponCommand {

    @NotBlank(message = "예약 ID는 필수입니다")
    private String reservationId;

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "쿠폰 ID는 필수입니다")
    private Long couponId;

    /**
     * 정적 팩토리 메서드
     */
    public static ReserveCouponCommand of(String reservationId, Long userId, Long couponId) {
        return ReserveCouponCommand.builder()
                .reservationId(reservationId)
                .userId(userId)
                .couponId(couponId)
                .build();
    }
}
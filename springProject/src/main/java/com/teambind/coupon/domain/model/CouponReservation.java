package com.teambind.coupon.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 예약 도메인 모델
 * 쿠폰 사용을 위한 예약 정보를 관리합니다.
 */
@Getter
@Builder
@AllArgsConstructor
public class CouponReservation {

    private String reservationId;        // 예약 ID
    private Long couponId;              // 쿠폰 ID
    private Long userId;                // 사용자 ID
    private String orderId;             // 주문 ID
    private BigDecimal orderAmount;     // 주문 금액
    private BigDecimal discountAmount;  // 할인 금액
    private LocalDateTime reservedAt;   // 예약 일시
    private LocalDateTime expiresAt;    // 예약 만료 일시
    private ReservationStatus status;   // 예약 상태
    private String lockValue;           // Redis 락 value (안전한 락 해제용)

    /**
     * 예약 확정
     */
    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }

    /**
     * 예약 취소
     *
     * @param reason 취소 사유
     */
    public void cancel(String reason) {
        this.status = ReservationStatus.CANCELLED;
    }

    /**
     * 예약 만료
     */
    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }

    /**
     * 취소 가능 여부 확인
     *
     * @return 취소 가능하면 true
     */
    public boolean isCancellable() {
        return status == ReservationStatus.PENDING;
    }
}
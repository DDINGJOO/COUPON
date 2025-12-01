package com.teambind.coupon.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 발급된 쿠폰 도메인 모델
 * 사용자에게 발급된 쿠폰 인스턴스를 나타냄
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CouponIssue {

    private Long id; // Snowflake ID
    private Long policyId; // 쿠폰 정책 ID
    private Long userId; // 사용자 ID

    private CouponStatus status;
    private String reservationId; // 예약 ID (RESERVED 상태일 때)
    private String orderId; // 주문 ID (USED 상태일 때)

    private LocalDateTime issuedAt;
    private LocalDateTime reservedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiredAt;

    private BigDecimal actualDiscountAmount; // 실제 적용된 할인 금액

    // 관련 정책 정보 (조회 성능을 위한 denormalization)
    private String couponName;
    private DiscountPolicy discountPolicy;

    /**
     * 쿠폰 예약 처리
     * @param reservationId 예약 ID
     * @return 예약 성공 여부
     */
    public boolean reserve(String reservationId) {
        if (!status.isReservable()) {
            return false;
        }

        this.status = CouponStatus.RESERVED;
        this.reservationId = reservationId;
        this.reservedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 예약 시간 초과 확인
     * @param timeoutMinutes 타임아웃 시간(분)
     * @return 타임아웃 여부
     */
    public boolean isReservationTimeout(int timeoutMinutes) {
        if (status != CouponStatus.RESERVED || reservedAt == null) {
            return false;
        }

        LocalDateTime timeoutTime = reservedAt.plusMinutes(timeoutMinutes);
        return LocalDateTime.now().isAfter(timeoutTime);
    }

    /**
     * 쿠폰 예약 취소 (타임아웃 또는 결제 실패)
     */
    public void cancelReservation() {
        if (status == CouponStatus.RESERVED) {
            this.status = CouponStatus.ISSUED;
            this.reservationId = null;
            this.reservedAt = null;
        }
    }

    /**
     * 쿠폰 사용 처리
     * @param orderId 주문 ID
     * @param discountAmount 실제 할인 금액
     * @return 사용 성공 여부
     */
    public boolean use(String orderId, BigDecimal discountAmount) {
        if (!status.isUsable() && status != CouponStatus.RESERVED) {
            return false;
        }

        this.status = CouponStatus.USED;
        this.orderId = orderId;
        this.usedAt = LocalDateTime.now();
        this.actualDiscountAmount = discountAmount;
        return true;
    }

    /**
     * 쿠폰 사용 확정
     * @param orderId 주문 ID
     * @param discountAmount 실제 할인 금액
     */
    public void confirmUsage(String orderId, BigDecimal discountAmount) {
        if (status != CouponStatus.RESERVED) {
            throw new IllegalStateException("예약된 쿠폰만 사용 확정이 가능합니다.");
        }

        this.status = CouponStatus.USED;
        this.orderId = orderId;
        this.usedAt = LocalDateTime.now();
        this.actualDiscountAmount = discountAmount;
    }

    /**
     * 쿠폰 예약 해제
     * @return 해제 성공 여부
     */
    public boolean release() {
        if (status != CouponStatus.RESERVED) {
            return false;
        }

        this.status = CouponStatus.ISSUED;
        this.reservationId = null;
        this.reservedAt = null;
        return true;
    }

    /**
     * 쿠폰 취소 처리
     */
    public void cancel() {
        this.status = CouponStatus.CANCELLED;
    }

    /**
     * 쿠폰 만료 처리
     */
    public void expire() {
        if (status == CouponStatus.ISSUED) {
            this.status = CouponStatus.EXPIRED;
            this.expiredAt = LocalDateTime.now();
        }
    }

    /**
     * 쿠폰 유효기간 만료 여부 확인
     * @param validUntil 정책의 유효기간 종료일
     * @return 만료 여부
     */
    public boolean isExpired(LocalDateTime validUntil) {
        return LocalDateTime.now().isAfter(validUntil) && status == CouponStatus.ISSUED;
    }

    /**
     * 현재 사용 가능한 상태인지 확인
     */
    public boolean isUsable() {
        return status == CouponStatus.ISSUED;
    }

    /**
     * 예약 ID와 일치하는지 확인
     */
    public boolean matchesReservation(String reservationId) {
        return this.reservationId != null && this.reservationId.equals(reservationId);
    }
}
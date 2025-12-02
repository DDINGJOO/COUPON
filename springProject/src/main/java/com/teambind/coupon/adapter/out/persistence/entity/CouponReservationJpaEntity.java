package com.teambind.coupon.adapter.out.persistence.entity;

import com.teambind.coupon.domain.model.ReservationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 예약 JPA 엔티티
 */
@Entity
@Table(name = "coupon_reservations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponReservationJpaEntity {

    @Id
    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "order_amount", nullable = false)
    private BigDecimal orderAmount;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "lock_value")
    private String lockValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
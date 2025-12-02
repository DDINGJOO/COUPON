package com.teambind.coupon.adapter.out.persistence.entity;

import com.teambind.coupon.domain.model.CouponStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 발급된 쿠폰 JPA 엔티티
 */
@Entity
@Table(name = "coupon_issues",
        indexes = {
                @Index(name = "idx_user_status", columnList = "user_id, status"),
                @Index(name = "idx_user_active", columnList = "user_id, expires_at"),
                @Index(name = "idx_reservation", columnList = "reservation_id"),
                @Index(name = "idx_timeout_check", columnList = "status, reserved_at"),
                @Index(name = "idx_policy_id", columnList = "policy_id")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CouponIssueEntity extends BaseEntity {

    @Id
    private Long id; // Snowflake ID

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "reservation_id", unique = true)
    private String reservationId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // 정책의 valid_until

    @Column(name = "actual_discount_amount", precision = 10, scale = 2)
    private BigDecimal actualDiscountAmount;

    // 조회 성능을 위한 denormalized 필드
    @Column(name = "coupon_name", length = 100)
    private String couponName;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_type", length = 20)
    private String discountType;

    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Version
    private Long version; // Optimistic Locking

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", insertable = false, updatable = false)
    private CouponPolicyEntity policy;

    /**
     * 쿠폰 예약 취소 (롤백)
     * RESERVED 상태를 다시 ISSUED 상태로 변경
     */
    public void rollback() {
        if (this.status == CouponStatus.RESERVED) {
            this.status = CouponStatus.ISSUED;
            this.reservationId = null;
            this.reservedAt = null;
        }
    }

    /**
     * 쿠폰 만료 처리
     * ISSUED 또는 RESERVED 상태를 EXPIRED로 변경
     */
    public void expire() {
        if (this.status == CouponStatus.ISSUED || this.status == CouponStatus.RESERVED) {
            this.status = CouponStatus.EXPIRED;
            this.expiredAt = LocalDateTime.now();
        }
    }
}
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

    @Column(nullable = false)
    private Long policyId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(unique = true)
    private String reservationId;

    private String orderId;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    private LocalDateTime reservedAt;
    private LocalDateTime usedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime expiresAt; // 정책의 valid_until

    @Column(precision = 10, scale = 2)
    private BigDecimal actualDiscountAmount;

    // 조회 성능을 위한 denormalized 필드
    @Column(length = 100)
    private String couponName;

    @Column(precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(length = 20)
    private String discountType;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Version
    private Long version; // Optimistic Locking

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", insertable = false, updatable = false)
    private CouponPolicyEntity policy;
}
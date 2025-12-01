package com.teambind.coupon.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 쿠폰 통계 JPA 엔티티
 * 일별 집계 데이터 저장
 */
@Entity
@Table(name = "coupon_statistics",
        indexes = {
                @Index(name = "idx_policy_date", columnList = "policy_id, date"),
                @Index(name = "idx_date", columnList = "date")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"policy_id", "date"})
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CouponStatisticsEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_issued", nullable = false)
    @Builder.Default
    private Integer totalIssued = 0;

    @Column(name = "total_used", nullable = false)
    @Builder.Default
    private Integer totalUsed = 0;

    @Column(name = "total_expired", nullable = false)
    @Builder.Default
    private Integer totalExpired = 0;

    @Column(name = "total_discount_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalDiscountAmount = BigDecimal.ZERO;

    // 시간대별 사용 통계 (0-23시)
    @Column(name = "hourly_usage", columnDefinition = "integer[]")
    private Integer[] hourlyUsage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", insertable = false, updatable = false)
    private CouponPolicyEntity policy;
}
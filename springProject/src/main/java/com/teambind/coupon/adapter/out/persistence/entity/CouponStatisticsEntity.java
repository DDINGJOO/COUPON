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

    @Column(nullable = false)
    private Long policyId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer totalIssued = 0;

    @Column(nullable = false)
    private Integer totalUsed = 0;

    @Column(nullable = false)
    private Integer totalExpired = 0;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDiscountAmount = BigDecimal.ZERO;

    // 시간대별 사용 통계 (0-23시)
    @Column(columnDefinition = "integer[]")
    private Integer[] hourlyUsage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", insertable = false, updatable = false)
    private CouponPolicyEntity policy;
}
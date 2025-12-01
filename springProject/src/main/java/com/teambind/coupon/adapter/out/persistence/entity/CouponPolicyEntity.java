package com.teambind.coupon.adapter.out.persistence.entity;

import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 정책 JPA 엔티티
 */
@Entity
@Table(name = "coupon_policies",
        indexes = {
                @Index(name = "idx_coupon_code", columnList = "coupon_code"),
                @Index(name = "idx_valid_period", columnList = "valid_from, valid_until"),
                @Index(name = "idx_is_active", columnList = "is_active")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CouponPolicyEntity extends BaseEntity {

    @Id
    private Long id; // Snowflake ID

    @Column(nullable = false, length = 100)
    private String couponName;

    @Column(unique = true, length = 50)
    private String couponCode; // CODE 타입일 경우 사용

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount; // 퍼센트 할인시 최대 금액

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private ItemApplicableRuleJson applicableRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DistributionType distributionType;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validUntil;

    private Integer maxIssueCount;
    private Integer maxUsagePerUser;

    @Column(nullable = false)
    private boolean isActive = true;

    private Long createdBy;

    @Version
    private Long version; // Optimistic Locking

    /**
     * 적용 가능 상품 규칙 JSON
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemApplicableRuleJson {
        private boolean allItemsApplicable;
        private List<Long> applicableItemIds;
    }
}
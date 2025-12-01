package com.teambind.coupon.adapter.out.persistence.mapper;

import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.DiscountPolicy;
import com.teambind.coupon.domain.model.ItemApplicableRule;
import org.springframework.stereotype.Component;

/**
 * CouponPolicy 도메인 모델과 엔티티 간 변환
 */
@Component
public class CouponPolicyMapper {

    /**
     * 엔티티를 도메인 모델로 변환
     */
    public CouponPolicy toDomain(CouponPolicyEntity entity) {
        if (entity == null) {
            return null;
        }

        DiscountPolicy discountPolicy = new DiscountPolicy(
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMaxDiscountAmount()
        );

        ItemApplicableRule applicableRule = null;
        if (entity.getApplicableRule() != null) {
            applicableRule = new ItemApplicableRule(
                    entity.getApplicableRule().isAllItemsApplicable(),
                    entity.getApplicableRule().getApplicableItemIds()
            );
        }

        return CouponPolicy.builder()
                .id(entity.getId())
                .couponName(entity.getCouponName())
                .couponCode(entity.getCouponCode())
                .description(entity.getDescription())
                .discountPolicy(discountPolicy)
                .applicableRule(applicableRule)
                .distributionType(entity.getDistributionType())
                .validFrom(entity.getValidFrom())
                .validUntil(entity.getValidUntil())
                .maxIssueCount(entity.getMaxIssueCount())
                .maxUsagePerUser(entity.getMaxUsagePerUser())
                .isActive(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }

    /**
     * 도메인 모델을 엔티티로 변환
     */
    public CouponPolicyEntity toEntity(CouponPolicy domain) {
        if (domain == null) {
            return null;
        }

        CouponPolicyEntity.ItemApplicableRuleJson ruleJson = null;
        if (domain.getApplicableRule() != null) {
            ruleJson = CouponPolicyEntity.ItemApplicableRuleJson.builder()
                    .allItemsApplicable(domain.getApplicableRule().isAllItemsApplicable())
                    .applicableItemIds(domain.getApplicableRule().getApplicableItemIds())
                    .build();
        }

        return CouponPolicyEntity.builder()
                .id(domain.getId())
                .couponName(domain.getCouponName())
                .couponCode(domain.getCouponCode())
                .description(domain.getDescription())
                .discountType(domain.getDiscountPolicy().getDiscountType())
                .discountValue(domain.getDiscountPolicy().getDiscountValue())
                .maxDiscountAmount(domain.getDiscountPolicy().getMaxDiscountAmount())
                .applicableRule(ruleJson)
                .distributionType(domain.getDistributionType())
                .validFrom(domain.getValidFrom())
                .validUntil(domain.getValidUntil())
                .maxIssueCount(domain.getMaxIssueCount())
                .maxUsagePerUser(domain.getMaxUsagePerUser())
                .isActive(domain.isActive())
                .createdBy(domain.getCreatedBy())
                .build();
    }

    /**
     * 엔티티 업데이트
     */
    public void updateEntity(CouponPolicyEntity entity, CouponPolicy domain) {
        entity.setCouponName(domain.getCouponName());
        entity.setDescription(domain.getDescription());
        entity.setDiscountType(domain.getDiscountPolicy().getDiscountType());
        entity.setDiscountValue(domain.getDiscountPolicy().getDiscountValue());
        entity.setMaxDiscountAmount(domain.getDiscountPolicy().getMaxDiscountAmount());

        if (domain.getApplicableRule() != null) {
            CouponPolicyEntity.ItemApplicableRuleJson ruleJson =
                    CouponPolicyEntity.ItemApplicableRuleJson.builder()
                            .allItemsApplicable(domain.getApplicableRule().isAllItemsApplicable())
                            .applicableItemIds(domain.getApplicableRule().getApplicableItemIds())
                            .build();
            entity.setApplicableRule(ruleJson);
        }

        entity.setValidFrom(domain.getValidFrom());
        entity.setValidUntil(domain.getValidUntil());
        entity.setMaxIssueCount(domain.getMaxIssueCount());
        entity.setMaxUsagePerUser(domain.getMaxUsagePerUser());
        entity.setActive(domain.isActive());
    }
}
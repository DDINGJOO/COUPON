package com.teambind.coupon.adapter.out.persistence.mapper;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.DiscountPolicy;
import com.teambind.coupon.domain.model.DiscountType;
import org.springframework.stereotype.Component;

/**
 * CouponIssue 도메인 모델과 엔티티 간 변환
 */
@Component
public class CouponIssueMapper {

    /**
     * 엔티티를 도메인 모델로 변환
     */
    public CouponIssue toDomain(CouponIssueEntity entity) {
        if (entity == null) {
            return null;
        }

        DiscountPolicy discountPolicy = null;
        if (entity.getDiscountType() != null && entity.getDiscountValue() != null) {
            DiscountType discountType = DiscountType.valueOf(entity.getDiscountType());
            discountPolicy = DiscountPolicy.builder()
                    .discountType(discountType)
                    .discountValue(entity.getDiscountValue())
                    .minOrderAmount(entity.getMinOrderAmount())
                    .maxDiscountAmount(entity.getMaxDiscountAmount())
                    .build();
        }

        return CouponIssue.builder()
                .id(entity.getId())
                .policyId(entity.getPolicyId())
                .userId(entity.getUserId())
                .status(entity.getStatus())
                .reservationId(entity.getReservationId())
                .orderId(entity.getOrderId())
                .issuedAt(entity.getIssuedAt())
                .reservedAt(entity.getReservedAt())
                .usedAt(entity.getUsedAt())
                .expiredAt(entity.getExpiredAt())
                .actualDiscountAmount(entity.getActualDiscountAmount())
                .couponName(entity.getCouponName())
                .discountPolicy(discountPolicy)
                .build();
    }

    /**
     * 도메인 모델을 엔티티로 변환
     */
    public CouponIssueEntity toEntity(CouponIssue domain) {
        if (domain == null) {
            return null;
        }

        CouponIssueEntity.CouponIssueEntityBuilder builder = CouponIssueEntity.builder()
                .id(domain.getId())
                .policyId(domain.getPolicyId())
                .userId(domain.getUserId())
                .status(domain.getStatus())
                .reservationId(domain.getReservationId())
                .orderId(domain.getOrderId())
                .issuedAt(domain.getIssuedAt())
                .reservedAt(domain.getReservedAt())
                .usedAt(domain.getUsedAt())
                .expiredAt(domain.getExpiredAt())
                .actualDiscountAmount(domain.getActualDiscountAmount())
                .couponName(domain.getCouponName());

        if (domain.getDiscountPolicy() != null) {
            builder.discountType(domain.getDiscountPolicy().getDiscountType().name())
                    .discountValue(domain.getDiscountPolicy().getDiscountValue())
                    .minOrderAmount(domain.getDiscountPolicy().getMinOrderAmount())
                    .maxDiscountAmount(domain.getDiscountPolicy().getMaxDiscountAmount());
        }

        return builder.build();
    }

    /**
     * 엔티티 업데이트
     */
    public void updateEntity(CouponIssueEntity entity, CouponIssue domain) {
        entity.setStatus(domain.getStatus());
        entity.setReservationId(domain.getReservationId());
        entity.setOrderId(domain.getOrderId());
        entity.setReservedAt(domain.getReservedAt());
        entity.setUsedAt(domain.getUsedAt());
        entity.setExpiredAt(domain.getExpiredAt());
        entity.setActualDiscountAmount(domain.getActualDiscountAmount());
    }
}
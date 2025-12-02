package com.teambind.coupon.adapter.out.persistence.mapper;

import com.teambind.coupon.adapter.out.persistence.entity.CouponReservationJpaEntity;
import com.teambind.coupon.domain.model.CouponReservation;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 예약 매퍼
 * 도메인 모델과 JPA 엔티티 간의 변환을 담당합니다.
 */
@Component
public class CouponReservationMapper {

    /**
     * 도메인 모델을 JPA 엔티티로 변환
     */
    public CouponReservationJpaEntity toJpaEntity(CouponReservation reservation) {
        if (reservation == null) {
            return null;
        }

        return CouponReservationJpaEntity.builder()
                .reservationId(reservation.getReservationId())
                .couponId(reservation.getCouponId())
                .userId(reservation.getUserId())
                .orderId(reservation.getOrderId())
                .orderAmount(reservation.getOrderAmount())
                .discountAmount(reservation.getDiscountAmount())
                .reservedAt(reservation.getReservedAt())
                .expiresAt(reservation.getExpiresAt())
                .status(reservation.getStatus())
                .lockValue(reservation.getLockValue())
                .build();
    }

    /**
     * JPA 엔티티를 도메인 모델로 변환
     */
    public CouponReservation toDomain(CouponReservationJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return CouponReservation.builder()
                .reservationId(entity.getReservationId())
                .couponId(entity.getCouponId())
                .userId(entity.getUserId())
                .orderId(entity.getOrderId())
                .orderAmount(entity.getOrderAmount())
                .discountAmount(entity.getDiscountAmount())
                .reservedAt(entity.getReservedAt())
                .expiresAt(entity.getExpiresAt())
                .status(entity.getStatus())
                .lockValue(entity.getLockValue())
                .build();
    }
}
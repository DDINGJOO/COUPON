package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.entity.CouponReservationJpaEntity;
import com.teambind.coupon.domain.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 예약 Repository
 */
@Repository
public interface CouponReservationRepository extends JpaRepository<CouponReservationJpaEntity, String> {

    /**
     * 예약 ID로 조회
     */
    Optional<CouponReservationJpaEntity> findByReservationId(String reservationId);

    /**
     * 만료된 예약 조회
     */
    @Query("SELECT r FROM CouponReservationJpaEntity r " +
           "WHERE r.status = :status AND r.expiresAt < :now")
    List<CouponReservationJpaEntity> findExpiredReservations(
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now);

    /**
     * 사용자의 예약 목록 조회
     */
    List<CouponReservationJpaEntity> findByUserIdAndStatus(Long userId, ReservationStatus status);

    /**
     * 쿠폰 ID로 예약 조회
     */
    Optional<CouponReservationJpaEntity> findByCouponIdAndStatus(Long couponId, ReservationStatus status);
}
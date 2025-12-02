package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.domain.model.CouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 발급된 쿠폰 Repository
 */
@Repository
public interface CouponIssueRepository extends JpaRepository<CouponIssueEntity, Long> {

    /**
     * 사용자의 특정 정책 쿠폰 발급 횟수 조회
     */
    int countByUserIdAndPolicyId(Long userId, Long policyId);

    /**
     * 사용자의 사용 가능한 쿠폰 조회
     */
    @Query("SELECT ci FROM CouponIssueEntity ci " +
           "WHERE ci.userId = :userId " +
           "AND ci.status = 'ISSUED' " +
           "AND ci.expiresAt > :now " +
           "ORDER BY ci.expiresAt ASC")
    List<CouponIssueEntity> findUsableCoupons(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    /**
     * 사용자의 쿠폰 목록 조회 (페이징)
     */
    Page<CouponIssueEntity> findByUserIdOrderByIssuedAtDesc(Long userId, Pageable pageable);

    /**
     * 예약 ID로 쿠폰 조회 (비관적 락)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CouponIssueEntity ci WHERE ci.reservationId = :reservationId")
    Optional<CouponIssueEntity> findByReservationIdWithLock(@Param("reservationId") String reservationId);

    /**
     * 타임아웃된 예약 쿠폰 조회
     */
    @Query("SELECT ci FROM CouponIssueEntity ci " +
           "WHERE ci.status = 'RESERVED' " +
           "AND ci.reservedAt < :timeoutTime")
    List<CouponIssueEntity> findTimeoutReservations(@Param("timeoutTime") LocalDateTime timeoutTime);

    /**
     * 만료 대상 쿠폰 조회
     */
    @Query("SELECT ci FROM CouponIssueEntity ci " +
           "WHERE ci.status = 'ISSUED' " +
           "AND ci.expiresAt < :now")
    List<CouponIssueEntity> findExpiredCoupons(@Param("now") LocalDateTime now);

    /**
     * 만료 대상 쿠폰 페이징 조회 (배치 처리용)
     */
    @Query("SELECT ci FROM CouponIssueEntity ci " +
           "WHERE ci.status IN ('ISSUED', 'RESERVED') " +
           "AND ci.expiresAt < :now")
    Page<CouponIssueEntity> findExpiredCouponsWithPaging(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * 쿠폰 상태를 만료로 일괄 업데이트
     */
    @Modifying
    @Query("UPDATE CouponIssueEntity ci " +
           "SET ci.status = 'EXPIRED', ci.expiredAt = :now " +
           "WHERE ci.id IN :ids " +
           "AND ci.status IN ('ISSUED', 'RESERVED')")
    int updateToExpiredBatch(
            @Param("ids") List<Long> ids,
            @Param("now") LocalDateTime now
    );

    /**
     * 쿠폰 ID와 사용자 ID로 조회
     */
    @Query("SELECT ci FROM CouponIssueEntity ci " +
           "WHERE ci.id = :couponId " +
           "AND ci.userId = :userId")
    Optional<CouponIssueEntity> findByIdAndUserId(
            @Param("couponId") Long couponId,
            @Param("userId") Long userId
    );

    /**
     * 쿠폰 ID와 사용자 ID로 조회 (비관적 락)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CouponIssueEntity ci " +
           "WHERE ci.id = :couponId " +
           "AND ci.userId = :userId")
    Optional<CouponIssueEntity> findByIdAndUserIdWithLock(
            @Param("couponId") Long couponId,
            @Param("userId") Long userId
    );

    /**
     * 쿠폰 상태 일괄 업데이트
     */
    @Modifying
    @Query("UPDATE CouponIssueEntity ci " +
           "SET ci.status = :newStatus, ci.expiredAt = :expiredAt " +
           "WHERE ci.id IN :ids")
    int updateStatusBatch(
            @Param("ids") List<Long> ids,
            @Param("newStatus") CouponStatus newStatus,
            @Param("expiredAt") LocalDateTime expiredAt
    );

    /**
     * 정책별 발급 통계
     */
    @Query("SELECT ci.policyId, COUNT(ci) " +
           "FROM CouponIssueEntity ci " +
           "WHERE ci.policyId IN :policyIds " +
           "GROUP BY ci.policyId")
    List<Object[]> countByPolicyIds(@Param("policyIds") List<Long> policyIds);

    /**
     * 사용자의 특정 상태 쿠폰 개수
     */
    int countByUserIdAndStatus(Long userId, CouponStatus status);

    /**
     * 기간별 사용 통계
     */
    @Query("SELECT DATE(ci.usedAt), COUNT(ci), SUM(ci.actualDiscountAmount) " +
           "FROM CouponIssueEntity ci " +
           "WHERE ci.status = 'USED' " +
           "AND ci.usedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(ci.usedAt) " +
           "ORDER BY DATE(ci.usedAt)")
    List<Object[]> getDailyUsageStatistics(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 사용자 ID와 상태로 쿠폰 목록 조회
     */
    List<CouponIssueEntity> findByUserIdAndStatus(Long userId, CouponStatus status);
}
package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.domain.model.DistributionType;
import jakarta.persistence.LockModeType;
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
 * 쿠폰 정책 Repository
 */
@Repository
public interface CouponPolicyRepository extends JpaRepository<CouponPolicyEntity, Long> {

    /**
     * 쿠폰 코드로 활성화된 정책 조회
     */
    Optional<CouponPolicyEntity> findByCouponCodeAndIsActiveTrue(String couponCode);

    /**
     * 쿠폰 코드로 정책 조회 (비관적 락)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cp FROM CouponPolicyEntity cp WHERE cp.couponCode = :couponCode AND cp.isActive = true")
    Optional<CouponPolicyEntity> findByCouponCodeWithLock(@Param("couponCode") String couponCode);

    /**
     * 활성화된 정책 목록 조회
     */
    List<CouponPolicyEntity> findByIsActiveTrueOrderByCreatedAtDesc();

    /**
     * 유효기간 내 활성화된 정책 조회
     */
    @Query("SELECT cp FROM CouponPolicyEntity cp " +
           "WHERE cp.isActive = true " +
           "AND cp.validFrom <= :now " +
           "AND cp.validUntil >= :now " +
           "ORDER BY cp.createdAt DESC")
    List<CouponPolicyEntity> findActiveAndValidPolicies(@Param("now") LocalDateTime now);

    /**
     * 만료 임박 정책 조회 (N일 이내)
     */
    @Query("SELECT cp FROM CouponPolicyEntity cp " +
           "WHERE cp.isActive = true " +
           "AND cp.validUntil BETWEEN :now AND :expiryDate " +
           "ORDER BY cp.validUntil ASC")
    List<CouponPolicyEntity> findExpiringSoonPolicies(
            @Param("now") LocalDateTime now,
            @Param("expiryDate") LocalDateTime expiryDate
    );

    /**
     * 배포 타입별 정책 조회
     */
    List<CouponPolicyEntity> findByDistributionTypeAndIsActiveTrue(DistributionType distributionType);

    /**
     * 재고 차감 (원자적 업데이트)
     */
    @Modifying
    @Query("UPDATE CouponPolicyEntity cp " +
           "SET cp.maxIssueCount = cp.maxIssueCount - 1 " +
           "WHERE cp.id = :policyId " +
           "AND cp.maxIssueCount > 0")
    int decrementStock(@Param("policyId") Long policyId);

    /**
     * 배치 재고 차감
     */
    @Modifying
    @Query("UPDATE CouponPolicyEntity cp " +
           "SET cp.currentIssueCount = cp.currentIssueCount + :quantity " +
           "WHERE cp.id = :policyId " +
           "AND cp.currentIssueCount + :quantity <= cp.maxIssueCount")
    int decrementStockBatch(@Param("policyId") Long policyId, @Param("quantity") int quantity);

    /**
     * 재고 복구
     */
    @Modifying
    @Query("UPDATE CouponPolicyEntity cp " +
           "SET cp.currentIssueCount = GREATEST(0, cp.currentIssueCount - :quantity) " +
           "WHERE cp.id = :policyId")
    int incrementStock(@Param("policyId") Long policyId, @Param("quantity") int quantity);

    /**
     * 비관적 락으로 정책 조회
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cp FROM CouponPolicyEntity cp WHERE cp.id = :policyId")
    Optional<CouponPolicyEntity> findByIdWithPessimisticLock(@Param("policyId") Long policyId);

    /**
     * 정책 존재 여부 확인
     */
    boolean existsByCouponCode(String couponCode);
}
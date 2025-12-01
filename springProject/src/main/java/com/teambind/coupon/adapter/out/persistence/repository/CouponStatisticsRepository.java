package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.entity.CouponStatisticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 통계 Repository
 */
@Repository
public interface CouponStatisticsRepository extends JpaRepository<CouponStatisticsEntity, Long> {

    /**
     * 특정 날짜의 정책별 통계 조회
     */
    Optional<CouponStatisticsEntity> findByPolicyIdAndDate(Long policyId, LocalDate date);

    /**
     * 기간별 정책 통계 조회
     */
    @Query("SELECT cs FROM CouponStatisticsEntity cs " +
           "WHERE cs.policyId = :policyId " +
           "AND cs.date BETWEEN :startDate AND :endDate " +
           "ORDER BY cs.date")
    List<CouponStatisticsEntity> findByPolicyIdAndDateRange(
            @Param("policyId") Long policyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 전체 통계 집계
     */
    @Query("SELECT SUM(cs.totalIssued), SUM(cs.totalUsed), " +
           "SUM(cs.totalExpired), SUM(cs.totalDiscountAmount) " +
           "FROM CouponStatisticsEntity cs " +
           "WHERE cs.date BETWEEN :startDate AND :endDate")
    List<Object[]> getAggregatedStatistics(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 정책별 누적 통계
     */
    @Query("SELECT cs.policyId, " +
           "SUM(cs.totalIssued), SUM(cs.totalUsed), " +
           "SUM(cs.totalExpired), SUM(cs.totalDiscountAmount) " +
           "FROM CouponStatisticsEntity cs " +
           "WHERE cs.policyId IN :policyIds " +
           "GROUP BY cs.policyId")
    List<Object[]> getCumulativeStatisticsByPolicies(@Param("policyIds") List<Long> policyIds);

    /**
     * 일별 전체 통계
     */
    @Query("SELECT cs.date, " +
           "SUM(cs.totalIssued), SUM(cs.totalUsed), " +
           "SUM(cs.totalExpired), SUM(cs.totalDiscountAmount) " +
           "FROM CouponStatisticsEntity cs " +
           "WHERE cs.date BETWEEN :startDate AND :endDate " +
           "GROUP BY cs.date " +
           "ORDER BY cs.date")
    List<Object[]> getDailyStatistics(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
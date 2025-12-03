package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.projection.CouponIssueProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 쿠폰 조회 전용 Repository
 * PostgreSQL Native Query를 사용한 커서 기반 페이지네이션 및 필터링
 */
@Repository
public interface CouponIssueQueryRepository extends JpaRepository<com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity, Long> {

    /**
     * 유저의 쿠폰 목록을 커서 기반 페이지네이션으로 조회
     *
     * @param userId 유저 ID
     * @param status 쿠폰 상태 필터 (ISSUED, USED, EXPIRED, RESERVED)
     * @param productIds 상품 ID 배열 (PostgreSQL 배열 형식: {1,2,3})
     * @param cursor 커서 (마지막으로 조회한 쿠폰 ID)
     * @param limit 조회 개수
     * @return 쿠폰 목록
     */
    @Query(value = """
        WITH filtered_coupons AS (
            SELECT
                ci.id AS coupon_issue_id,
                ci.user_id,
                ci.policy_id,
                ci.status,
                ci.issued_at,
                ci.expires_at,
                ci.used_at,
                ci.reserved_at,
                ci.reservation_id,
                ci.actual_discount_amount,
                cp.coupon_name,
                cp.coupon_code,
                cp.description,
                cp.discount_type,
                cp.discount_value,
                cp.minimum_order_amount,
                cp.max_discount_amount,
                cp.applicable_rule::text AS applicable_rule,
                cp.distribution_type,
                CASE
                    WHEN ci.status = 'ISSUED' AND ci.expires_at > CURRENT_TIMESTAMP THEN true
                    ELSE false
                END AS is_available
            FROM coupon_issues ci
            INNER JOIN coupon_policies cp ON ci.policy_id = cp.id
            WHERE ci.user_id = :userId
                AND (:cursor IS NULL OR ci.id < :cursor)
                AND (
                    :status IS NULL
                    OR ci.status = :status
                    OR (:status = 'AVAILABLE' AND ci.status = 'ISSUED' AND ci.expires_at > CURRENT_TIMESTAMP)
                )
                AND (
                    :productIds IS NULL
                    OR cp.applicable_rule IS NULL
                    OR cp.applicable_rule->'applicableItemIds' IS NULL
                    OR EXISTS (
                        SELECT 1 FROM jsonb_array_elements_text(cp.applicable_rule->'applicableItemIds') AS item
                        WHERE item::bigint = ANY(CAST(:productIds AS bigint[]))
                    )
                )
            ORDER BY
                CASE WHEN :status = 'AVAILABLE' THEN ci.expires_at END ASC,
                ci.id DESC
            LIMIT :limit
        )
        SELECT * FROM filtered_coupons
        """, nativeQuery = true)
    List<CouponIssueProjection> findUserCouponsWithCursor(
        @Param("userId") Long userId,
        @Param("status") String status,
        @Param("productIds") String productIds,
        @Param("cursor") Long cursor,
        @Param("limit") int limit
    );

    /**
     * 유저의 특정 상태 쿠폰 총 개수 조회
     *
     * @param userId 유저 ID
     * @param status 쿠폰 상태
     * @param productIds 상품 ID 배열
     * @return 쿠폰 개수
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM coupon_issues ci
        INNER JOIN coupon_policies cp ON ci.policy_id = cp.id
        WHERE ci.user_id = :userId
            AND (
                :status IS NULL
                OR ci.status = :status
                OR (:status = 'AVAILABLE' AND ci.status = 'ISSUED' AND ci.expires_at > CURRENT_TIMESTAMP)
            )
            AND (
                :productIds IS NULL
                OR cp.applicable_rule IS NULL
                OR cp.applicable_rule->'applicableItemIds' IS NULL
                OR EXISTS (
                    SELECT 1 FROM jsonb_array_elements_text(cp.applicable_rule->'applicableItemIds') AS item
                    WHERE item::bigint = ANY(CAST(:productIds AS bigint[]))
                )
            )
        """, nativeQuery = true)
    Long countUserCoupons(
        @Param("userId") Long userId,
        @Param("status") String status,
        @Param("productIds") String productIds
    );

    /**
     * 곧 만료될 쿠폰 조회 (N일 이내)
     *
     * @param userId 유저 ID
     * @param daysUntilExpiry 만료까지 남은 일수
     * @param limit 조회 개수
     * @return 쿠폰 목록
     */
    @Query(value = """
        SELECT
            ci.id AS coupon_issue_id,
            ci.user_id,
            ci.policy_id,
            ci.status,
            ci.issued_at,
            ci.expires_at,
            ci.used_at,
            ci.reserved_at,
            ci.reservation_id,
            ci.actual_discount_amount,
            cp.coupon_name,
            cp.coupon_code,
            cp.description,
            cp.discount_type,
            cp.discount_value,
            cp.minimum_order_amount,
            cp.max_discount_amount,
            cp.applicable_rule::text AS applicable_rule,
            cp.distribution_type,
            true AS is_available
        FROM coupon_issues ci
        INNER JOIN coupon_policies cp ON ci.policy_id = cp.id
        WHERE ci.user_id = :userId
            AND ci.status = 'ISSUED'
            AND ci.expires_at > CURRENT_TIMESTAMP
            AND ci.expires_at <= CURRENT_TIMESTAMP + CAST(:daysUntilExpiry || ' days' AS INTERVAL)
        ORDER BY ci.expires_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<CouponIssueProjection> findExpiringCoupons(
        @Param("userId") Long userId,
        @Param("daysUntilExpiry") int daysUntilExpiry,
        @Param("limit") int limit
    );
}
package com.teambind.coupon.application.port.out;

import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 쿠폰 발급 조회 Output Port
 */
public interface LoadCouponIssuePort {

    /**
     * ID로 발급된 쿠폰 조회
     */
    Optional<CouponIssue> loadById(Long issueId);

    /**
     * 쿠폰 ID와 사용자 ID로 조회
     */
    Optional<CouponIssue> loadByIdAndUserId(Long issueId, Long userId);

    /**
     * 쿠폰 ID와 사용자 ID로 조회 (비관적 락)
     */
    Optional<CouponIssue> loadByIdAndUserIdWithLock(Long issueId, Long userId);

    /**
     * 예약 ID로 쿠폰 조회 (비관적 락)
     */
    Optional<CouponIssue> loadByReservationIdWithLock(String reservationId);

    /**
     * 사용자의 특정 정책 발급 횟수 조회
     */
    int countUserIssuance(Long userId, Long policyId);

    /**
     * 사용자의 사용 가능한 쿠폰 조회
     */
    List<CouponIssue> loadUsableCoupons(Long userId);

    /**
     * 타임아웃된 예약 쿠폰 조회
     */
    List<CouponIssue> loadTimeoutReservations(LocalDateTime timeoutTime);

    /**
     * 만료 대상 쿠폰 조회
     */
    List<CouponIssue> loadExpiredCoupons();

    /**
     * 사용자의 특정 상태 쿠폰 개수
     */
    int countByUserIdAndStatus(Long userId, CouponStatus status);

    /**
     * 사용자의 사용 가능한 쿠폰 목록 조회
     */
    List<CouponIssue> findAvailableCouponsByUserId(Long userId);

    /**
     * ID로 쿠폰 조회
     */
    Optional<CouponIssue> findById(Long couponId);
}
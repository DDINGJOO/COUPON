package com.teambind.coupon.application.port.out;

import com.teambind.coupon.domain.model.CouponPolicy;

import java.util.Optional;

/**
 * 쿠폰 정책 조회 Output Port
 */
public interface LoadCouponPolicyPort {

    /**
     * ID로 쿠폰 정책 조회
     */
    Optional<CouponPolicy> loadById(Long policyId);

    /**
     * 쿠폰 코드로 활성화된 정책 조회
     */
    Optional<CouponPolicy> loadByCodeAndActive(String couponCode);

    /**
     * 쿠폰 코드로 정책 조회 (비관적 락)
     */
    Optional<CouponPolicy> loadByCodeWithLock(String couponCode);

    /**
     * 쿠폰 코드 존재 여부 확인
     */
    boolean existsByCode(String couponCode);

    /**
     * 전체 쿠폰 정책 수 조회
     */
    default int countAll() {
        return 0; // TODO: Repository 구현 필요
    }
}
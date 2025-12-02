package com.teambind.coupon.application.port.out;

import com.teambind.coupon.domain.model.CouponPolicy;

import java.util.List;
import java.util.Map;
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

    /**
     * 여러 ID로 쿠폰 정책들을 배치 조회
     * N+1 쿼리 문제 해결을 위한 메서드
     *
     * @param policyIds 조회할 정책 ID 목록
     * @return 정책 ID를 키로 하는 Map
     */
    Map<Long, CouponPolicy> loadByIds(List<Long> policyIds);
}
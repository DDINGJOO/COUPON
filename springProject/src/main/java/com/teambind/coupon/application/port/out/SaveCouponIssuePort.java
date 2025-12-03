package com.teambind.coupon.application.port.out;

import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 발급 저장 Output Port
 */
public interface SaveCouponIssuePort {

    /**
     * 쿠폰 발급 저장
     */
    CouponIssue save(CouponIssue issue);

    /**
     * 쿠폰 발급 업데이트
     */
    void update(CouponIssue issue);

    /**
     * 쿠폰 상태 일괄 업데이트
     */
    int updateStatusBatch(List<Long> issueIds, CouponStatus newStatus, LocalDateTime expiredAt);

    /**
     * 여러 쿠폰 발급 저장
     */
    List<CouponIssue> saveAll(List<CouponIssue> issues);

    /**
     * 여러 쿠폰 업데이트 (배치 처리)
     */
    void updateAll(List<CouponIssue> issues);
}
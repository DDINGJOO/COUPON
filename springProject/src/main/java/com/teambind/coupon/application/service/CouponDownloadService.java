package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DownloadCouponCommand;
import com.teambind.coupon.application.port.in.DownloadCouponUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DistributionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 쿠폰 다운로드 서비스
 * CODE 타입 쿠폰 다운로드 비즈니스 로직 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponDownloadService implements DownloadCouponUseCase {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponPolicyPort saveCouponPolicyPort;
    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;

    @Override
    @Transactional
    public CouponIssue downloadCoupon(DownloadCouponCommand command) {
        log.info("쿠폰 다운로드 시작 - userId: {}, couponCode: {}",
                command.getUserId(), command.getCouponCode());

        // 1. 쿠폰 정책 조회 (비관적 락)
        CouponPolicy policy = loadCouponPolicyPort.loadByCodeWithLock(command.getCouponCode())
                .orElseThrow(() -> new CouponDomainException.CouponNotFound(0L));

        // 2. 쿠폰 정책 검증
        validateCouponPolicy(policy);

        // 3. 사용자 발급 제한 확인
        validateUserLimit(command.getUserId(), policy);

        // 4. 재고 차감 시도
        boolean stockDecremented = saveCouponPolicyPort.decrementStock(policy.getId());
        if (!stockDecremented) {
            throw new CouponDomainException.CouponStockExhausted(command.getCouponCode());
        }

        // 5. 쿠폰 발급
        CouponIssue couponIssue = createCouponIssue(command.getUserId(), policy);
        CouponIssue savedIssue = saveCouponIssuePort.save(couponIssue);

        log.info("쿠폰 다운로드 완료 - issueId: {}, userId: {}, couponCode: {}",
                savedIssue.getId(), command.getUserId(), command.getCouponCode());

        return savedIssue;
    }

    /**
     * 쿠폰 정책 검증
     */
    private void validateCouponPolicy(CouponPolicy policy) {
        // CODE 타입 확인
        if (policy.getDistributionType() != DistributionType.CODE) {
            throw new CouponDomainException("CODE 타입 쿠폰만 다운로드 가능합니다");
        }

        // 활성화 상태 확인
        if (!policy.isActive()) {
            throw new CouponDomainException("비활성화된 쿠폰입니다");
        }

        // 발급 가능 여부 확인
        if (!policy.isIssuable()) {
            if (policy.isExpired()) {
                throw new CouponDomainException.CouponExpired(policy.getId());
            }
            if (policy.isNotStarted()) {
                throw new CouponDomainException("아직 발급 기간이 시작되지 않았습니다");
            }
            throw new CouponDomainException.CouponStockExhausted(policy.getCouponCode());
        }
    }

    /**
     * 사용자 발급 제한 확인
     */
    private void validateUserLimit(Long userId, CouponPolicy policy) {
        if (policy.hasUserLimit()) {
            int userIssuanceCount = loadCouponIssuePort.countUserIssuance(userId, policy.getId());
            if (userIssuanceCount >= policy.getMaxUsagePerUser()) {
                throw new CouponDomainException.UserCouponLimitExceeded(userId, policy.getCouponCode());
            }
        }
    }

    /**
     * 쿠폰 발급 엔티티 생성
     */
    private CouponIssue createCouponIssue(Long userId, CouponPolicy policy) {
        return CouponIssue.builder()
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }
}
package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DirectIssueCouponCommand;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.common.annotation.UseCase;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DistributionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 쿠폰 직접 발급 서비스
 * 관리자가 사용자에게 직접 쿠폰을 발급하는 기능
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
@Transactional
public class CouponDirectIssueService implements DirectIssueCouponUseCase {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    public DirectIssueResult issueCouponDirectly(DirectIssueCouponCommand command) {
        log.info("쿠폰 직접 발급 시작: policyId={}, userCount={}",
                command.getPolicyId(), command.getUserIds().size());

        // 1. 쿠폰 정책 조회 및 검증
        CouponPolicy policy = loadCouponPolicyPort.loadById(command.getPolicyId())
                .orElseThrow(() -> new CouponDomainException.CouponNotFound(command.getPolicyId()));

        // 2. DIRECT 타입 검증
        if (policy.getDistributionType() != DistributionType.DIRECT) {
            throw new CouponDomainException.InvalidRequest(
                    "직접 발급은 DIRECT 타입 쿠폰만 가능합니다: " + policy.getDistributionType());
        }

        // 3. 발급 가능 상태 검증
        if (!policy.isIssuable()) {
            throw new CouponDomainException.CouponNotIssuable(
                    "발급 불가능한 쿠폰입니다: " + policy.getCouponName());
        }

        // 4. 사용자별 발급 처리
        List<Long> successUserIds = new ArrayList<>();
        List<Long> failedUserIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Long userId : command.getUserIds()) {
            try {
                issueToUser(policy, userId, command.getReason());
                successUserIds.add(userId);
            } catch (Exception e) {
                log.error("사용자 {} 쿠폰 발급 실패: {}", userId, e.getMessage());
                failedUserIds.add(userId);
                errors.add(String.format("User %d: %s", userId, e.getMessage()));
            }
        }

        // 5. 결과 반환
        if (failedUserIds.isEmpty()) {
            log.info("쿠폰 직접 발급 완료: 모든 사용자 성공 ({}명)", successUserIds.size());
            return DirectIssueResult.success(command.getPolicyId(), successUserIds.size());
        } else {
            log.warn("쿠폰 직접 발급 부분 성공: 성공={}, 실패={}",
                    successUserIds.size(), failedUserIds.size());
            return DirectIssueResult.partial(
                    command.getPolicyId(),
                    command.getUserIds().size(),
                    successUserIds.size(),
                    failedUserIds,
                    errors
            );
        }
    }

    private void issueToUser(CouponPolicy policy, Long userId, String reason) {
        // 사용자별 발급 제한 확인
        if (policy.getMaxUsagePerUser() != null) {
            int issuedCount = loadCouponIssuePort.countUserIssuance(userId, policy.getId());
            if (issuedCount >= policy.getMaxUsagePerUser()) {
                throw new CouponDomainException.ExceedMaxUsage(
                        String.format("사용자 %d는 이미 최대 수량을 발급받았습니다", userId));
            }
        }

        // 쿠폰 발급
        CouponIssue couponIssue = CouponIssue.builder()
                .id(idGenerator.nextId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();

        saveCouponIssuePort.save(couponIssue);

        log.debug("쿠폰 발급 성공: userId={}, policyId={}, reason={}",
                userId, policy.getId(), reason);
    }
}
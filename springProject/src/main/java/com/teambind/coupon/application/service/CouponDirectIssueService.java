package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DirectIssueCouponCommand;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.common.annotation.DistributedLock;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 쿠폰 직접 발급 서비스
 * 관리자가 특정 사용자에게 DIRECT 타입 쿠폰 직접 발급
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponDirectIssueService implements DirectIssueCouponUseCase {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponPolicyPort saveCouponPolicyPort;
    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;

    @Override
    @Transactional
    @DistributedLock(key = "#command.couponPolicyId", prefix = "direct:issue", waitTime = 10, leaseTime = 30)
    public DirectIssueResult directIssue(DirectIssueCouponCommand command) {
        log.info("직접 쿠폰 발급 시작 - policyId: {}, userCount: {}, issuedBy: {}",
                command.getCouponPolicyId(), command.getUserIds().size(), command.getIssuedBy());

        // 1. 쿠폰 정책 조회 및 검증
        CouponPolicy policy = loadCouponPolicyPort.loadById(command.getCouponPolicyId())
                .orElseThrow(() -> new CouponDomainException.CouponNotFound(command.getCouponPolicyId()));

        validateDirectIssuePolicy(policy, command);

        // 2. 재고 확인
        if (!checkStock(policy, command.getTotalQuantity())) {
            int available = policy.getMaxIssueCount() != null ?
                    policy.getMaxIssueCount() - policy.getCurrentIssueCount().get() : Integer.MAX_VALUE;
            log.error("재고 부족 - policyId: {}, required: {}, available: {}",
                    policy.getId(), command.getTotalQuantity(), available);
            return createFailureResult(command, "재고 부족");
        }

        // 3. 사용자별 발급
        List<CouponIssue> issuedCoupons = new ArrayList<>();
        List<IssueFailure> failures = new ArrayList<>();

        for (Long userId : command.getUserIds()) {
            try {
                List<CouponIssue> userCoupons = issueToUser(
                        userId, policy, command.getQuantityPerUser(),
                        command.getIssuedBy(), command.getReason(), command.isSkipValidation()
                );
                issuedCoupons.addAll(userCoupons);
            } catch (Exception e) {
                log.error("사용자 쿠폰 발급 실패 - userId: {}, error: {}", userId, e.getMessage());
                failures.add(new IssueFailure(userId, e.getMessage(), "ISSUE_FAILED"));
            }
        }

        // 4. 재고 차감
        if (!issuedCoupons.isEmpty()) {
            // 발급된 수량만큼 재고 차감 (decrementStock은 한 개씩 차감)
            for (int i = 0; i < issuedCoupons.size(); i++) {
                boolean stockUpdated = saveCouponPolicyPort.decrementStock(policy.getId());
                if (!stockUpdated) {
                    log.warn("재고 차감 실패 - 롤백 필요");
                    throw new CouponDomainException("재고 업데이트 실패");
                }
            }
        }

        // 5. 결과 생성
        DirectIssueResult result = new DirectIssueResult(
                command.getTotalQuantity(),
                issuedCoupons.size(),
                failures.size() * command.getQuantityPerUser(),
                issuedCoupons,
                failures
        );

        log.info("직접 쿠폰 발급 완료 - 성공: {}/{}, 실패: {}",
                result.successCount(), result.requestedCount(), result.failedCount());

        // 6. 발급 이벤트 발행 (필요시)
        publishDirectIssueEvent(result, command);

        return result;
    }

    /**
     * 정책 검증
     */
    private void validateDirectIssuePolicy(CouponPolicy policy, DirectIssueCouponCommand command) {
        // DIRECT 타입 확인
        if (policy.getDistributionType() != DistributionType.DIRECT) {
            throw new CouponDomainException(
                    String.format("DIRECT 타입이 아닙니다. type: %s", policy.getDistributionType())
            );
        }

        // 활성화 상태 확인
        if (!policy.isActive()) {
            throw new CouponDomainException("비활성화된 쿠폰 정책입니다");
        }

        // 검증 스킵이 아닌 경우 발급 가능 여부 확인
        if (!command.isSkipValidation() && !policy.isIssuable()) {
            if (policy.isExpired()) {
                throw new CouponDomainException.CouponExpired(policy.getId());
            }
            if (policy.isNotStarted()) {
                throw new CouponDomainException("아직 발급 기간이 시작되지 않았습니다");
            }
            throw new CouponDomainException("발급 불가능한 상태입니다");
        }
    }

    /**
     * 재고 확인
     */
    private boolean checkStock(CouponPolicy policy, int requiredQuantity) {
        if (policy.getMaxIssueCount() == null) {
            // 무제한 재고
            return true;
        }
        int remainingStock = policy.getMaxIssueCount() - policy.getCurrentIssueCount().get();
        return remainingStock >= requiredQuantity;
    }

    /**
     * 사용자에게 쿠폰 발급
     */
    private List<CouponIssue> issueToUser(Long userId, CouponPolicy policy, int quantity,
                                           String issuedBy, String reason, boolean skipValidation) {
        List<CouponIssue> issuedCoupons = new ArrayList<>();

        // 사용자 발급 제한 확인 (검증 스킵이 아닌 경우)
        if (!skipValidation && policy.hasUserLimit()) {
            int currentCount = loadCouponIssuePort.countUserIssuance(userId, policy.getId());
            int availableCount = Math.max(0, policy.getMaxUsagePerUser() - currentCount);

            if (availableCount == 0) {
                throw new CouponDomainException.UserCouponLimitExceeded(userId, policy.getCouponCode());
            }

            // 요청 수량이 가능한 수량보다 많으면 조정
            quantity = Math.min(quantity, availableCount);
        }

        // 쿠폰 발급
        for (int i = 0; i < quantity; i++) {
            CouponIssue couponIssue = createDirectIssueCoupon(userId, policy, issuedBy, reason);
            CouponIssue saved = saveCouponIssuePort.save(couponIssue);
            issuedCoupons.add(saved);

            log.debug("쿠폰 발급 - userId: {}, couponId: {}, issuedBy: {}",
                    userId, saved.getId(), issuedBy);
        }

        return issuedCoupons;
    }

    /**
     * 직접 발급 쿠폰 생성
     */
    private CouponIssue createDirectIssueCoupon(Long userId, CouponPolicy policy,
                                                 String issuedBy, String reason) {
        return CouponIssue.builder()
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 실패 결과 생성
     */
    private DirectIssueResult createFailureResult(DirectIssueCouponCommand command, String reason) {
        List<IssueFailure> failures = command.getUserIds().stream()
                .map(userId -> new IssueFailure(userId, reason, "STOCK_EXHAUSTED"))
                .collect(Collectors.toList());

        return new DirectIssueResult(
                command.getTotalQuantity(),
                0,
                command.getTotalQuantity(),
                List.of(),
                failures
        );
    }

    /**
     * 직접 발급 이벤트 발행
     */
    private void publishDirectIssueEvent(DirectIssueResult result, DirectIssueCouponCommand command) {
        // TODO: 이벤트 발행 구현
        if (result.isFullySuccessful()) {
            log.info("직접 발급 성공 이벤트 - count: {}, issuedBy: {}",
                    result.successCount(), command.getIssuedBy());
        } else if (result.isPartiallySuccessful()) {
            log.warn("직접 발급 부분 성공 이벤트 - success: {}, failed: {}",
                    result.successCount(), result.failedCount());
        }
    }
}
package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 동시성이 보장된 쿠폰 발급 서비스
 * Redis 기반 재고 관리와 분산 락을 활용한 안전한 쿠폰 발급
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrentCouponIssueService {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final CouponStockService stockService;
    private final RedisDistributedLock distributedLock;
    private final SnowflakeIdGenerator idGenerator;

    private static final String ISSUE_LOCK_PREFIX = "coupon:issue:lock:";
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 쿠폰 발급 (동시성 안전)
     * Redis 재고 관리와 분산 락을 통한 안전한 발급
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = MAX_RETRY_COUNT,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public CouponIssue issueCoupon(Long policyId, Long userId, String issuedBy) {
        log.info("쿠폰 발급 시작 - policyId: {}, userId: {}", policyId, userId);

        // 1. 정책 조회 및 검증
        CouponPolicy policy = loadCouponPolicyPort.loadById(policyId)
                .orElseThrow(() -> new CouponDomainException.CouponNotFound(policyId));

        validatePolicy(policy);

        // 2. Redis에서 재고 체크 및 차감 (원자적 연산)
        boolean stockDecremented = false;
        try {
            // Redis에 재고가 없으면 초기화
            initializeStockIfNeeded(policy);

            // 재고 차감 시도
            if (!stockService.decrementStock(policyId, 1)) {
                throw new CouponDomainException.StockExhausted(policy.getCouponCode());
            }
            stockDecremented = true;

            // 3. 사용자별 발급 제한 체크 (Redis 카운터)
            if (policy.hasUserLimit()) {
                boolean userCountIncremented = stockService.incrementUserIssueCount(
                    userId, policyId, policy.getMaxUsagePerUser()
                );

                if (!userCountIncremented) {
                    throw new CouponDomainException.UserCouponLimitExceeded(userId, policy.getCouponCode());
                }
            }

            // 4. 쿠폰 발급 (DB 저장)
            CouponIssue couponIssue = createCouponIssue(userId, policy, issuedBy);
            CouponIssue savedCoupon = saveCouponIssuePort.save(couponIssue);

            log.info("쿠폰 발급 성공 - couponId: {}, userId: {}, policyId: {}",
                     savedCoupon.getId(), userId, policyId);

            return savedCoupon;

        } catch (Exception e) {
            // 재고 차감했으면 롤백
            if (stockDecremented) {
                stockService.incrementStock(policyId, 1);
            }
            throw e;
        }
    }

    /**
     * 배치 쿠폰 발급 (여러 사용자에게 동시 발급)
     * 분산 락을 사용하여 전체 프로세스를 보호
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BatchIssueResult batchIssueCoupons(Long policyId, java.util.List<Long> userIds, String issuedBy) {
        String lockKey = ISSUE_LOCK_PREFIX + "batch:" + policyId;
        String lockValue = UUID.randomUUID().toString();

        // 배치 발급은 분산 락으로 보호
        if (!distributedLock.tryLock(lockKey, lockValue, Duration.ofSeconds(30))) {
            log.error("배치 발급 락 획득 실패 - policyId: {}", policyId);
            throw new CouponDomainException("다른 배치 발급이 진행 중입니다");
        }

        try {
            return processBatchIssue(policyId, userIds, issuedBy);
        } finally {
            distributedLock.unlock(lockKey, lockValue);
        }
    }

    /**
     * 배치 발급 처리
     */
    private BatchIssueResult processBatchIssue(Long policyId, java.util.List<Long> userIds, String issuedBy) {
        log.info("배치 쿠폰 발급 시작 - policyId: {}, userCount: {}", policyId, userIds.size());

        CouponPolicy policy = loadCouponPolicyPort.loadById(policyId)
                .orElseThrow(() -> new CouponDomainException.CouponNotFound(policyId));

        validatePolicy(policy);
        initializeStockIfNeeded(policy);

        // 전체 재고 확인 및 차감
        int requiredStock = userIds.size();
        if (!stockService.batchDecrementStock(policyId, userIds, 1)) {
            int currentStock = stockService.getCurrentStock(policyId);
            log.error("배치 발급 실패 - 재고 부족. required: {}, current: {}",
                     requiredStock, currentStock);
            return BatchIssueResult.failure("재고 부족", requiredStock, 0);
        }

        // 사용자별 발급
        java.util.List<CouponIssue> issuedCoupons = new java.util.ArrayList<>();
        java.util.List<Long> failedUsers = new java.util.ArrayList<>();

        for (Long userId : userIds) {
            try {
                // 사용자별 제한 체크
                if (policy.hasUserLimit()) {
                    boolean allowed = stockService.incrementUserIssueCount(
                        userId, policyId, policy.getMaxUsagePerUser()
                    );
                    if (!allowed) {
                        failedUsers.add(userId);
                        continue;
                    }
                }

                CouponIssue coupon = createCouponIssue(userId, policy, issuedBy);
                CouponIssue saved = saveCouponIssuePort.save(coupon);
                issuedCoupons.add(saved);

            } catch (Exception e) {
                log.error("사용자 쿠폰 발급 실패 - userId: {}, error: {}", userId, e.getMessage());
                failedUsers.add(userId);
            }
        }

        // 실패한 수량만큼 재고 복구
        if (!failedUsers.isEmpty()) {
            stockService.incrementStock(policyId, failedUsers.size());
        }

        return BatchIssueResult.of(
            requiredStock,
            issuedCoupons.size(),
            failedUsers
        );
    }

    /**
     * 선착순 쿠폰 발급
     * 높은 동시성 상황에서의 선착순 발급 처리
     */
    public CouponIssue issueFCFSCoupon(Long policyId, Long userId, String issuedBy) {
        log.info("선착순 쿠폰 발급 시도 - policyId: {}, userId: {}", policyId, userId);

        String userLockKey = ISSUE_LOCK_PREFIX + "user:" + policyId + ":" + userId;
        String lockValue = UUID.randomUUID().toString();

        // 사용자별 중복 발급 방지 락
        if (!distributedLock.tryLock(userLockKey, lockValue, Duration.ofSeconds(3))) {
            throw new CouponDomainException("이미 발급 요청이 진행 중입니다");
        }

        try {
            return issueCoupon(policyId, userId, issuedBy);
        } finally {
            distributedLock.unlock(userLockKey, lockValue);
        }
    }

    /**
     * 정책 검증
     */
    private void validatePolicy(CouponPolicy policy) {
        if (!policy.isActive()) {
            throw new CouponDomainException("비활성화된 쿠폰 정책입니다");
        }

        if (!policy.isIssuable()) {
            if (policy.isExpired()) {
                throw new CouponDomainException.CouponExpired(policy.getId());
            }
            if (policy.isNotStarted()) {
                throw new CouponDomainException("아직 발급 기간이 아닙니다");
            }
            throw new CouponDomainException("발급 불가능한 상태입니다");
        }
    }

    /**
     * Redis 재고 초기화
     */
    private void initializeStockIfNeeded(CouponPolicy policy) {
        if (policy.getMaxIssueCount() == null) {
            // 무제한은 큰 수로 초기화
            stockService.initializeStock(policy.getId(), Integer.MAX_VALUE);
        } else {
            int remainingStock = policy.getMaxIssueCount() -
                               policy.getCurrentIssueCount().get();
            stockService.initializeStock(policy.getId(), remainingStock);
        }
    }

    /**
     * 쿠폰 발급 엔티티 생성
     */
    private CouponIssue createCouponIssue(Long userId, CouponPolicy policy, String issuedBy) {
        return CouponIssue.builder()
                .id(idGenerator.nextId())
                .policyId(policy.getId())
                .userId(userId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName(policy.getCouponName())
                .discountPolicy(policy.getDiscountPolicy())
                .build();
    }

    /**
     * 배치 발급 결과
     */
    public static class BatchIssueResult {
        private final int requested;
        private final int succeeded;
        private final java.util.List<Long> failedUserIds;

        private BatchIssueResult(int requested, int succeeded, java.util.List<Long> failedUserIds) {
            this.requested = requested;
            this.succeeded = succeeded;
            this.failedUserIds = failedUserIds;
        }

        public static BatchIssueResult of(int requested, int succeeded, java.util.List<Long> failedUserIds) {
            return new BatchIssueResult(requested, succeeded, failedUserIds);
        }

        public static BatchIssueResult failure(String reason, int requested, int succeeded) {
            return new BatchIssueResult(requested, succeeded, java.util.Collections.emptyList());
        }

        public int getRequested() { return requested; }
        public int getSucceeded() { return succeeded; }
        public int getFailed() { return requested - succeeded; }
        public java.util.List<Long> getFailedUserIds() { return failedUserIds; }
        public boolean isFullySuccessful() { return requested == succeeded; }
        public boolean isPartiallySuccessful() { return succeeded > 0 && succeeded < requested; }
    }
}
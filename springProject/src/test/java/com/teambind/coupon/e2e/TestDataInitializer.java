package com.teambind.coupon.e2e;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * E2E 테스트용 데이터 초기화 헬퍼
 * 대량의 테스트 데이터를 효율적으로 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataInitializer {

    private final CouponPolicyRepository couponPolicyRepository;
    private final CouponIssueRepository couponIssueRepository;

    private final Random random = new Random();

    /**
     * 쿠폰 정책 생성
     */
    @Transactional
    public List<CouponPolicyEntity> createCouponPolicies(int count) {
        log.info("Creating {} coupon policies for E2E test", count);

        List<CouponPolicyEntity> policies = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            // Snowflake ID 생성 (간단한 구현)
            long snowflakeId = System.currentTimeMillis() * 1000 + i;

            // 적용 가능 상품 규칙 생성
            CouponPolicyEntity.ItemApplicableRuleJson applicableRule =
                CouponPolicyEntity.ItemApplicableRuleJson.builder()
                    .allItemsApplicable(false)
                    .applicableItemIds(List.of(1L, 2L, 3L))
                    .build();

            CouponPolicyEntity policy = CouponPolicyEntity.builder()
                    .id(snowflakeId)
                    .couponName("E2E 테스트 쿠폰 " + (i + 1))
                    .couponCode("E2E_CODE_" + (i + 1))
                    .description("E2E 테스트용 쿠폰 정책")
                    .discountType(i % 2 == 0 ? DiscountType.PERCENTAGE : DiscountType.AMOUNT)
                    .discountValue(i % 2 == 0 ? BigDecimal.valueOf(10) : BigDecimal.valueOf(1000))
                    .minimumOrderAmount(BigDecimal.valueOf(10000))
                    .maxDiscountAmount(BigDecimal.valueOf(5000))
                    .applicableRule(applicableRule)
                    .distributionType(DistributionType.CODE)
                    .validFrom(LocalDateTime.now().minusDays(10))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .maxIssueCount(1000)
                    .maxUsagePerUser(5)
                    .isActive(true)
                    .createdBy(1L)
                    .build();

            policies.add(couponPolicyRepository.save(policy));
        }

        log.info("Created {} coupon policies", policies.size());
        return policies;
    }

    /**
     * 대량의 쿠폰 발급 데이터 생성
     */
    @Transactional
    public List<CouponIssueEntity> createCouponIssues(
            List<Long> userIds,
            List<CouponPolicyEntity> policies,
            int issuesPerUser) {

        log.info("Creating coupon issues for {} users with {} issues each",
                userIds.size(), issuesPerUser);

        List<CouponIssueEntity> issues = new ArrayList<>();

        for (Long userId : userIds) {
            for (int i = 0; i < issuesPerUser; i++) {
                CouponPolicyEntity policy = policies.get(random.nextInt(policies.size()));
                CouponStatus status = generateRandomStatus();

                CouponIssueEntity issue = CouponIssueEntity.builder()
                        .userId(userId)
                        .policyId(policy.getId())
                        .status(status)
                        .issuedAt(LocalDateTime.now().minusDays(random.nextInt(30)))
                        .expiresAt(generateExpiryDate(status))
                        .usedAt(status == CouponStatus.USED ?
                                LocalDateTime.now().minusDays(random.nextInt(5)) : null)
                        .actualDiscountAmount(status == CouponStatus.USED ?
                                BigDecimal.valueOf(random.nextInt(5000)) : null)
                        .build();

                issues.add(issue);
            }
        }

        List<CouponIssueEntity> savedIssues = couponIssueRepository.saveAll(issues);
        log.info("Created {} coupon issues", savedIssues.size());
        return savedIssues;
    }

    /**
     * 특정 상태의 쿠폰 생성
     */
    @Transactional
    public List<CouponIssueEntity> createCouponIssuesWithStatus(
            Long userId,
            CouponPolicyEntity policy,
            CouponStatus status,
            int count) {

        List<CouponIssueEntity> issues = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            CouponIssueEntity issue = CouponIssueEntity.builder()
                    .userId(userId)
                    .policyId(policy.getId())
                    .status(status)
                    .issuedAt(LocalDateTime.now().minusDays(i))
                    .expiresAt(status == CouponStatus.EXPIRED ?
                            LocalDateTime.now().minusDays(1) :
                            LocalDateTime.now().plusDays(30 - i))
                    .usedAt(status == CouponStatus.USED ?
                            LocalDateTime.now().minusDays(1) : null)
                    .build();

            issues.add(issue);
        }

        return couponIssueRepository.saveAll(issues);
    }

    /**
     * 만료 임박 쿠폰 생성
     */
    @Transactional
    public List<CouponIssueEntity> createExpiringCoupons(
            Long userId,
            CouponPolicyEntity policy,
            int daysUntilExpiry,
            int count) {

        List<CouponIssueEntity> issues = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            CouponIssueEntity issue = CouponIssueEntity.builder()
                    .userId(userId)
                    .policyId(policy.getId())
                    .status(CouponStatus.ISSUED)
                    .issuedAt(LocalDateTime.now().minusDays(10))
                    .expiresAt(LocalDateTime.now().plusDays(daysUntilExpiry))
                    .build();

            issues.add(issue);
        }

        return couponIssueRepository.saveAll(issues);
    }

    /**
     * 모든 테스트 데이터 삭제
     */
    @Transactional
    public void cleanupAllTestData() {
        log.info("Cleaning up all E2E test data");

        // E2E 테스트 데이터만 삭제 (이름으로 구분)
        couponIssueRepository.deleteAll();
        couponPolicyRepository.deleteAll(
                couponPolicyRepository.findAll().stream()
                        .filter(p -> p.getCouponName().startsWith("E2E"))
                        .toList()
        );

        log.info("E2E test data cleanup completed");
    }

    // 헬퍼 메서드들
    private Long[] generateProductIds(int seed) {
        int count = (seed % 3) + 1;
        Long[] ids = new Long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = (long) (seed * 10 + i + 1);
        }
        return ids;
    }

    private CouponStatus generateRandomStatus() {
        CouponStatus[] statuses = {
                CouponStatus.ISSUED,
                CouponStatus.ISSUED,  // ISSUED를 더 많이
                CouponStatus.USED,
                CouponStatus.EXPIRED
        };
        return statuses[random.nextInt(statuses.length)];
    }

    private LocalDateTime generateExpiryDate(CouponStatus status) {
        return switch (status) {
            case EXPIRED -> LocalDateTime.now().minusDays(random.nextInt(10) + 1);
            case USED -> LocalDateTime.now().plusDays(random.nextInt(20) + 10);
            default -> LocalDateTime.now().plusDays(random.nextInt(30) + 1);
        };
    }
}
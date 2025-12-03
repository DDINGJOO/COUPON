package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.projection.CouponIssueProjection;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssueQueryRepository 통합 테스트
 * PostgreSQL TestContainer 사용
 */
@SpringBootTest
@Testcontainers
@Transactional
@DisplayName("CouponIssueQueryRepository 통합 테스트")
class CouponIssueQueryRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("coupon_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CouponIssueQueryRepository couponIssueQueryRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    @Autowired
    private CouponPolicyRepository couponPolicyRepository;

    private Long userId;
    private CouponPolicyEntity policy1;
    private CouponPolicyEntity policy2;

    @BeforeEach
    void setUp() {
        userId = 100L;

        // 쿠폰 정책 생성
        policy1 = createCouponPolicy("쿠폰1", new Long[]{1L, 2L, 3L});
        policy2 = createCouponPolicy("쿠폰2", new Long[]{4L, 5L});

        // 쿠폰 발급 데이터 생성
        createCouponIssues();
    }

    @Nested
    @DisplayName("커서 기반 페이지네이션 조회")
    class CursorBasedPagination {

        @Test
        @DisplayName("첫 페이지 조회")
        void findFirstPage() {
            // when
            List<CouponIssueProjection> result = couponIssueQueryRepository.findUserCouponsWithCursor(
                    userId, null, null, null, 3
            );

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getCouponIssueId()).isNotNull();
            assertThat(result.get(0).getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("커서를 사용한 다음 페이지 조회")
        void findNextPageWithCursor() {
            // given
            List<CouponIssueProjection> firstPage = couponIssueQueryRepository.findUserCouponsWithCursor(
                    userId, null, null, null, 2
            );
            Long cursor = firstPage.get(firstPage.size() - 1).getCouponIssueId();

            // when
            List<CouponIssueProjection> nextPage = couponIssueQueryRepository.findUserCouponsWithCursor(
                    userId, null, null, cursor, 2
            );

            // then
            assertThat(nextPage).hasSizeLessThanOrEqualTo(2);
            assertThat(nextPage).allMatch(p -> p.getCouponIssueId() < cursor);
        }
    }

    @Nested
    @DisplayName("필터링 조회")
    class FilteringQuery {

        @Test
        @DisplayName("상태별 필터링 - ISSUED")
        void filterByStatusIssued() {
            // when
            List<CouponIssueProjection> result = couponIssueQueryRepository.findUserCouponsWithCursor(
                    userId, "ISSUED", null, null, 10
            );

            // then
            assertThat(result).allMatch(p -> "ISSUED".equals(p.getStatus()));
        }

        @Test
        @DisplayName("상태별 필터링 - AVAILABLE (특수 처리)")
        void filterByStatusAvailable() {
            // when
            List<CouponIssueProjection> result = couponIssueQueryRepository.findUserCouponsWithCursor(
                    userId, "AVAILABLE", null, null, 10
            );

            // then
            assertThat(result).allMatch(p ->
                    "ISSUED".equals(p.getStatus()) &&
                    p.getExpiresAt().isAfter(LocalDateTime.now())
            );
        }

        @Test
        @DisplayName("상품ID 필터링")
        void filterByProductIds() {
            // when
            String productIds = "{1,2}";  // PostgreSQL 배열 형식
            List<CouponIssueProjection> result = couponIssueQueryRepository.findUserCouponsWithCursor(
                    userId, null, productIds, null, 10
            );

            // then
            assertThat(result).isNotEmpty();
            // policy1의 쿠폰만 조회되어야 함 (상품ID 1,2,3을 포함)
        }

        @Test
        @DisplayName("복합 필터링 - 상태 + 상품ID")
        void filterByMultipleConditions() {
            // when
            List<CouponIssueProjection> result = couponIssueQueryRepository.findUserCouponsWithCursor(
                    userId, "ISSUED", "{1,2,3}", null, 10
            );

            // then
            assertThat(result).allMatch(p ->
                    "ISSUED".equals(p.getStatus())
            );
        }
    }

    @Nested
    @DisplayName("만료 임박 쿠폰 조회")
    class ExpiringCoupons {

        @Test
        @DisplayName("7일 이내 만료 쿠폰 조회")
        void findExpiringWithin7Days() {
            // given
            // 만료 임박 쿠폰 추가 생성
            CouponIssueEntity expiringCoupon = CouponIssueEntity.builder()
                    .id(System.currentTimeMillis() * 1000 + 300)
                    .userId(userId)
                    .policyId(policy1.getId())
                    .status(CouponStatus.ISSUED)
                    .issuedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(3))
                    .build();
            couponIssueRepository.save(expiringCoupon);

            // when
            List<CouponIssueProjection> result = couponIssueQueryRepository.findExpiringCoupons(
                    userId, 7, 10
            );

            // then
            assertThat(result).anyMatch(p ->
                    p.getExpiresAt().isBefore(LocalDateTime.now().plusDays(7))
            );
        }
    }

    @Nested
    @DisplayName("쿠폰 개수 조회")
    class CountCoupons {

        @Test
        @DisplayName("전체 쿠폰 개수 조회")
        void countAllCoupons() {
            // when
            Long count = couponIssueQueryRepository.countUserCoupons(userId, null, null);

            // then
            assertThat(count).isGreaterThan(0);
        }

        @Test
        @DisplayName("상태별 쿠폰 개수 조회")
        void countByStatus() {
            // when
            Long issuedCount = couponIssueQueryRepository.countUserCoupons(userId, "ISSUED", null);
            Long usedCount = couponIssueQueryRepository.countUserCoupons(userId, "USED", null);

            // then
            assertThat(issuedCount).isGreaterThanOrEqualTo(0);
            assertThat(usedCount).isGreaterThanOrEqualTo(0);
        }
    }

    // 테스트 데이터 생성 메서드들
    private CouponPolicyEntity createCouponPolicy(String name, Long[] productIds) {
        // Snowflake ID 생성 (간단한 구현)
        long snowflakeId = System.currentTimeMillis() * 1000 + name.hashCode();

        // 적용 가능 상품 규칙 생성
        CouponPolicyEntity.ItemApplicableRuleJson applicableRule =
                CouponPolicyEntity.ItemApplicableRuleJson.builder()
                        .allItemsApplicable(false)
                        .applicableItemIds(java.util.Arrays.asList(productIds))
                        .build();

        return couponPolicyRepository.save(
                CouponPolicyEntity.builder()
                        .id(snowflakeId)
                        .couponName(name)
                        .couponCode("CODE_" + name)
                        .description("테스트 쿠폰")
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(BigDecimal.TEN)
                        .minimumOrderAmount(BigDecimal.valueOf(10000))
                        .maxDiscountAmount(BigDecimal.valueOf(5000))
                        .applicableRule(applicableRule)
                        .distributionType(DistributionType.CODE)
                        .validFrom(LocalDateTime.now().minusDays(1))
                        .validUntil(LocalDateTime.now().plusDays(30))
                        .maxIssueCount(100)
                        .maxUsagePerUser(3)
                        .isActive(true)
                        .createdBy(1L)
                        .build()
        );
    }

    private void createCouponIssues() {
        // ISSUED 상태 쿠폰
        for (int i = 0; i < 3; i++) {
            long issueId = System.currentTimeMillis() * 1000 + i;
            couponIssueRepository.save(
                    CouponIssueEntity.builder()
                            .id(issueId)
                            .userId(userId)
                            .policyId(policy1.getId())
                            .status(CouponStatus.ISSUED)
                            .issuedAt(LocalDateTime.now().minusDays(i))
                            .expiresAt(LocalDateTime.now().plusDays(30 - i))
                            .build()
            );
        }

        // USED 상태 쿠폰
        couponIssueRepository.save(
                CouponIssueEntity.builder()
                        .id(System.currentTimeMillis() * 1000 + 100)
                        .userId(userId)
                        .policyId(policy2.getId())
                        .status(CouponStatus.USED)
                        .issuedAt(LocalDateTime.now().minusDays(10))
                        .expiresAt(LocalDateTime.now().plusDays(20))
                        .usedAt(LocalDateTime.now().minusDays(5))
                        .actualDiscountAmount(BigDecimal.valueOf(1000))
                        .build()
        );

        // EXPIRED 상태 쿠폰
        couponIssueRepository.save(
                CouponIssueEntity.builder()
                        .id(System.currentTimeMillis() * 1000 + 200)
                        .userId(userId)
                        .policyId(policy1.getId())
                        .status(CouponStatus.EXPIRED)
                        .issuedAt(LocalDateTime.now().minusDays(40))
                        .expiresAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
    }
}
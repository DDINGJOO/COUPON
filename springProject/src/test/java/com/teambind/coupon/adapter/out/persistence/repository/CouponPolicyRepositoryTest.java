package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponPolicyRepository JPA Repository 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("CouponPolicyRepository 테스트")
class CouponPolicyRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CouponPolicyRepository repository;

    private CouponPolicyEntity testPolicy;

    @BeforeEach
    void setUp() {
        // Snowflake ID를 직접 생성 (테스트용)
        long snowflakeId = System.currentTimeMillis() << 22 | (1L << 12) | 1L;

        testPolicy = CouponPolicyEntity.builder()
                .id(snowflakeId)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .description("테스트용 쿠폰입니다")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .minOrderAmount(new BigDecimal("10000"))
                .maxDiscountAmount(new BigDecimal("5000"))
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(100)
                .maxUsagePerUser(2)
                .isActive(true)
                .createdBy(1L)
                .build();
    }

    @Test
    @DisplayName("쿠폰 정책 저장")
    void save() {
        // when
        CouponPolicyEntity saved = repository.save(testPolicy);
        entityManager.flush();

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(saved.getCouponCode()).isEqualTo("TEST2024");
    }

    @Test
    @DisplayName("ID로 조회")
    void findById() {
        // given
        CouponPolicyEntity saved = entityManager.persistAndFlush(testPolicy);

        // when
        Optional<CouponPolicyEntity> found = repository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getCouponName()).isEqualTo("테스트 쿠폰");
    }

    @Test
    @DisplayName("쿠폰 코드로 조회")
    void findByCouponCode() {
        // given
        entityManager.persistAndFlush(testPolicy);

        // when
        Optional<CouponPolicyEntity> found = repository.findByCouponCodeAndIsActiveTrue("TEST2024");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getCouponCode()).isEqualTo("TEST2024");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("활성화된 쿠폰 정책만 조회")
    void findActiveOnly() {
        // given
        CouponPolicyEntity inactivePolicy = CouponPolicyEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 2L)
                .couponName("비활성 쿠폰")
                .couponCode("INACTIVE")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("1000"))
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(7))
                .isActive(false)
                .build();

        entityManager.persistAndFlush(testPolicy);
        entityManager.persistAndFlush(inactivePolicy);

        // when
        List<CouponPolicyEntity> activePolicies = repository.findByIsActiveTrueOrderByCreatedAtDesc();

        // then
        assertThat(activePolicies).hasSize(1);
        assertThat(activePolicies.get(0).getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(activePolicies.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("유효기간 내 쿠폰 정책 조회")
    void findValidPolicies() {
        // given
        LocalDateTime now = LocalDateTime.now();

        CouponPolicyEntity expiredPolicy = CouponPolicyEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 3L)
                .couponName("만료된 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("1000"))
                .distributionType(DistributionType.DIRECT)
                .validFrom(now.minusDays(30))
                .validUntil(now.minusDays(1))
                .isActive(true)
                .build();

        CouponPolicyEntity futurePolicy = CouponPolicyEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 4L)
                .couponName("미래 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("2000"))
                .distributionType(DistributionType.DIRECT)
                .validFrom(now.plusDays(1))
                .validUntil(now.plusDays(30))
                .isActive(true)
                .build();

        entityManager.persistAndFlush(testPolicy);
        entityManager.persistAndFlush(expiredPolicy);
        entityManager.persistAndFlush(futurePolicy);

        // when
        List<CouponPolicyEntity> validPolicies = repository.findActiveAndValidPolicies(now);

        // then
        assertThat(validPolicies).hasSize(1);
        assertThat(validPolicies.get(0).getCouponName()).isEqualTo("테스트 쿠폰");
    }

    @Test
    @DisplayName("배포 타입별 조회")
    void findByDistributionType() {
        // given
        CouponPolicyEntity directPolicy = CouponPolicyEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 5L)
                .couponName("직접 발급 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("3000"))
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(7))
                .isActive(true)
                .build();

        entityManager.persistAndFlush(testPolicy);
        entityManager.persistAndFlush(directPolicy);

        // when
        List<CouponPolicyEntity> codePolicies = repository.findByDistributionTypeAndIsActiveTrue(DistributionType.CODE);

        // then
        assertThat(codePolicies).hasSize(1);
        assertThat(codePolicies.get(0).getDistributionType()).isEqualTo(DistributionType.CODE);
    }

    @Test
    @DisplayName("정책 수정")
    void update() {
        // given
        CouponPolicyEntity saved = entityManager.persistAndFlush(testPolicy);

        // when
        saved.setMaxIssueCount(200);
        saved.setMaxUsagePerUser(3);
        saved.setActive(false);
        repository.save(saved);
        entityManager.flush();

        // then
        CouponPolicyEntity updated = entityManager.find(CouponPolicyEntity.class, saved.getId());
        assertThat(updated.getMaxIssueCount()).isEqualTo(200);
        assertThat(updated.getMaxUsagePerUser()).isEqualTo(3);
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @DisplayName("Pessimistic Write Lock으로 조회")
    void findByCouponCodeWithLock() {
        // given
        entityManager.persistAndFlush(testPolicy);

        // when
        Optional<CouponPolicyEntity> found = repository.findByCouponCodeWithLock("TEST2024");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getCouponCode()).isEqualTo("TEST2024");
    }

    @Test
    @DisplayName("쿠폰 코드 존재 여부 확인")
    void existsByCouponCode() {
        // given
        entityManager.persistAndFlush(testPolicy);

        // when
        boolean exists = repository.existsByCouponCode("TEST2024");
        boolean notExists = repository.existsByCouponCode("NONEXISTENT");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("만료 임박 쿠폰 정책 조회")
    void findExpiringSoonPolicies() {
        // given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysLater = now.plusDays(7);

        CouponPolicyEntity expiringSoon = CouponPolicyEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 7L)
                .couponName("곧 만료 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("1000"))
                .distributionType(DistributionType.CODE)
                .validFrom(now.minusDays(10))
                .validUntil(now.plusDays(3))
                .isActive(true)
                .build();

        entityManager.persistAndFlush(testPolicy); // 30일 후 만료
        entityManager.persistAndFlush(expiringSoon); // 3일 후 만료

        // when
        List<CouponPolicyEntity> expiringPolicies = repository.findExpiringSoonPolicies(now, sevenDaysLater);

        // then
        assertThat(expiringPolicies).hasSize(1);
        assertThat(expiringPolicies.get(0).getCouponName()).isEqualTo("곧 만료 쿠폰");
    }
}
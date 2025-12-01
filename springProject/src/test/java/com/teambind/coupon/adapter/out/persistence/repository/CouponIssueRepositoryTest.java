package com.teambind.coupon.adapter.out.persistence.repository;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.config.TestJpaConfig;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssueRepository JPA Repository 테스트
 */
@DataJpaTest
@Import(TestJpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:25432/coupon_test_db",
    "spring.datasource.username=testuser",
    "spring.datasource.password=testpass",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
@DisplayName("CouponIssueRepository 테스트")
class CouponIssueRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CouponIssueRepository repository;

    @Autowired
    private CouponPolicyRepository policyRepository;

    private CouponPolicyEntity testPolicy;
    private CouponIssueEntity testIssue;

    @BeforeEach
    void setUp() {
        // 테스트용 정책 생성
        testPolicy = CouponPolicyEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 1L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("3000"))
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        testPolicy = policyRepository.save(testPolicy);
        entityManager.flush();

        // 테스트용 발급 생성
        testIssue = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 2L)
                .policyId(testPolicy.getId())
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiresAt(testPolicy.getValidUntil())
                .build();
    }

    @Test
    @DisplayName("쿠폰 발급 저장")
    void save() {
        // when
        CouponIssueEntity saved = repository.save(testIssue);
        entityManager.flush();

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo(CouponStatus.ISSUED);
    }

    @Test
    @DisplayName("사용자별 쿠폰 조회")
    void findByUserId() {
        // given
        CouponIssueEntity issue1 = repository.save(testIssue);

        CouponIssueEntity issue2 = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 3L)
                .policyId(testPolicy.getId())
                .userId(100L)
                .status(CouponStatus.USED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .usedAt(LocalDateTime.now())
                .orderId("ORDER-123")
                .build();
        repository.save(issue2);

        entityManager.flush();
        entityManager.clear();

        // when
        List<CouponIssueEntity> userCoupons = repository.findByUserIdOrderByIssuedAtDesc(100L);

        // then
        assertThat(userCoupons).hasSize(2);
        assertThat(userCoupons.get(0).getUserId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("사용자별 활성 쿠폰 조회")
    void findActiveByUserId() {
        // given
        repository.save(testIssue);

        CouponIssueEntity usedCoupon = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 4L)
                .policyId(testPolicy.getId())
                .userId(100L)
                .status(CouponStatus.USED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .usedAt(LocalDateTime.now())
                .build();
        repository.save(usedCoupon);

        entityManager.flush();
        entityManager.clear();

        // when
        List<CouponIssueEntity> activeCoupons = repository.findActiveByUserId(
                100L, LocalDateTime.now()
        );

        // then
        assertThat(activeCoupons).hasSize(1);
        assertThat(activeCoupons.get(0).getStatus()).isEqualTo(CouponStatus.ISSUED);
    }

    @Test
    @DisplayName("예약된 쿠폰 조회")
    void findReservedCoupon() {
        // given
        String reservationId = UUID.randomUUID().toString();
        testIssue.setStatus(CouponStatus.RESERVED);
        testIssue.setReservationId(reservationId);
        testIssue.setReservedAt(LocalDateTime.now());
        repository.save(testIssue);

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<CouponIssueEntity> reserved = repository.findByReservationId(reservationId);

        // then
        assertThat(reserved).isPresent();
        assertThat(reserved.get().getStatus()).isEqualTo(CouponStatus.RESERVED);
        assertThat(reserved.get().getReservationId()).isEqualTo(reservationId);
    }

    @Test
    @DisplayName("타임아웃된 예약 쿠폰 조회")
    void findTimedOutReservations() {
        // given
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(30);

        CouponIssueEntity timedOut = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 5L)
                .policyId(testPolicy.getId())
                .userId(101L)
                .status(CouponStatus.RESERVED)
                .issuedAt(LocalDateTime.now().minusHours(1))
                .reservedAt(LocalDateTime.now().minusMinutes(35))
                .reservationId(UUID.randomUUID().toString())
                .build();
        repository.save(timedOut);

        CouponIssueEntity notTimedOut = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 6L)
                .policyId(testPolicy.getId())
                .userId(102L)
                .status(CouponStatus.RESERVED)
                .issuedAt(LocalDateTime.now().minusMinutes(10))
                .reservedAt(LocalDateTime.now().minusMinutes(10))
                .reservationId(UUID.randomUUID().toString())
                .build();
        repository.save(notTimedOut);

        entityManager.flush();
        entityManager.clear();

        // when
        List<CouponIssueEntity> timedOutList = repository.findTimedOutReservations(timeout);

        // then
        assertThat(timedOutList).hasSize(1);
        assertThat(timedOutList.get(0).getUserId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("만료 대상 쿠폰 조회")
    void findExpiredCoupons() {
        // given
        LocalDateTime now = LocalDateTime.now();

        CouponIssueEntity expired = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 7L)
                .policyId(testPolicy.getId())
                .userId(103L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(31))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        repository.save(expired);

        entityManager.flush();
        entityManager.clear();

        // when
        List<CouponIssueEntity> expiredList = repository.findExpiredCoupons(now);

        // then
        assertThat(expiredList).hasSize(1);
        assertThat(expiredList.get(0).getUserId()).isEqualTo(103L);
    }

    @Test
    @DisplayName("사용자별 정책별 발급 개수 카운트")
    void countByUserAndPolicy() {
        // given
        repository.save(testIssue);

        CouponIssueEntity second = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 8L)
                .policyId(testPolicy.getId())
                .userId(100L)
                .status(CouponStatus.USED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .build();
        repository.save(second);

        entityManager.flush();

        // when
        long count = repository.countByUserIdAndPolicyId(100L, testPolicy.getId());

        // then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("페이징 처리")
    void paging() {
        // given
        for (int i = 0; i < 25; i++) {
            CouponIssueEntity issue = CouponIssueEntity.builder()
                    .id(System.currentTimeMillis() << 22 | (1L << 12) | (100L + i))
                    .policyId(testPolicy.getId())
                    .userId(100L)
                    .status(CouponStatus.ISSUED)
                    .issuedAt(LocalDateTime.now().minusHours(i))
                    .build();
            repository.save(issue);
        }

        entityManager.flush();
        entityManager.clear();

        // when
        Page<CouponIssueEntity> page = repository.findByUserId(100L, PageRequest.of(0, 10));

        // then
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("벌크 업데이트 - 만료 처리")
    void bulkUpdateExpired() {
        // given
        LocalDateTime expiredDate = LocalDateTime.now().minusDays(1);

        for (int i = 0; i < 5; i++) {
            CouponIssueEntity issue = CouponIssueEntity.builder()
                    .id(System.currentTimeMillis() << 22 | (1L << 12) | (200L + i))
                    .policyId(testPolicy.getId())
                    .userId(200L + i)
                    .status(CouponStatus.ISSUED)
                    .issuedAt(LocalDateTime.now().minusDays(30))
                    .expiresAt(expiredDate)
                    .build();
            repository.save(issue);
        }

        entityManager.flush();
        entityManager.clear();

        // when
        int updated = repository.updateExpiredCoupons(LocalDateTime.now());

        // then
        assertThat(updated).isEqualTo(5);

        List<CouponIssueEntity> expiredCoupons = repository.findByStatus(CouponStatus.EXPIRED);
        assertThat(expiredCoupons).hasSize(5);
    }

    @Test
    @DisplayName("통계 조회 - 상태별 카운트")
    void countByStatus() {
        // given
        // ISSUED 상태
        repository.save(testIssue);

        // USED 상태
        CouponIssueEntity used = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 300L)
                .policyId(testPolicy.getId())
                .userId(300L)
                .status(CouponStatus.USED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .usedAt(LocalDateTime.now())
                .build();
        repository.save(used);

        // RESERVED 상태
        CouponIssueEntity reserved = CouponIssueEntity.builder()
                .id(System.currentTimeMillis() << 22 | (1L << 12) | 301L)
                .policyId(testPolicy.getId())
                .userId(301L)
                .status(CouponStatus.RESERVED)
                .issuedAt(LocalDateTime.now())
                .reservedAt(LocalDateTime.now())
                .reservationId(UUID.randomUUID().toString())
                .build();
        repository.save(reserved);

        entityManager.flush();

        // when
        long issuedCount = repository.countByStatus(CouponStatus.ISSUED);
        long usedCount = repository.countByStatus(CouponStatus.USED);
        long reservedCount = repository.countByStatus(CouponStatus.RESERVED);

        // then
        assertThat(issuedCount).isEqualTo(1);
        assertThat(usedCount).isEqualTo(1);
        assertThat(reservedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 발급 체크")
    void checkDuplicateIssue() {
        // given
        repository.save(testIssue);
        entityManager.flush();

        // when
        boolean exists = repository.existsByUserIdAndPolicyId(100L, testPolicy.getId());

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Native Query - 일일 발급 통계")
    void dailyIssueStatistics() {
        // given
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

        for (int i = 0; i < 10; i++) {
            CouponIssueEntity issue = CouponIssueEntity.builder()
                    .id(System.currentTimeMillis() << 22 | (1L << 12) | (400L + i))
                    .policyId(testPolicy.getId())
                    .userId(400L + i)
                    .status(i < 7 ? CouponStatus.ISSUED : CouponStatus.USED)
                    .issuedAt(today.plusHours(i))
                    .build();
            repository.save(issue);
        }

        entityManager.flush();

        // when
        var stats = repository.getDailyStatistics(today, today.plusDays(1));

        // then
        assertThat(stats).containsEntry("totalIssued", 10L);
        assertThat(stats).containsEntry("totalUsed", 3L);
    }
}
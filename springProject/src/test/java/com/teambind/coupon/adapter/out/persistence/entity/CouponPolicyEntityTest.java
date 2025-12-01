package com.teambind.coupon.adapter.out.persistence.entity;

import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponPolicyEntity JPA 엔티티 단위 테스트
 */
@DisplayName("CouponPolicyEntity 테스트")
class CouponPolicyEntityTest {

    private CouponPolicyEntity entity;

    @BeforeEach
    void setUp() {
        entity = CouponPolicyEntity.builder()
                .id(1L)
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
    @DisplayName("엔티티 생성 및 필드 검증")
    void createEntity() {
        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(entity.getCouponCode()).isEqualTo("TEST2024");
        assertThat(entity.getDescription()).isEqualTo("테스트용 쿠폰입니다");
        assertThat(entity.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(entity.getDiscountValue()).isEqualByComparingTo("10");
        assertThat(entity.getMinOrderAmount()).isEqualByComparingTo("10000");
        assertThat(entity.getMaxDiscountAmount()).isEqualByComparingTo("5000");
        assertThat(entity.getDistributionType()).isEqualTo(DistributionType.CODE);
        assertThat(entity.getMaxIssueCount()).isEqualTo(100);
        assertThat(entity.getMaxUsagePerUser()).isEqualTo(2);
        assertThat(entity.isActive()).isTrue();
    }

    @Test
    @DisplayName("기본값 검증")
    void defaultValues() {
        CouponPolicyEntity defaultEntity = CouponPolicyEntity.builder()
                .couponName("기본 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("1000"))
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(7))
                .build();

        assertThat(defaultEntity.isActive()).isTrue(); // @Builder.Default 적용
        assertThat(defaultEntity.getMaxIssueCount()).isNull();
        assertThat(defaultEntity.getMaxUsagePerUser()).isNull();
    }

    @Test
    @DisplayName("적용 가능 규칙 JSON 변환")
    void applicableRuleJson() {
        CouponPolicyEntity.ItemApplicableRuleJson rule = new CouponPolicyEntity.ItemApplicableRuleJson();
        rule.setAllItemsApplicable(false);
        rule.setApplicableItemIds(List.of(100L, 200L, 300L));

        entity.setApplicableRule(rule);

        assertThat(entity.getApplicableRule()).isNotNull();
        assertThat(entity.getApplicableRule().isAllItemsApplicable()).isFalse();
        assertThat(entity.getApplicableRule().getApplicableItemIds()).containsExactly(100L, 200L, 300L);
    }

    @Test
    @DisplayName("유효기간 설정")
    void validityPeriod() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusDays(30);

        entity.setValidFrom(now);
        entity.setValidUntil(future);

        assertThat(entity.getValidFrom()).isEqualTo(now);
        assertThat(entity.getValidUntil()).isEqualTo(future);
        assertThat(entity.getValidUntil()).isAfter(entity.getValidFrom());
    }

    @Test
    @DisplayName("무제한 발급 설정")
    void unlimitedIssue() {
        entity.setMaxIssueCount(null);

        assertThat(entity.getMaxIssueCount()).isNull();
    }

    @Test
    @DisplayName("Optimistic Locking 버전 관리")
    void versionManagement() {
        entity.setVersion(1L);
        assertThat(entity.getVersion()).isEqualTo(1L);

        entity.setVersion(2L);
        assertThat(entity.getVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("BaseEntity 상속 필드 확인")
    void baseEntityFields() {
        // BaseEntity의 createdAt과 updatedAt은 JPA Auditing이 자동 관리
        // 실제 영속성 컨텍스트에서 테스트 필요
        assertThat(entity).isInstanceOf(BaseEntity.class);
    }

    @Test
    @DisplayName("빌더 패턴 - 필수 필드만")
    void builderWithRequiredFields() {
        CouponPolicyEntity minimal = CouponPolicyEntity.builder()
                .id(2L)
                .couponName("최소 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(7))
                .build();

        assertThat(minimal).isNotNull();
        assertThat(minimal.getId()).isEqualTo(2L);
        assertThat(minimal.getCouponName()).isEqualTo("최소 쿠폰");
        assertThat(minimal.getCouponCode()).isNull();
        assertThat(minimal.getDescription()).isNull();
    }
}
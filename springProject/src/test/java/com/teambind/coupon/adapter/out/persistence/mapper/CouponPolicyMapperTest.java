package com.teambind.coupon.adapter.out.persistence.mapper;

import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponPolicyMapper 단위 테스트
 */
@DisplayName("CouponPolicyMapper 테스트")
class CouponPolicyMapperTest {

    private CouponPolicyMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CouponPolicyMapper();
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - 전체 필드")
    void toDomain_AllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponPolicyEntity.ItemApplicableRuleJson ruleJson =
                CouponPolicyEntity.ItemApplicableRuleJson.builder()
                        .allItemsApplicable(false)
                        .applicableItemIds(List.of(1L, 2L, 3L))
                        .build();

        CouponPolicyEntity entity = CouponPolicyEntity.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .description("테스트용 쿠폰입니다")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15"))
                .maxDiscountAmount(new BigDecimal("10000"))
                .minimumOrderAmount(new BigDecimal("30000"))
                .applicableRule(ruleJson)
                .distributionType(DistributionType.CODE)
                .validFrom(now.minusDays(1))
                .validUntil(now.plusDays(30))
                .maxIssueCount(1000)
                .maxUsagePerUser(5)
                .isActive(true)
                .createdBy(100L)
                .build();

        // when
        CouponPolicy domain = mapper.toDomain(entity);

        // then
        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(domain.getCouponCode()).isEqualTo("TEST2024");
        assertThat(domain.getDescription()).isEqualTo("테스트용 쿠폰입니다");
        assertThat(domain.getDistributionType()).isEqualTo(DistributionType.CODE);
        assertThat(domain.getValidFrom()).isEqualTo(now.minusDays(1));
        assertThat(domain.getValidUntil()).isEqualTo(now.plusDays(30));
        assertThat(domain.getMaxIssueCount()).isEqualTo(1000);
        assertThat(domain.getMaxUsagePerUser()).isEqualTo(5);
        assertThat(domain.isActive()).isTrue();
        assertThat(domain.getCreatedBy()).isEqualTo(100L);

        // 할인 정책 검증
        assertThat(domain.getDiscountPolicy()).isNotNull();
        assertThat(domain.getDiscountPolicy().getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(domain.getDiscountPolicy().getDiscountValue()).isEqualTo(new BigDecimal("15"));
        assertThat(domain.getDiscountPolicy().getMaxDiscountAmount()).isEqualTo(new BigDecimal("10000"));
        assertThat(domain.getDiscountPolicy().getMinOrderAmount()).isEqualTo(new BigDecimal("30000"));

        // 적용 규칙 검증
        assertThat(domain.getApplicableRule()).isNotNull();
        assertThat(domain.getApplicableRule().isAllItemsApplicable()).isFalse();
        assertThat(domain.getApplicableRule().getApplicableItemIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - 필수 필드만")
    void toDomain_RequiredFieldsOnly() {
        // given
        CouponPolicyEntity entity = CouponPolicyEntity.builder()
                .id(1L)
                .couponName("최소 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        // when
        CouponPolicy domain = mapper.toDomain(entity);

        // then
        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getCouponName()).isEqualTo("최소 쿠폰");
        assertThat(domain.getDistributionType()).isEqualTo(DistributionType.DIRECT);
        assertThat(domain.isActive()).isTrue();
        assertThat(domain.getCouponCode()).isNull();
        assertThat(domain.getDescription()).isNull();
        assertThat(domain.getApplicableRule()).isNull();
        assertThat(domain.getMaxIssueCount()).isNull();
        assertThat(domain.getMaxUsagePerUser()).isNull();
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - null 처리")
    void toDomain_NullEntity() {
        // when
        CouponPolicy domain = mapper.toDomain(null);

        // then
        assertThat(domain).isNull();
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - 전체 상품 적용 규칙")
    void toDomain_AllItemsApplicableRule() {
        // given
        CouponPolicyEntity.ItemApplicableRuleJson ruleJson =
                CouponPolicyEntity.ItemApplicableRuleJson.builder()
                        .allItemsApplicable(true)
                        .applicableItemIds(null)
                        .build();

        CouponPolicyEntity entity = CouponPolicyEntity.builder()
                .id(1L)
                .couponName("전체 적용 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("3000"))
                .applicableRule(ruleJson)
                .distributionType(DistributionType.EVENT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(7))
                .isActive(true)
                .build();

        // when
        CouponPolicy domain = mapper.toDomain(entity);

        // then
        assertThat(domain.getApplicableRule()).isNotNull();
        assertThat(domain.getApplicableRule().isAllItemsApplicable()).isTrue();
        assertThat(domain.getApplicableRule().getApplicableItemIds()).isNull();
    }

    @Test
    @DisplayName("도메인 모델을 엔티티로 변환 - 전체 필드")
    void toEntity_AllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        DiscountPolicy discountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20"))
                .minOrderAmount(new BigDecimal("50000"))
                .maxDiscountAmount(new BigDecimal("20000"))
                .build();

        ItemApplicableRule rule = new ItemApplicableRule(false, List.of(10L, 20L, 30L));

        CouponPolicy domain = CouponPolicy.builder()
                .id(1L)
                .couponName("프리미엄 쿠폰")
                .couponCode("PREMIUM2024")
                .description("프리미엄 회원 전용 쿠폰")
                .discountPolicy(discountPolicy)
                .applicableRule(rule)
                .distributionType(DistributionType.CODE)
                .validFrom(now.minusDays(3))
                .validUntil(now.plusDays(27))
                .maxIssueCount(500)
                .maxUsagePerUser(3)
                .isActive(true)
                .createdBy(200L)
                .build();

        // when
        CouponPolicyEntity entity = mapper.toEntity(domain);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getCouponName()).isEqualTo("프리미엄 쿠폰");
        assertThat(entity.getCouponCode()).isEqualTo("PREMIUM2024");
        assertThat(entity.getDescription()).isEqualTo("프리미엄 회원 전용 쿠폰");
        assertThat(entity.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(entity.getDiscountValue()).isEqualTo(new BigDecimal("20"));
        assertThat(entity.getMaxDiscountAmount()).isEqualTo(new BigDecimal("20000"));
        assertThat(entity.getMinimumOrderAmount()).isEqualTo(new BigDecimal("50000"));
        assertThat(entity.getDistributionType()).isEqualTo(DistributionType.CODE);
        assertThat(entity.getValidFrom()).isEqualTo(now.minusDays(3));
        assertThat(entity.getValidUntil()).isEqualTo(now.plusDays(27));
        assertThat(entity.getMaxIssueCount()).isEqualTo(500);
        assertThat(entity.getMaxUsagePerUser()).isEqualTo(3);
        assertThat(entity.isActive()).isTrue();
        assertThat(entity.getCreatedBy()).isEqualTo(200L);

        // 적용 규칙 검증
        assertThat(entity.getApplicableRule()).isNotNull();
        assertThat(entity.getApplicableRule().isAllItemsApplicable()).isFalse();
        assertThat(entity.getApplicableRule().getApplicableItemIds()).containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("도메인 모델을 엔티티로 변환 - 필수 필드만")
    void toEntity_RequiredFieldsOnly() {
        // given
        DiscountPolicy discountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("10000"))
                .build();

        CouponPolicy domain = CouponPolicy.builder()
                .couponName("기본 쿠폰")
                .discountPolicy(discountPolicy)
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(14))
                .isActive(false)
                .build();

        // when
        CouponPolicyEntity entity = mapper.toEntity(domain);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getCouponName()).isEqualTo("기본 쿠폰");
        assertThat(entity.getDiscountType()).isEqualTo(DiscountType.AMOUNT);
        assertThat(entity.getDiscountValue()).isEqualTo(new BigDecimal("10000"));
        assertThat(entity.getDistributionType()).isEqualTo(DistributionType.DIRECT);
        assertThat(entity.isActive()).isFalse();
        assertThat(entity.getCouponCode()).isNull();
        assertThat(entity.getDescription()).isNull();
        assertThat(entity.getApplicableRule()).isNull();
    }

    @Test
    @DisplayName("도메인 모델을 엔티티로 변환 - null 처리")
    void toEntity_NullDomain() {
        // when
        CouponPolicyEntity entity = mapper.toEntity(null);

        // then
        assertThat(entity).isNull();
    }

    @Test
    @DisplayName("엔티티 업데이트")
    void updateEntity() {
        // given
        CouponPolicyEntity entity = CouponPolicyEntity.builder()
                .id(1L)
                .couponName("기존 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .isActive(true)
                .build();

        DiscountPolicy newDiscountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("25"))
                .minOrderAmount(new BigDecimal("100000"))
                .maxDiscountAmount(new BigDecimal("30000"))
                .build();

        ItemApplicableRule newRule = new ItemApplicableRule(false, List.of(100L, 200L));

        CouponPolicy domain = CouponPolicy.builder()
                .couponName("업데이트된 쿠폰")
                .description("새로운 설명")
                .discountPolicy(newDiscountPolicy)
                .applicableRule(newRule)
                .validFrom(LocalDateTime.now().plusDays(1))
                .validUntil(LocalDateTime.now().plusDays(60))
                .maxIssueCount(2000)
                .maxUsagePerUser(10)
                .isActive(false)
                .build();

        // when
        mapper.updateEntity(entity, domain);

        // then
        assertThat(entity.getCouponName()).isEqualTo("업데이트된 쿠폰");
        assertThat(entity.getDescription()).isEqualTo("새로운 설명");
        assertThat(entity.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(entity.getDiscountValue()).isEqualTo(new BigDecimal("25"));
        assertThat(entity.getMaxDiscountAmount()).isEqualTo(new BigDecimal("30000"));
        assertThat(entity.getMinimumOrderAmount()).isEqualTo(new BigDecimal("100000"));
        assertThat(entity.getValidFrom()).isEqualTo(domain.getValidFrom());
        assertThat(entity.getValidUntil()).isEqualTo(domain.getValidUntil());
        assertThat(entity.getMaxIssueCount()).isEqualTo(2000);
        assertThat(entity.getMaxUsagePerUser()).isEqualTo(10);
        assertThat(entity.isActive()).isFalse();

        assertThat(entity.getApplicableRule()).isNotNull();
        assertThat(entity.getApplicableRule().isAllItemsApplicable()).isFalse();
        assertThat(entity.getApplicableRule().getApplicableItemIds()).containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("엔티티 업데이트 - 적용 규칙 없음")
    void updateEntity_NoApplicableRule() {
        // given
        CouponPolicyEntity entity = CouponPolicyEntity.builder()
                .id(1L)
                .applicableRule(CouponPolicyEntity.ItemApplicableRuleJson.builder()
                        .allItemsApplicable(true)
                        .build())
                .build();

        DiscountPolicy discountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("7000"))
                .build();

        CouponPolicy domain = CouponPolicy.builder()
                .couponName("규칙 없는 쿠폰")
                .discountPolicy(discountPolicy)
                .applicableRule(null) // 적용 규칙 없음
                .isActive(true)
                .build();

        // when
        mapper.updateEntity(entity, domain);

        // then
        assertThat(entity.getCouponName()).isEqualTo("규칙 없는 쿠폰");
        assertThat(entity.getApplicableRule()).isNotNull(); // 기존 규칙 유지
    }

    @Test
    @DisplayName("양방향 변환 일관성 검증")
    void bidirectionalConversion() {
        // given
        DiscountPolicy discountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("30"))
                .minOrderAmount(new BigDecimal("70000"))
                .maxDiscountAmount(new BigDecimal("50000"))
                .build();

        ItemApplicableRule rule = new ItemApplicableRule(true, null);

        CouponPolicy original = CouponPolicy.builder()
                .id(1L)
                .couponName("일관성 테스트 쿠폰")
                .couponCode("CONSIST2024")
                .description("양방향 변환 테스트")
                .discountPolicy(discountPolicy)
                .applicableRule(rule)
                .distributionType(DistributionType.EVENT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(90))
                .maxIssueCount(10000)
                .maxUsagePerUser(1)
                .isActive(true)
                .createdBy(999L)
                .build();

        // when
        CouponPolicyEntity entity = mapper.toEntity(original);
        CouponPolicy converted = mapper.toDomain(entity);

        // then
        assertThat(converted.getId()).isEqualTo(original.getId());
        assertThat(converted.getCouponName()).isEqualTo(original.getCouponName());
        assertThat(converted.getCouponCode()).isEqualTo(original.getCouponCode());
        assertThat(converted.getDescription()).isEqualTo(original.getDescription());
        assertThat(converted.getDistributionType()).isEqualTo(original.getDistributionType());
        assertThat(converted.getMaxIssueCount()).isEqualTo(original.getMaxIssueCount());
        assertThat(converted.getMaxUsagePerUser()).isEqualTo(original.getMaxUsagePerUser());
        assertThat(converted.isActive()).isEqualTo(original.isActive());
        assertThat(converted.getCreatedBy()).isEqualTo(original.getCreatedBy());

        // 할인 정책 검증
        assertThat(converted.getDiscountPolicy().getDiscountType())
                .isEqualTo(original.getDiscountPolicy().getDiscountType());
        assertThat(converted.getDiscountPolicy().getDiscountValue())
                .isEqualTo(original.getDiscountPolicy().getDiscountValue());
        assertThat(converted.getDiscountPolicy().getMinOrderAmount())
                .isEqualTo(original.getDiscountPolicy().getMinOrderAmount());
        assertThat(converted.getDiscountPolicy().getMaxDiscountAmount())
                .isEqualTo(original.getDiscountPolicy().getMaxDiscountAmount());

        // 적용 규칙 검증
        assertThat(converted.getApplicableRule().isAllItemsApplicable())
                .isEqualTo(original.getApplicableRule().isAllItemsApplicable());
    }
}
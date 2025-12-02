package com.teambind.coupon.adapter.out.persistence.mapper;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssueMapper 단위 테스트
 */
@DisplayName("CouponIssueMapper 테스트")
class CouponIssueMapperTest {

    private CouponIssueMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CouponIssueMapper();
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - 전체 필드")
    void toDomain_AllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponIssueEntity entity = CouponIssueEntity.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.RESERVED)
                .reservationId("RESV-123")
                .orderId("ORD-456")
                .issuedAt(now.minusDays(1))
                .reservedAt(now.minusHours(2))
                .usedAt(now)
                .expiredAt(now.plusDays(30))
                .actualDiscountAmount(new BigDecimal("5000"))
                .couponName("테스트 쿠폰")
                .discountType("AMOUNT")
                .discountValue(new BigDecimal("10000"))
                .minOrderAmount(new BigDecimal("30000"))
                .maxDiscountAmount(new BigDecimal("15000"))
                .build();

        // when
        CouponIssue domain = mapper.toDomain(entity);

        // then
        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getPolicyId()).isEqualTo(100L);
        assertThat(domain.getUserId()).isEqualTo(1000L);
        assertThat(domain.getStatus()).isEqualTo(CouponStatus.RESERVED);
        assertThat(domain.getReservationId()).isEqualTo("RESV-123");
        assertThat(domain.getOrderId()).isEqualTo("ORD-456");
        assertThat(domain.getIssuedAt()).isEqualTo(now.minusDays(1));
        assertThat(domain.getReservedAt()).isEqualTo(now.minusHours(2));
        assertThat(domain.getUsedAt()).isEqualTo(now);
        assertThat(domain.getExpiredAt()).isEqualTo(now.plusDays(30));
        assertThat(domain.getActualDiscountAmount()).isEqualTo(new BigDecimal("5000"));
        assertThat(domain.getCouponName()).isEqualTo("테스트 쿠폰");

        // 할인 정책 검증
        assertThat(domain.getDiscountPolicy()).isNotNull();
        assertThat(domain.getDiscountPolicy().getDiscountType()).isEqualTo(DiscountType.AMOUNT);
        assertThat(domain.getDiscountPolicy().getDiscountValue()).isEqualTo(new BigDecimal("10000"));
        assertThat(domain.getDiscountPolicy().getMinOrderAmount()).isEqualTo(new BigDecimal("30000"));
        assertThat(domain.getDiscountPolicy().getMaxDiscountAmount()).isEqualTo(new BigDecimal("15000"));
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - 필수 필드만")
    void toDomain_RequiredFieldsOnly() {
        // given
        CouponIssueEntity entity = CouponIssueEntity.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("최소 쿠폰")
                .build();

        // when
        CouponIssue domain = mapper.toDomain(entity);

        // then
        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getPolicyId()).isEqualTo(100L);
        assertThat(domain.getUserId()).isEqualTo(1000L);
        assertThat(domain.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(domain.getCouponName()).isEqualTo("최소 쿠폰");
        assertThat(domain.getDiscountPolicy()).isNull();
        assertThat(domain.getReservationId()).isNull();
        assertThat(domain.getOrderId()).isNull();
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - null 처리")
    void toDomain_NullEntity() {
        // when
        CouponIssue domain = mapper.toDomain(null);

        // then
        assertThat(domain).isNull();
    }

    @Test
    @DisplayName("엔티티를 도메인 모델로 변환 - 퍼센트 할인")
    void toDomain_PercentageDiscount() {
        // given
        CouponIssueEntity entity = CouponIssueEntity.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("20"))
                .maxDiscountAmount(new BigDecimal("10000"))
                .build();

        // when
        CouponIssue domain = mapper.toDomain(entity);

        // then
        assertThat(domain.getDiscountPolicy()).isNotNull();
        assertThat(domain.getDiscountPolicy().getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(domain.getDiscountPolicy().getDiscountValue()).isEqualTo(new BigDecimal("20"));
        assertThat(domain.getDiscountPolicy().getMaxDiscountAmount()).isEqualTo(new BigDecimal("10000"));
    }

    @Test
    @DisplayName("도메인 모델을 엔티티로 변환 - 전체 필드")
    void toEntity_AllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        DiscountPolicy discountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("15"))
                .minOrderAmount(new BigDecimal("20000"))
                .maxDiscountAmount(new BigDecimal("5000"))
                .build();

        CouponIssue domain = CouponIssue.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.USED)
                .reservationId("RESV-999")
                .orderId("ORD-888")
                .issuedAt(now.minusDays(2))
                .reservedAt(now.minusHours(3))
                .usedAt(now.minusHours(1))
                .expiredAt(now.plusDays(28))
                .actualDiscountAmount(new BigDecimal("3000"))
                .couponName("할인 쿠폰")
                .discountPolicy(discountPolicy)
                .build();

        // when
        CouponIssueEntity entity = mapper.toEntity(domain);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getPolicyId()).isEqualTo(100L);
        assertThat(entity.getUserId()).isEqualTo(1000L);
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(entity.getReservationId()).isEqualTo("RESV-999");
        assertThat(entity.getOrderId()).isEqualTo("ORD-888");
        assertThat(entity.getIssuedAt()).isEqualTo(now.minusDays(2));
        assertThat(entity.getReservedAt()).isEqualTo(now.minusHours(3));
        assertThat(entity.getUsedAt()).isEqualTo(now.minusHours(1));
        assertThat(entity.getExpiredAt()).isEqualTo(now.plusDays(28));
        assertThat(entity.getActualDiscountAmount()).isEqualTo(new BigDecimal("3000"));
        assertThat(entity.getCouponName()).isEqualTo("할인 쿠폰");

        // 할인 정책 필드 검증
        assertThat(entity.getDiscountType()).isEqualTo("PERCENTAGE");
        assertThat(entity.getDiscountValue()).isEqualTo(new BigDecimal("15"));
        assertThat(entity.getMinOrderAmount()).isEqualTo(new BigDecimal("20000"));
        assertThat(entity.getMaxDiscountAmount()).isEqualTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("도메인 모델을 엔티티로 변환 - 필수 필드만")
    void toEntity_RequiredFieldsOnly() {
        // given
        CouponIssue domain = CouponIssue.builder()
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("기본 쿠폰")
                .build();

        // when
        CouponIssueEntity entity = mapper.toEntity(domain);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getPolicyId()).isEqualTo(100L);
        assertThat(entity.getUserId()).isEqualTo(1000L);
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(entity.getCouponName()).isEqualTo("기본 쿠폰");
        assertThat(entity.getDiscountType()).isNull();
        assertThat(entity.getDiscountValue()).isNull();
    }

    @Test
    @DisplayName("도메인 모델을 엔티티로 변환 - null 처리")
    void toEntity_NullDomain() {
        // when
        CouponIssueEntity entity = mapper.toEntity(null);

        // then
        assertThat(entity).isNull();
    }

    @Test
    @DisplayName("엔티티 업데이트")
    void updateEntity() {
        // given
        CouponIssueEntity entity = CouponIssueEntity.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .build();

        LocalDateTime now = LocalDateTime.now();
        CouponIssue domain = CouponIssue.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.USED)
                .reservationId("RESV-NEW")
                .orderId("ORD-NEW")
                .reservedAt(now.minusHours(1))
                .usedAt(now)
                .actualDiscountAmount(new BigDecimal("7500"))
                .build();

        // when
        mapper.updateEntity(entity, domain);

        // then
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(entity.getReservationId()).isEqualTo("RESV-NEW");
        assertThat(entity.getOrderId()).isEqualTo("ORD-NEW");
        assertThat(entity.getReservedAt()).isEqualTo(now.minusHours(1));
        assertThat(entity.getUsedAt()).isEqualTo(now);
        assertThat(entity.getActualDiscountAmount()).isEqualTo(new BigDecimal("7500"));
    }

    @Test
    @DisplayName("엔티티 업데이트 - 만료 처리")
    void updateEntity_Expiration() {
        // given
        CouponIssueEntity entity = CouponIssueEntity.builder()
                .id(1L)
                .status(CouponStatus.ISSUED)
                .build();

        LocalDateTime expiredAt = LocalDateTime.now();
        CouponIssue domain = CouponIssue.builder()
                .status(CouponStatus.EXPIRED)
                .expiredAt(expiredAt)
                .build();

        // when
        mapper.updateEntity(entity, domain);

        // then
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(entity.getExpiredAt()).isEqualTo(expiredAt);
    }

    @Test
    @DisplayName("양방향 변환 일관성 검증")
    void bidirectionalConversion() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponIssue original = CouponIssue.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.RESERVED)
                .reservationId("RESV-123")
                .issuedAt(now)
                .reservedAt(now.plusMinutes(5))
                .couponName("일관성 테스트 쿠폰")
                .discountPolicy(DiscountPolicy.builder()
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(new BigDecimal("5000"))
                        .build())
                .build();

        // when
        CouponIssueEntity entity = mapper.toEntity(original);
        CouponIssue converted = mapper.toDomain(entity);

        // then
        assertThat(converted.getId()).isEqualTo(original.getId());
        assertThat(converted.getPolicyId()).isEqualTo(original.getPolicyId());
        assertThat(converted.getUserId()).isEqualTo(original.getUserId());
        assertThat(converted.getStatus()).isEqualTo(original.getStatus());
        assertThat(converted.getReservationId()).isEqualTo(original.getReservationId());
        assertThat(converted.getCouponName()).isEqualTo(original.getCouponName());
        assertThat(converted.getDiscountPolicy().getDiscountType())
                .isEqualTo(original.getDiscountPolicy().getDiscountType());
        assertThat(converted.getDiscountPolicy().getDiscountValue())
                .isEqualTo(original.getDiscountPolicy().getDiscountValue());
    }
}
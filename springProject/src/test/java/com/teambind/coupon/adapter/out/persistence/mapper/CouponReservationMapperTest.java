package com.teambind.coupon.adapter.out.persistence.mapper;

import com.teambind.coupon.adapter.out.persistence.entity.CouponReservationJpaEntity;
import com.teambind.coupon.domain.model.CouponReservation;
import com.teambind.coupon.domain.model.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponReservationMapper 단위 테스트
 */
@DisplayName("CouponReservationMapper 테스트")
class CouponReservationMapperTest {

    private CouponReservationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CouponReservationMapper();
    }

    @Test
    @DisplayName("도메인 모델을 JPA 엔티티로 변환 - 전체 필드")
    void toJpaEntity_AllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponReservation domain = CouponReservation.builder()
                .reservationId("RESV-123456")
                .couponId(100L)
                .userId(1000L)
                .orderId("ORD-789012")
                .orderAmount(new BigDecimal("50000"))
                .discountAmount(new BigDecimal("5000"))
                .reservedAt(now)
                .expiresAt(now.plusMinutes(30))
                .status(ReservationStatus.PENDING)
                .lockValue("LOCK-VALUE-123")
                .build();

        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(domain);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getReservationId()).isEqualTo("RESV-123456");
        assertThat(entity.getCouponId()).isEqualTo(100L);
        assertThat(entity.getUserId()).isEqualTo(1000L);
        assertThat(entity.getOrderId()).isEqualTo("ORD-789012");
        assertThat(entity.getOrderAmount()).isEqualTo(new BigDecimal("50000"));
        assertThat(entity.getDiscountAmount()).isEqualTo(new BigDecimal("5000"));
        assertThat(entity.getReservedAt()).isEqualTo(now);
        assertThat(entity.getExpiresAt()).isEqualTo(now.plusMinutes(30));
        assertThat(entity.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(entity.getLockValue()).isEqualTo("LOCK-VALUE-123");
    }

    @Test
    @DisplayName("도메인 모델을 JPA 엔티티로 변환 - 필수 필드만")
    void toJpaEntity_RequiredFieldsOnly() {
        // given
        CouponReservation domain = CouponReservation.builder()
                .reservationId("RESV-MIN")
                .couponId(1L)
                .userId(100L)
                .reservedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .status(ReservationStatus.PENDING)
                .build();

        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(domain);

        // then
        assertThat(entity).isNotNull();
        assertThat(entity.getReservationId()).isEqualTo("RESV-MIN");
        assertThat(entity.getCouponId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo(100L);
        assertThat(entity.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(entity.getOrderId()).isNull();
        assertThat(entity.getOrderAmount()).isNull();
        assertThat(entity.getDiscountAmount()).isNull();
        assertThat(entity.getLockValue()).isNull();
    }

    @Test
    @DisplayName("도메인 모델을 JPA 엔티티로 변환 - null 처리")
    void toJpaEntity_NullDomain() {
        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(null);

        // then
        assertThat(entity).isNull();
    }

    @Test
    @DisplayName("JPA 엔티티를 도메인 모델로 변환 - 전체 필드")
    void toDomain_AllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponReservationJpaEntity entity = CouponReservationJpaEntity.builder()
                .reservationId("RESV-999888")
                .couponId(200L)
                .userId(2000L)
                .orderId("ORD-111222")
                .orderAmount(new BigDecimal("100000"))
                .discountAmount(new BigDecimal("15000"))
                .reservedAt(now.minusMinutes(5))
                .expiresAt(now.plusMinutes(25))
                .status(ReservationStatus.CONFIRMED)
                .lockValue("LOCK-XYZ-789")
                .build();

        // when
        CouponReservation domain = mapper.toDomain(entity);

        // then
        assertThat(domain).isNotNull();
        assertThat(domain.getReservationId()).isEqualTo("RESV-999888");
        assertThat(domain.getCouponId()).isEqualTo(200L);
        assertThat(domain.getUserId()).isEqualTo(2000L);
        assertThat(domain.getOrderId()).isEqualTo("ORD-111222");
        assertThat(domain.getOrderAmount()).isEqualTo(new BigDecimal("100000"));
        assertThat(domain.getDiscountAmount()).isEqualTo(new BigDecimal("15000"));
        assertThat(domain.getReservedAt()).isEqualTo(now.minusMinutes(5));
        assertThat(domain.getExpiresAt()).isEqualTo(now.plusMinutes(25));
        assertThat(domain.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(domain.getLockValue()).isEqualTo("LOCK-XYZ-789");
    }

    @Test
    @DisplayName("JPA 엔티티를 도메인 모델로 변환 - 필수 필드만")
    void toDomain_RequiredFieldsOnly() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponReservationJpaEntity entity = CouponReservationJpaEntity.builder()
                .reservationId("RESV-BASIC")
                .couponId(10L)
                .userId(500L)
                .reservedAt(now)
                .expiresAt(now.plusMinutes(20))
                .status(ReservationStatus.PENDING)
                .build();

        // when
        CouponReservation domain = mapper.toDomain(entity);

        // then
        assertThat(domain).isNotNull();
        assertThat(domain.getReservationId()).isEqualTo("RESV-BASIC");
        assertThat(domain.getCouponId()).isEqualTo(10L);
        assertThat(domain.getUserId()).isEqualTo(500L);
        assertThat(domain.getReservedAt()).isEqualTo(now);
        assertThat(domain.getExpiresAt()).isEqualTo(now.plusMinutes(20));
        assertThat(domain.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(domain.getOrderId()).isNull();
        assertThat(domain.getOrderAmount()).isNull();
        assertThat(domain.getDiscountAmount()).isNull();
        assertThat(domain.getLockValue()).isNull();
    }

    @Test
    @DisplayName("JPA 엔티티를 도메인 모델로 변환 - null 처리")
    void toDomain_NullEntity() {
        // when
        CouponReservation domain = mapper.toDomain(null);

        // then
        assertThat(domain).isNull();
    }

    @Test
    @DisplayName("양방향 변환 일관성 검증 - 전체 필드")
    void bidirectionalConversion_AllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponReservation original = CouponReservation.builder()
                .reservationId("RESV-CONSIST")
                .couponId(999L)
                .userId(9999L)
                .orderId("ORD-CONSIST")
                .orderAmount(new BigDecimal("75000"))
                .discountAmount(new BigDecimal("7500"))
                .reservedAt(now)
                .expiresAt(now.plusMinutes(15))
                .status(ReservationStatus.PENDING)
                .lockValue("LOCK-CONSIST")
                .build();

        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(original);
        CouponReservation converted = mapper.toDomain(entity);

        // then
        assertThat(converted.getReservationId()).isEqualTo(original.getReservationId());
        assertThat(converted.getCouponId()).isEqualTo(original.getCouponId());
        assertThat(converted.getUserId()).isEqualTo(original.getUserId());
        assertThat(converted.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(converted.getOrderAmount()).isEqualTo(original.getOrderAmount());
        assertThat(converted.getDiscountAmount()).isEqualTo(original.getDiscountAmount());
        assertThat(converted.getReservedAt()).isEqualTo(original.getReservedAt());
        assertThat(converted.getExpiresAt()).isEqualTo(original.getExpiresAt());
        assertThat(converted.getStatus()).isEqualTo(original.getStatus());
        assertThat(converted.getLockValue()).isEqualTo(original.getLockValue());
    }

    @Test
    @DisplayName("양방향 변환 일관성 검증 - 필수 필드만")
    void bidirectionalConversion_RequiredFieldsOnly() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CouponReservation original = CouponReservation.builder()
                .reservationId("RESV-MINIMAL")
                .couponId(5L)
                .userId(50L)
                .reservedAt(now)
                .expiresAt(now.plusMinutes(30))
                .status(ReservationStatus.EXPIRED)
                .build();

        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(original);
        CouponReservation converted = mapper.toDomain(entity);

        // then
        assertThat(converted.getReservationId()).isEqualTo(original.getReservationId());
        assertThat(converted.getCouponId()).isEqualTo(original.getCouponId());
        assertThat(converted.getUserId()).isEqualTo(original.getUserId());
        assertThat(converted.getReservedAt()).isEqualTo(original.getReservedAt());
        assertThat(converted.getExpiresAt()).isEqualTo(original.getExpiresAt());
        assertThat(converted.getStatus()).isEqualTo(original.getStatus());
        assertThat(converted.getOrderId()).isNull();
        assertThat(converted.getOrderAmount()).isNull();
        assertThat(converted.getDiscountAmount()).isNull();
        assertThat(converted.getLockValue()).isNull();
    }

    @Test
    @DisplayName("상태별 변환 검증 - CONFIRMED")
    void conversionWithConfirmedStatus() {
        // given
        CouponReservation domain = CouponReservation.builder()
                .reservationId("RESV-CONFIRMED")
                .couponId(77L)
                .userId(777L)
                .orderId("ORD-CONFIRMED")
                .orderAmount(new BigDecimal("120000"))
                .discountAmount(new BigDecimal("12000"))
                .reservedAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .status(ReservationStatus.CONFIRMED)
                .build();

        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(domain);
        CouponReservation converted = mapper.toDomain(entity);

        // then
        assertThat(converted.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(converted.getOrderId()).isEqualTo("ORD-CONFIRMED");
        assertThat(converted.getOrderAmount()).isEqualTo(new BigDecimal("120000"));
        assertThat(converted.getDiscountAmount()).isEqualTo(new BigDecimal("12000"));
    }

    @Test
    @DisplayName("상태별 변환 검증 - CANCELLED")
    void conversionWithCancelledStatus() {
        // given
        CouponReservation domain = CouponReservation.builder()
                .reservationId("RESV-CANCELLED")
                .couponId(88L)
                .userId(888L)
                .reservedAt(LocalDateTime.now().minusMinutes(20))
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .status(ReservationStatus.CANCELLED)
                .build();

        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(domain);
        CouponReservation converted = mapper.toDomain(entity);

        // then
        assertThat(converted.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(converted.getOrderId()).isNull();
        assertThat(converted.getOrderAmount()).isNull();
        assertThat(converted.getDiscountAmount()).isNull();
    }

    @Test
    @DisplayName("금액 정확도 검증")
    void amountPrecisionVerification() {
        // given
        CouponReservation domain = CouponReservation.builder()
                .reservationId("RESV-AMOUNT")
                .couponId(99L)
                .userId(999L)
                .orderAmount(new BigDecimal("123456.78"))
                .discountAmount(new BigDecimal("9876.54"))
                .reservedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .status(ReservationStatus.PENDING)
                .build();

        // when
        CouponReservationJpaEntity entity = mapper.toJpaEntity(domain);
        CouponReservation converted = mapper.toDomain(entity);

        // then
        assertThat(converted.getOrderAmount()).isEqualByComparingTo(new BigDecimal("123456.78"));
        assertThat(converted.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("9876.54"));
    }
}
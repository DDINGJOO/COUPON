package com.teambind.coupon.adapter.out.persistence.entity;

import com.teambind.coupon.domain.model.CouponStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssueEntity JPA 엔티티 단위 테스트
 */
@DisplayName("CouponIssueEntity 테스트")
class CouponIssueEntityTest {

    private CouponIssueEntity entity;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        entity = CouponIssueEntity.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(now)
                .expiresAt(now.plusDays(30))
                .couponName("테스트 쿠폰")
                .discountType("PERCENTAGE")
                .discountValue(new BigDecimal("10"))
                .minOrderAmount(new BigDecimal("10000"))
                .maxDiscountAmount(new BigDecimal("5000"))
                .build();
    }

    @Test
    @DisplayName("엔티티 생성 및 필드 검증")
    void createEntity() {
        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getPolicyId()).isEqualTo(100L);
        assertThat(entity.getUserId()).isEqualTo(1000L);
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(entity.getIssuedAt()).isEqualTo(now);
        assertThat(entity.getExpiresAt()).isEqualTo(now.plusDays(30));
        assertThat(entity.getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(entity.getDiscountType()).isEqualTo("PERCENTAGE");
        assertThat(entity.getDiscountValue()).isEqualByComparingTo("10");
        assertThat(entity.getMinOrderAmount()).isEqualByComparingTo("10000");
        assertThat(entity.getMaxDiscountAmount()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("예약 상태 설정")
    void reservationState() {
        String reservationId = "RES-001";
        LocalDateTime reservedTime = LocalDateTime.now();

        entity.setStatus(CouponStatus.RESERVED);
        entity.setReservationId(reservationId);
        entity.setReservedAt(reservedTime);

        assertThat(entity.getStatus()).isEqualTo(CouponStatus.RESERVED);
        assertThat(entity.getReservationId()).isEqualTo(reservationId);
        assertThat(entity.getReservedAt()).isEqualTo(reservedTime);
    }

    @Test
    @DisplayName("사용 상태 설정")
    void usedState() {
        String orderId = "ORD-001";
        LocalDateTime usedTime = LocalDateTime.now();
        BigDecimal actualDiscount = new BigDecimal("3000");

        entity.setStatus(CouponStatus.USED);
        entity.setOrderId(orderId);
        entity.setUsedAt(usedTime);
        entity.setActualDiscountAmount(actualDiscount);

        assertThat(entity.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(entity.getOrderId()).isEqualTo(orderId);
        assertThat(entity.getUsedAt()).isEqualTo(usedTime);
        assertThat(entity.getActualDiscountAmount()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("만료 상태 설정")
    void expiredState() {
        LocalDateTime expiredTime = LocalDateTime.now();

        entity.setStatus(CouponStatus.EXPIRED);
        entity.setExpiredAt(expiredTime);

        assertThat(entity.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(entity.getExpiredAt()).isEqualTo(expiredTime);
    }

    @Test
    @DisplayName("취소 상태 설정")
    void cancelledState() {
        entity.setStatus(CouponStatus.CANCELLED);

        assertThat(entity.getStatus()).isEqualTo(CouponStatus.CANCELLED);
    }

    @Test
    @DisplayName("Optimistic Locking 버전 관리")
    void versionManagement() {
        entity.setVersion(1L);
        assertThat(entity.getVersion()).isEqualTo(1L);

        // 버전 업데이트
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
    @DisplayName("정책 엔티티 연관관계 설정")
    void policyRelation() {
        CouponPolicyEntity policy = CouponPolicyEntity.builder()
                .id(100L)
                .couponName("연관 정책")
                .build();

        entity.setPolicy(policy);

        assertThat(entity.getPolicy()).isNotNull();
        assertThat(entity.getPolicy().getId()).isEqualTo(100L);
        assertThat(entity.getPolicy().getCouponName()).isEqualTo("연관 정책");
    }

    @Test
    @DisplayName("빌더 패턴 - 필수 필드만")
    void builderWithRequiredFields() {
        CouponIssueEntity minimal = CouponIssueEntity.builder()
                .id(2L)
                .policyId(200L)
                .userId(2000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(now)
                .build();

        assertThat(minimal).isNotNull();
        assertThat(minimal.getId()).isEqualTo(2L);
        assertThat(minimal.getPolicyId()).isEqualTo(200L);
        assertThat(minimal.getUserId()).isEqualTo(2000L);
        assertThat(minimal.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(minimal.getIssuedAt()).isEqualTo(now);
        assertThat(minimal.getReservationId()).isNull();
        assertThat(minimal.getOrderId()).isNull();
    }

    @Test
    @DisplayName("상태 전이 시나리오")
    void stateTransitionScenario() {
        // 발급 상태
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.ISSUED);

        // 예약 상태로 전이
        entity.setStatus(CouponStatus.RESERVED);
        entity.setReservationId("RES-002");
        entity.setReservedAt(LocalDateTime.now());
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.RESERVED);
        assertThat(entity.getReservationId()).isNotNull();

        // 사용 완료로 전이
        entity.setStatus(CouponStatus.USED);
        entity.setOrderId("ORD-002");
        entity.setUsedAt(LocalDateTime.now());
        entity.setActualDiscountAmount(new BigDecimal("4000"));
        assertThat(entity.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(entity.getOrderId()).isNotNull();
        assertThat(entity.getActualDiscountAmount()).isNotNull();
    }
}
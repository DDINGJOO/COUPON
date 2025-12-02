package com.teambind.coupon.integration;

import com.teambind.coupon.IntegrationTestBase;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.request.CouponConfirmRequest;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 사용 프로세스 통합 테스트
 */
class CouponUsageIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CouponPolicyRepository couponPolicyRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    private CouponPolicyEntity testPolicy;
    private CouponIssueEntity testCoupon;
    private Long testUserId = 12345L;

    @BeforeEach
    protected void setUp() {
        // 테스트용 쿠폰 정책 생성
        testPolicy = CouponPolicyEntity.builder()
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(10000))
                .minOrderAmount(BigDecimal.valueOf(50000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .validFrom(1)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        testPolicy = couponPolicyRepository.save(testPolicy);

        // 테스트용 쿠폰 발급
        testCoupon = CouponIssueEntity.builder()
                .policyId(testPolicy.getId())
                .userId(testUserId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        testCoupon = couponIssueRepository.save(testCoupon);
    }

    @Test
    @DisplayName("쿠폰 예약 성공")
    void reserveCoupon_Success() throws Exception {
        // given
        String reservationId = "RESV-" + UUID.randomUUID().toString();
        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId(reservationId)
                .userId(testUserId)
                .couponId(testCoupon.getId())
                .orderAmount(BigDecimal.valueOf(100000))
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.data.discountAmount").value(10000))
                .andExpect(jsonPath("$.data.finalAmount").value(90000));

        // 상태 확인
        CouponIssueEntity reserved = couponIssueRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(reserved.getStatus()).isEqualTo(CouponStatus.RESERVED);
        assertThat(reserved.getReservationId()).isEqualTo(reservationId);
    }

    @Test
    @DisplayName("최소 주문 금액 미충족")
    void reserveCoupon_MinimumOrderAmountNotMet() throws Exception {
        // given
        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-" + UUID.randomUUID().toString())
                .userId(testUserId)
                .couponId(testCoupon.getId())
                .orderAmount(BigDecimal.valueOf(30000)) // 최소 주문 금액 미달
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MINIMUM_ORDER_NOT_MET"));

        // 상태 확인
        CouponIssueEntity notReserved = couponIssueRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(notReserved.getStatus()).isEqualTo(CouponStatus.ISSUED);
    }

    @Test
    @DisplayName("쿠폰 사용 확정")
    void confirmCoupon_Success() throws Exception {
        // given
        String reservationId = "RESV-" + UUID.randomUUID().toString();

        // 먼저 예약
        testCoupon.setStatus(CouponStatus.RESERVED);
        testCoupon.setReservationId(reservationId);
        testCoupon.setReservedAt(LocalDateTime.now());
        couponIssueRepository.save(testCoupon);

        CouponConfirmRequest request = CouponConfirmRequest.builder()
                .reservationId(reservationId)
                .orderId("ORDER-" + UUID.randomUUID().toString())
                .actualDiscountAmount(BigDecimal.valueOf(10000))
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("USED"));

        // 상태 확인
        CouponIssueEntity used = couponIssueRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(used.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("예약 취소")
    void cancelReservation_Success() throws Exception {
        // given
        String reservationId = "RESV-" + UUID.randomUUID().toString();

        // 먼저 예약
        testCoupon.setStatus(CouponStatus.RESERVED);
        testCoupon.setReservationId(reservationId);
        testCoupon.setReservedAt(LocalDateTime.now());
        couponIssueRepository.save(testCoupon);

        // when & then
        mockMvc.perform(delete("/api/v1/coupons/apply/{reservationId}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 상태 확인
        CouponIssueEntity cancelled = couponIssueRepository.findByReservationId(reservationId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(cancelled.getReservationId()).isNull();
    }

    @Test
    @DisplayName("이미 사용된 쿠폰 예약 시도")
    void reserveUsedCoupon_Fail() throws Exception {
        // given
        testCoupon.setStatus(CouponStatus.USED);
        testCoupon.setUsedAt(LocalDateTime.now());
        couponIssueRepository.save(testCoupon);

        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-" + UUID.randomUUID().toString())
                .userId(testUserId)
                .couponId(testCoupon.getId())
                .orderAmount(BigDecimal.valueOf(100000))
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ALREADY_USED"));
    }

    @Test
    @DisplayName("만료된 쿠폰 사용 시도")
    void useExpiredCoupon_Fail() throws Exception {
        // given
        testCoupon.setExpiresAt(LocalDateTime.now().minusDays(1)); // 어제 만료
        couponIssueRepository.save(testCoupon);

        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-" + UUID.randomUUID().toString())
                .userId(testUserId)
                .couponId(testCoupon.getId())
                .orderAmount(BigDecimal.valueOf(100000))
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COUPON_EXPIRED"));
    }

    @Test
    @DisplayName("다른 사용자의 쿠폰 사용 시도")
    void useOtherUsersCoupon_Fail() throws Exception {
        // given
        Long otherUserId = 99999L;
        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-" + UUID.randomUUID().toString())
                .userId(otherUserId) // 다른 사용자 ID
                .couponId(testCoupon.getId())
                .orderAmount(BigDecimal.valueOf(100000))
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED_COUPON_ACCESS"));
    }

    @Test
    @DisplayName("퍼센트 할인 쿠폰 적용")
    void applyPercentageDiscount() throws Exception {
        // given
        CouponPolicyEntity percentPolicy = CouponPolicyEntity.builder()
                .couponName("10% 할인 쿠폰")
                .couponCode("PERCENT10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .maxDiscountAmount(BigDecimal.valueOf(5000)) // 최대 할인 5000원
                .minOrderAmount(BigDecimal.valueOf(10000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .isActive(true)
                .build();

        percentPolicy = couponPolicyRepository.save(percentPolicy);

        CouponIssueEntity percentCoupon = CouponIssueEntity.builder()
                .policyId(percentPolicy.getId())
                .userId(testUserId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        percentCoupon = couponIssueRepository.save(percentCoupon);

        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-" + UUID.randomUUID().toString())
                .userId(testUserId)
                .couponId(percentCoupon.getId())
                .orderAmount(BigDecimal.valueOf(100000)) // 10% = 10000원이지만 최대 5000원
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(5000)) // 최대 할인 적용
                .andExpect(jsonPath("$.data.finalAmount").value(95000));
    }
}
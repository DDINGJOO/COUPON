package com.teambind.coupon.integration;

import com.teambind.coupon.IntegrationTestBase;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.application.dto.request.CouponIssueRequest;
import com.teambind.coupon.application.dto.request.BatchIssueRequest;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * 쿠폰 발급 통합 테스트
 */
class CouponIssueIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CouponPolicyRepository couponPolicyRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    private CouponPolicyEntity testPolicy;

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
    }

    @Test
    @DisplayName("쿠폰 코드로 정상 발급")
    void issueByCode_Success() throws Exception {
        // given
        CouponIssueRequest request = CouponIssueRequest.builder()
                .couponCode("TEST2024")
                .userId(12345L)
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponName").value("테스트 쿠폰"))
                .andExpect(jsonPath("$.data.discountValue").value(10000))
                .andExpect(jsonPath("$.data.status").value("ISSUED"));

        // 발급 확인
        List<CouponIssueEntity> issues = couponIssueRepository.findByUserId(12345L);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getStatus()).isEqualTo(CouponStatus.ISSUED);
    }

    @Test
    @DisplayName("중복 발급 방지")
    void preventDuplicateIssue() throws Exception {
        // given
        CouponIssueRequest request = CouponIssueRequest.builder()
                .couponCode("TEST2024")
                .userId(12345L)
                .build();

        // 첫 번째 발급
        mockMvc.perform(post("/api/v1/coupons/issue/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // when & then - 두 번째 발급 시도
        mockMvc.perform(post("/api/v1/coupons/issue/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ALREADY_ISSUED"));

        // 발급 개수 확인
        List<CouponIssueEntity> issues = couponIssueRepository.findByUserId(12345L);
        assertThat(issues).hasSize(1);
    }

    @Test
    @DisplayName("배치 발급 성공")
    void batchIssue_Success() throws Exception {
        // given
        BatchIssueRequest request = BatchIssueRequest.builder()
                .policyId(testPolicy.getId())
                .userIds(Arrays.asList(100L, 101L, 102L))
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRequested").value(3))
                .andExpect(jsonPath("$.data.successCount").value(3))
                .andExpect(jsonPath("$.data.failureCount").value(0));

        // 발급 확인
        List<CouponIssueEntity> issues = couponIssueRepository.findByPolicyId(testPolicy.getId());
        assertThat(issues).hasSize(3);
        assertThat(issues).allMatch(issue -> issue.getStatus() == CouponStatus.ISSUED);
    }

    @Test
    @DisplayName("선착순(FCFS) 발급 - 재고 있을 때")
    void fcfsIssue_WithStock() throws Exception {
        // given
        CouponPolicyEntity fcfsPolicy = CouponPolicyEntity.builder()
                .couponName("선착순 쿠폰")
                .couponCode("FCFS2024")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .distributionType(DistributionType.EVENT)
                .maxIssueCount(10)
                .validFrom(1)
                .validFrom(7)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusHours(1))
                .isActive(true)
                .build();

        fcfsPolicy = couponPolicyRepository.save(fcfsPolicy);

        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/fcfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CouponIssueRequest.builder()
                                        .policyId(fcfsPolicy.getId())
                                        .userId(999L)
                                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.remainingStock").value(9));
    }

    @Test
    @DisplayName("선착순(FCFS) 발급 - 재고 소진")
    void fcfsIssue_StockExhausted() throws Exception {
        // given
        CouponPolicyEntity fcfsPolicy = CouponPolicyEntity.builder()
                .couponName("선착순 쿠폰")
                .couponCode("FCFS2024")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .distributionType(DistributionType.EVENT)
                .validFrom(1)
                .validFrom(7)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusHours(1))
                .isActive(true)
                .build();

        fcfsPolicy = couponPolicyRepository.save(fcfsPolicy);

        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/fcfs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CouponIssueRequest.builder()
                                        .policyId(fcfsPolicy.getId())
                                        .userId(999L)
                                        .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("STOCK_EXHAUSTED"));
    }

    @Test
    @DisplayName("유효하지 않은 쿠폰 코드")
    void issueWithInvalidCode() throws Exception {
        // given
        CouponIssueRequest request = CouponIssueRequest.builder()
                .couponCode("INVALID_CODE")
                .userId(12345L)
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("발급 기간 종료된 쿠폰")
    void issueExpiredPolicy() throws Exception {
        // given
        CouponPolicyEntity expiredPolicy = CouponPolicyEntity.builder()
                .couponName("만료된 쿠폰")
                .couponCode("EXPIRED2024")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .validFrom(LocalDateTime.now().minusDays(30))
                .validUntil(LocalDateTime.now().minusDays(1)) // 어제 종료
                .isActive(true)
                .build();

        couponPolicyRepository.save(expiredPolicy);

        CouponIssueRequest request = CouponIssueRequest.builder()
                .couponCode("EXPIRED2024")
                .userId(12345L)
                .build();

        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ISSUE_PERIOD_ENDED"));
    }
}
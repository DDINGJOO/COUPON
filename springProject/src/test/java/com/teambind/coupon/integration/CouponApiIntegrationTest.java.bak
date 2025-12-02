package com.teambind.coupon.integration;

import com.teambind.coupon.IntegrationTestBase;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * API 엔드포인트 통합 테스트
 */
class CouponApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CouponPolicyRepository couponPolicyRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    private Long testUserId = 12345L;

    @BeforeEach
    protected void setUp() {
        // 테스트 데이터 초기화
        couponIssueRepository.deleteAll();
        couponPolicyRepository.deleteAll();
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 목록 조회")
    void getAvailableCoupons() throws Exception {
        // given
        CouponPolicyEntity policy1 = createPolicy("쿠폰1", "COUPON1", BigDecimal.valueOf(5000));
        CouponPolicyEntity policy2 = createPolicy("쿠폰2", "COUPON2", BigDecimal.valueOf(10000));

        // 사용 가능한 쿠폰
        createIssuedCoupon(policy1.getId(), testUserId, CouponStatus.ISSUED);
        createIssuedCoupon(policy2.getId(), testUserId, CouponStatus.ISSUED);

        // 사용된 쿠폰 (조회되면 안됨)
        CouponIssueEntity usedCoupon = createIssuedCoupon(policy1.getId(), testUserId, CouponStatus.USED);
        usedCoupon.setUsedAt(LocalDateTime.now());
        couponIssueRepository.save(usedCoupon);

        // when & then
        mockMvc.perform(get("/api/v1/coupons/available")
                        .param("userId", testUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.coupons", hasSize(2)))
                .andExpect(jsonPath("$.data.coupons[*].status", everyItem(equalTo("ISSUED"))));
    }

    @Test
    @DisplayName("사용자 쿠폰 이력 조회 - 페이징")
    void getUserCouponHistory() throws Exception {
        // given
        CouponPolicyEntity policy = createPolicy("테스트 쿠폰", "TEST", BigDecimal.valueOf(5000));

        // 15개 쿠폰 생성 (페이징 테스트)
        IntStream.range(0, 15).forEach(i -> {
            CouponIssueEntity coupon = createIssuedCoupon(policy.getId(), testUserId,
                    i % 3 == 0 ? CouponStatus.USED : CouponStatus.ISSUED);
            if (coupon.getStatus() == CouponStatus.USED) {
                coupon.setUsedAt(LocalDateTime.now());
                couponIssueRepository.save(coupon);
            }
        });

        // when & then - 첫 페이지
        mockMvc.perform(get("/api/v1/coupons/history")
                        .param("userId", testUserId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(10)))
                .andExpect(jsonPath("$.data.totalElements").value(15))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.number").value(0));

        // 두 번째 페이지
        mockMvc.perform(get("/api/v1/coupons/history")
                        .param("userId", testUserId.toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.number").value(1));
    }

    @Test
    @DisplayName("쿠폰 정책 상세 조회")
    void getCouponPolicyDetail() throws Exception {
        // given
        CouponPolicyEntity policy = CouponPolicyEntity.builder()
                .couponName("상세 조회 테스트")
                .couponCode("DETAIL")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(15))
                .maxDiscountAmount(BigDecimal.valueOf(10000))
                .minOrderAmount(BigDecimal.valueOf(30000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(1000)
                .maxIssueCount(250)
                .validFrom(3)
                .validFrom(LocalDateTime.now().minusDays(10))
                .validUntil(LocalDateTime.now().plusDays(20))
                .isActive(true)
                .build();

        policy = couponPolicyRepository.save(policy);

        // when & then
        mockMvc.perform(get("/api/v1/coupons/policies/{policyId}", policy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.couponName").value("상세 조회 테스트"))
                .andExpect(jsonPath("$.data.couponCode").value("DETAIL"))
                .andExpect(jsonPath("$.data.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.data.discountValue").value(15))
                .andExpect(jsonPath("$.data.maxDiscountAmount").value(10000))
                .andExpect(jsonPath("$.data.remainingCount").value(750))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @DisplayName("실시간 통계 조회")
    void getRealtimeStatistics() throws Exception {
        // given
        CouponPolicyEntity policy = createPolicy("통계 테스트", "STATS", BigDecimal.valueOf(5000));

        // 쿠폰 발급 및 사용 데이터 생성
        IntStream.range(0, 10).forEach(i -> {
            CouponIssueEntity coupon = createIssuedCoupon(policy.getId(), 100L + i, CouponStatus.ISSUED);
            if (i < 3) {
                coupon.setStatus(CouponStatus.USED);
                coupon.setUsedAt(LocalDateTime.now());
                couponIssueRepository.save(coupon);
            } else if (i < 5) {
                coupon.setStatus(CouponStatus.RESERVED);
                coupon.setReservationId("RESV-" + i);
                coupon.setReservedAt(LocalDateTime.now());
                couponIssueRepository.save(coupon);
            }
        });

        // when & then
        mockMvc.perform(get("/api/v1/coupons/statistics/realtime/{policyId}", policy.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyId").value(policy.getId()))
                .andExpect(jsonPath("$.data.maxIssueCount").value(10))
                .andExpect(jsonPath("$.data.usedCount").value(3))
                .andExpect(jsonPath("$.data.reservedCount").value(2))
                .andExpect(jsonPath("$.data.availableCount").value(90)) // maxIssueCount(100) - maxIssueCount(10)
                .andExpect(jsonPath("$.data.usageRate").value(30.0)); // 3/10 * 100
    }

    @Test
    @DisplayName("유효성 검증 - 필수 파라미터 누락")
    void validationError_MissingParameter() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")) // 빈 요청
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("유효성 검증 - 잘못된 데이터 타입")
    void validationError_InvalidDataType() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/coupons/issue/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponCode\": \"TEST\", \"userId\": \"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("존재하지 않는 리소스 조회")
    void resourceNotFound() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/coupons/policies/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    @DisplayName("관리자 API - 재고 동기화")
    void adminApi_StockSync() throws Exception {
        // given
        CouponPolicyEntity policy = createPolicy("재고 동기화 테스트", "SYNC", BigDecimal.valueOf(5000));

        // when & then
        mockMvc.perform(post("/api/v1/admin/coupons/stock/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\": " + policy.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policyId").value(policy.getId()))
                .andExpect(jsonPath("$.data.syncStatus").value("SUCCESS"));
    }

    @Test
    @DisplayName("관리자 API - 만료 처리")
    void adminApi_ExpireProcess() throws Exception {
        // given
        CouponPolicyEntity policy = createPolicy("만료 테스트", "EXPIRE", BigDecimal.valueOf(5000));

        // 만료된 쿠폰 생성
        CouponIssueEntity expiredCoupon = CouponIssueEntity.builder()
                .policyId(policy.getId())
                .userId(testUserId)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(31))
                .expiresAt(LocalDateTime.now().minusDays(1)) // 어제 만료
                .build();
        couponIssueRepository.save(expiredCoupon);

        // when & then
        mockMvc.perform(post("/api/v1/admin/coupons/expire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchSize\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedCount").value(greaterThan(0)));
    }

    // Helper methods
    private CouponPolicyEntity createPolicy(String name, String code, BigDecimal discountValue) {
        CouponPolicyEntity policy = CouponPolicyEntity.builder()
                .couponName(name)
                .couponCode(code)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(discountValue)
                .minOrderAmount(BigDecimal.valueOf(10000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .validFrom(3)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        return couponPolicyRepository.save(policy);
    }

    private CouponIssueEntity createIssuedCoupon(Long policyId, Long userId, CouponStatus status) {
        CouponIssueEntity coupon = CouponIssueEntity.builder()
                .policyId(policyId)
                .userId(userId)
                .status(status)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        return couponIssueRepository.save(coupon);
    }
}
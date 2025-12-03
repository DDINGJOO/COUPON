package com.teambind.coupon.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.coupon.adapter.in.web.dto.CouponQueryRequest;
import com.teambind.coupon.adapter.in.web.dto.CouponQueryResponse;
import com.teambind.coupon.application.service.CouponQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CouponQueryController 단위 테스트
 * MockitoExtension을 사용한 순수 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponQueryController 테스트")
class CouponQueryControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private CouponQueryService couponQueryService;

    @InjectMocks
    private CouponQueryController couponQueryController;

    private Long userId;
    private CouponQueryResponse mockResponse;

    @BeforeEach
    void setUp() {
        // MockMvc 설정
        mockMvc = MockMvcBuilders.standaloneSetup(couponQueryController).build();
        objectMapper = new ObjectMapper();

        userId = 100L;

        // Mock 응답 데이터 생성
        CouponQueryResponse.CouponItem item1 = CouponQueryResponse.CouponItem.builder()
                .couponIssueId(1L)
                .userId(userId)
                .couponName("10% 할인 쿠폰")
                .status("ISSUED")
                .discountType("PERCENTAGE")
                .isAvailable(true)
                .remainingDays(30L)
                .build();

        CouponQueryResponse.CouponItem item2 = CouponQueryResponse.CouponItem.builder()
                .couponIssueId(2L)
                .userId(userId)
                .couponName("5000원 할인 쿠폰")
                .status("ISSUED")
                .discountType("FIXED")
                .isAvailable(true)
                .remainingDays(15L)
                .build();

        mockResponse = CouponQueryResponse.builder()
                .data(Arrays.asList(item1, item2))
                .nextCursor(2L)
                .hasNext(true)
                .count(2)
                .build();
    }

    @Nested
    @DisplayName("GET /api/coupons/users/{userId}")
    class QueryUserCoupons {

        @Test
        @DisplayName("파라미터 없이 조회 성공")
        void queryWithoutParameters() throws Exception {
            // given
            when(couponQueryService.queryUserCoupons(eq(userId), any(CouponQueryRequest.class)))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].couponIssueId").value(1))
                    .andExpect(jsonPath("$.nextCursor").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.count").value(2));
        }

        @Test
        @DisplayName("상태 필터와 함께 조회")
        void queryWithStatusFilter() throws Exception {
            // given
            when(couponQueryService.queryUserCoupons(eq(userId), any(CouponQueryRequest.class)))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId)
                            .param("status", "AVAILABLE"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].status").value("ISSUED"));
        }

        @Test
        @DisplayName("상품ID 필터와 함께 조회")
        void queryWithProductIds() throws Exception {
            // given
            when(couponQueryService.queryUserCoupons(eq(userId), any(CouponQueryRequest.class)))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId)
                            .param("productIds", "1,2,3"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @DisplayName("커서와 limit 파라미터로 조회")
        void queryWithCursorAndLimit() throws Exception {
            // given
            when(couponQueryService.queryUserCoupons(eq(userId), any(CouponQueryRequest.class)))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId)
                            .param("cursor", "10")
                            .param("limit", "20"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextCursor").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true));
        }

        @Test
        @DisplayName("모든 파라미터와 함께 조회")
        void queryWithAllParameters() throws Exception {
            // given
            when(couponQueryService.queryUserCoupons(eq(userId), any(CouponQueryRequest.class)))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId)
                            .param("status", "AVAILABLE")
                            .param("productIds", "10,20,30")
                            .param("cursor", "100")
                            .param("limit", "50"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.count").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/coupons/users/{userId}/expiring")
    class QueryExpiringCoupons {

        @Test
        @DisplayName("만료 임박 쿠폰 조회 - 기본값")
        void queryExpiringDefault() throws Exception {
            // given
            when(couponQueryService.queryExpiringCoupons(userId, 7, 10))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}/expiring", userId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].remainingDays").exists());
        }

        @Test
        @DisplayName("만료 임박 쿠폰 조회 - 커스텀 파라미터")
        void queryExpiringWithParams() throws Exception {
            // given
            when(couponQueryService.queryExpiringCoupons(userId, 3, 20))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}/expiring", userId)
                            .param("days", "3")
                            .param("limit", "20"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/coupons/users/{userId}/statistics")
    class GetCouponStatistics {

        @Test
        @DisplayName("쿠폰 통계 조회 성공")
        void getCouponStatisticsSuccess() throws Exception {
            // given
            var statistics = com.teambind.coupon.application.port.in.QueryUserCouponsUseCase.CouponStatistics.builder()
                    .totalCoupons(100L)
                    .availableCoupons(30L)
                    .usedCoupons(50L)
                    .expiredCoupons(20L)
                    .expiringCoupons(5L)
                    .build();
            when(couponQueryService.getCouponStatistics(userId))
                    .thenReturn(statistics);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}/statistics", userId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCoupons").value(100))
                    .andExpect(jsonPath("$.availableCoupons").value(30))
                    .andExpect(jsonPath("$.usedCoupons").value(50))
                    .andExpect(jsonPath("$.expiredCoupons").value(20))
                    .andExpect(jsonPath("$.expiringCoupons").value(5));
        }
    }

    @Nested
    @DisplayName("POST /api/coupons/users/{userId}/query")
    class QueryUserCouponsPost {

        @Test
        @DisplayName("POST 방식으로 복잡한 필터 조회")
        void queryWithComplexFilters() throws Exception {
            // given
            CouponQueryRequest request = CouponQueryRequest.builder()
                    .status(CouponQueryRequest.CouponStatusFilter.AVAILABLE)
                    .productIds(Arrays.asList(1L, 2L, 3L))
                    .cursor(null)
                    .limit(20)
                    .build();

            when(couponQueryService.queryUserCoupons(eq(userId), any(CouponQueryRequest.class)))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(post("/api/coupons/users/{userId}/query", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].couponName").exists());
        }

        @Test
        @DisplayName("잘못된 요청 데이터로 실패")
        void queryWithInvalidRequest() throws Exception {
            // given
            String invalidJson = "{\"limit\": -1}";  // 잘못된 limit 값

            // when & then
            mockMvc.perform(post("/api/coupons/users/{userId}/query", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }
}
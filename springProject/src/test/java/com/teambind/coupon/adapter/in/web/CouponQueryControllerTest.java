package com.teambind.coupon.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.coupon.adapter.in.web.dto.CouponQueryRequest;
import com.teambind.coupon.adapter.in.web.dto.CouponQueryResponse;
import com.teambind.coupon.application.port.in.QueryUserCouponsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 */
@WebMvcTest(CouponQueryController.class)
@DisplayName("CouponQueryController 테스트")
class CouponQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QueryUserCouponsUseCase queryUserCouponsUseCase;

    private Long userId;
    private CouponQueryResponse mockResponse;

    @BeforeEach
    void setUp() {
        userId = 100L;
        mockResponse = CouponQueryResponse.builder()
                .data(List.of(
                        CouponQueryResponse.CouponItem.builder()
                                .couponIssueId(1L)
                                .userId(userId)
                                .couponName("테스트 쿠폰")
                                .status("ISSUED")
                                .isAvailable(true)
                                .build()
                ))
                .nextCursor(1L)
                .hasNext(true)
                .count(1)
                .build();
    }

    @Nested
    @DisplayName("GET /api/coupons/users/{userId}")
    class QueryUserCoupons {

        @Test
        @DisplayName("파라미터 없이 조회 성공")
        void queryWithoutParameters() throws Exception {
            // given
            when(queryUserCouponsUseCase.queryUserCoupons(eq(userId), any()))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].couponIssueId").value(1))
                    .andExpect(jsonPath("$.nextCursor").value(1))
                    .andExpect(jsonPath("$.hasNext").value(true));
        }

        @Test
        @DisplayName("상태 필터와 함께 조회")
        void queryWithStatusFilter() throws Exception {
            // given
            when(queryUserCouponsUseCase.queryUserCoupons(eq(userId), any()))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId)
                            .param("status", "AVAILABLE"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @DisplayName("상품ID 필터와 함께 조회")
        void queryWithProductIds() throws Exception {
            // given
            when(queryUserCouponsUseCase.queryUserCoupons(eq(userId), any()))
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
            when(queryUserCouponsUseCase.queryUserCoupons(eq(userId), any()))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId)
                            .param("cursor", "10")
                            .param("limit", "20"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextCursor").exists())
                    .andExpect(jsonPath("$.hasNext").exists());
        }

        @Test
        @DisplayName("모든 파라미터와 함께 조회")
        void queryWithAllParameters() throws Exception {
            // given
            when(queryUserCouponsUseCase.queryUserCoupons(eq(userId), any()))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}", userId)
                            .param("status", "UNUSED")
                            .param("productIds", "10,20,30")
                            .param("cursor", "100")
                            .param("limit", "50"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/coupons/users/{userId}/expiring")
    class QueryExpiringCoupons {

        @Test
        @DisplayName("만료 임박 쿠폰 조회 - 기본값")
        void queryExpiringDefault() throws Exception {
            // given
            when(queryUserCouponsUseCase.queryExpiringCoupons(userId, 7, 10))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}/expiring", userId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("만료 임박 쿠폰 조회 - 커스텀 파라미터")
        void queryExpiringWithParameters() throws Exception {
            // given
            when(queryUserCouponsUseCase.queryExpiringCoupons(userId, 3, 5))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/coupons/users/{userId}/expiring", userId)
                            .param("days", "3")
                            .param("limit", "5"))
                    .andDo(print())
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/coupons/users/{userId}/statistics")
    class GetCouponStatistics {

        @Test
        @DisplayName("쿠폰 통계 조회 성공")
        void getStatisticsSuccess() throws Exception {
            // given
            QueryUserCouponsUseCase.CouponStatistics statistics =
                    QueryUserCouponsUseCase.CouponStatistics.builder()
                            .totalCoupons(100L)
                            .availableCoupons(30L)
                            .usedCoupons(50L)
                            .expiredCoupons(20L)
                            .expiringCoupons(5L)
                            .build();

            when(queryUserCouponsUseCase.getCouponStatistics(userId))
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
    class QueryUserCouponsWithBody {

        @Test
        @DisplayName("POST 방식으로 복잡한 필터 조회")
        void queryWithRequestBody() throws Exception {
            // given
            CouponQueryRequest request = CouponQueryRequest.builder()
                    .status(CouponQueryRequest.CouponStatusFilter.AVAILABLE)
                    .productIds(Arrays.asList(1L, 2L, 3L))
                    .cursor(null)
                    .limit(20)
                    .build();

            when(queryUserCouponsUseCase.queryUserCoupons(eq(userId), any()))
                    .thenReturn(mockResponse);

            // when & then
            mockMvc.perform(post("/api/coupons/users/{userId}/query", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.count").value(1));
        }

        @Test
        @DisplayName("잘못된 요청 데이터로 실패")
        void queryWithInvalidRequest() throws Exception {
            // given
            String invalidRequest = """
                    {
                        "limit": 200
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/coupons/users/{userId}/query", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }
}
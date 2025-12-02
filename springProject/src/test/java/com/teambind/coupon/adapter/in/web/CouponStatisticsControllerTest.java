package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase;
import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 쿠폰 통계 API 컨트롤러 테스트
 */
@WebMvcTest(CouponStatisticsController.class)
@ActiveProfiles("test")
@DisplayName("쿠폰 통계 API 테스트")
@org.junit.jupiter.api.Disabled("테스트 환경 설정 필요")
class CouponStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetCouponStatisticsUseCase getCouponStatisticsUseCase;

    private RealtimeStatistics mockRealtimeStats;
    private GlobalStatistics mockGlobalStats;
    private UserStatistics mockUserStats;

    @BeforeEach
    void setUp() {
        mockRealtimeStats = RealtimeStatistics.builder()
                .policyId(1L)
                .policyName("테스트 쿠폰")
                .maxIssueCount(1000)
                .currentIssueCount(500)
                .usedCount(200)
                .reservedCount(50)
                .availableCount(500)
                .usageRate(40.0)
                .lastIssuedAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .build();

        mockGlobalStats = GlobalStatistics.builder()
                .totalPolicies(10)
                .totalIssuedCoupons(5000L)
                .totalUsedCoupons(2000L)
                .totalReservedCoupons(500L)
                .totalExpiredCoupons(100L)
                .overallUsageRate(40.0)
                .statusDistribution(Map.of(
                        "ISSUED", 2400L,
                        "USED", 2000L,
                        "RESERVED", 500L,
                        "EXPIRED", 100L
                ))
                .typeDistribution(Map.of(
                        "CODE", 3000L,
                        "DIRECT", 2000L
                ))
                .build();

        mockUserStats = UserStatistics.builder()
                .userId(123L)
                .totalCoupons(10)
                .availableCoupons(5)
                .usedCoupons(3)
                .expiredCoupons(2)
                .firstIssuedAt(LocalDateTime.now().minusDays(30))
                .lastUsedAt(LocalDateTime.now().minusDays(1))
                .couponsByStatus(Map.of(
                        "ISSUED", 5,
                        "USED", 3,
                        "EXPIRED", 2
                ))
                .build();
    }

    @Test
    @DisplayName("실시간 통계 조회 성공")
    void getRealtimeStatistics_Success() throws Exception {
        // given
        when(getCouponStatisticsUseCase.getRealtimeStatistics(anyLong()))
                .thenReturn(mockRealtimeStats);

        // when & then
        mockMvc.perform(get("/api/coupons/statistics/realtime/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(1))
                .andExpect(jsonPath("$.policyName").value("테스트 쿠폰"))
                .andExpect(jsonPath("$.currentIssueCount").value(500))
                .andExpect(jsonPath("$.usageRate").value(40.0));
    }

    @Test
    @DisplayName("전체 통계 조회 성공")
    void getGlobalStatistics_Success() throws Exception {
        // given
        when(getCouponStatisticsUseCase.getGlobalStatistics())
                .thenReturn(mockGlobalStats);

        // when & then
        mockMvc.perform(get("/api/coupons/statistics/global"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPolicies").value(10))
                .andExpect(jsonPath("$.totalIssuedCoupons").value(5000))
                .andExpect(jsonPath("$.overallUsageRate").value(40.0));
    }

    @Test
    @DisplayName("사용자 통계 조회 성공")
    void getUserStatistics_Success() throws Exception {
        // given
        when(getCouponStatisticsUseCase.getUserStatistics(anyLong()))
                .thenReturn(mockUserStats);

        // when & then
        mockMvc.perform(get("/api/coupons/statistics/user/123"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(123))
                .andExpect(jsonPath("$.totalCoupons").value(10))
                .andExpect(jsonPath("$.availableCoupons").value(5));
    }

    @Test
    @DisplayName("대시보드 통계 조회 성공")
    void getDashboardSummary_Success() throws Exception {
        // given
        when(getCouponStatisticsUseCase.getGlobalStatistics())
                .thenReturn(mockGlobalStats);

        // when & then
        mockMvc.perform(get("/api/coupons/statistics/dashboard"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPolicies").value(10))
                .andExpect(jsonPath("$.totalIssuedCoupons").value(5000))
                .andExpect(jsonPath("$.activeReservations").value(500));
    }
}

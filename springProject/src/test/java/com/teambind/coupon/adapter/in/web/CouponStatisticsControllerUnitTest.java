package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase;
import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CouponStatisticsController 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponStatisticsController 단위 테스트")
class CouponStatisticsControllerUnitTest {

    @InjectMocks
    private CouponStatisticsController statisticsController;

    @Mock
    private GetCouponStatisticsUseCase getCouponStatisticsUseCase;

    private RealtimeStatistics realtimeStatistics;
    private GlobalStatistics globalStatistics;
    private UserStatistics userStatistics;

    @BeforeEach
    void setUp() {
        realtimeStatistics = RealtimeStatistics.builder()
                .policyId(1L)
                .policyName("테스트 쿠폰")
                .maxIssueCount(100)
                .currentIssueCount(60)
                .usedCount(45)
                .reservedCount(10)
                .availableCount(40)
                .usageRate(75.0)
                .lastIssuedAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .build();

        globalStatistics = GlobalStatistics.builder()
                .totalPolicies(10)
                .totalIssuedCoupons(1000L)
                .totalUsedCoupons(450L)
                .totalReservedCoupons(50L)
                .totalExpiredCoupons(100L)
                .overallUsageRate(45.0)
                .statusDistribution(null)
                .typeDistribution(null)
                .build();

        userStatistics = UserStatistics.builder()
                .userId(100L)
                .totalCoupons(10)
                .availableCoupons(2)
                .usedCoupons(5)
                .expiredCoupons(3)
                .firstIssuedAt(LocalDateTime.now().minusDays(30))
                .lastUsedAt(LocalDateTime.now().minusDays(1))
                .couponsByStatus(null)
                .build();
    }

    @Test
    @DisplayName("실시간 통계 조회 성공")
    void getRealtimeStatistics_Success() {
        // given
        Long policyId = 1L;
        when(getCouponStatisticsUseCase.getRealtimeStatistics(policyId))
                .thenReturn(realtimeStatistics);

        // when
        ResponseEntity<RealtimeStatistics> response = statisticsController.getRealtimeStatistics(policyId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPolicyId()).isEqualTo(1L);
        assertThat(response.getBody().getPolicyName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().getCurrentIssueCount()).isEqualTo(60);
        assertThat(response.getBody().getUsedCount()).isEqualTo(45);
        assertThat(response.getBody().getUsageRate()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("전체 통계 조회 성공")
    void getGlobalStatistics_Success() {
        // given
        when(getCouponStatisticsUseCase.getGlobalStatistics())
                .thenReturn(globalStatistics);

        // when
        ResponseEntity<GlobalStatistics> response = statisticsController.getGlobalStatistics();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalPolicies()).isEqualTo(10);
        assertThat(response.getBody().getTotalIssuedCoupons()).isEqualTo(1000L);
        assertThat(response.getBody().getTotalUsedCoupons()).isEqualTo(450L);
        assertThat(response.getBody().getOverallUsageRate()).isEqualTo(45.0);
    }

    @Test
    @DisplayName("사용자별 통계 조회 성공")
    void getUserStatistics_Success() {
        // given
        Long userId = 100L;
        when(getCouponStatisticsUseCase.getUserStatistics(userId))
                .thenReturn(userStatistics);

        // when
        ResponseEntity<UserStatistics> response = statisticsController.getUserStatistics(userId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(100L);
        assertThat(response.getBody().getTotalCoupons()).isEqualTo(10);
        assertThat(response.getBody().getUsedCoupons()).isEqualTo(5);
        assertThat(response.getBody().getAvailableCoupons()).isEqualTo(2);
    }

    @Test
    @DisplayName("대시보드 요약 통계 조회 성공")
    void getDashboardSummary_Success() {
        // given
        when(getCouponStatisticsUseCase.getGlobalStatistics())
                .thenReturn(globalStatistics);

        // when
        ResponseEntity<CouponStatisticsController.DashboardSummary> response =
                statisticsController.getDashboardSummary();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalPolicies()).isEqualTo(10);
        assertThat(response.getBody().getTotalIssuedCoupons()).isEqualTo(1000L);
        assertThat(response.getBody().getTotalUsedCoupons()).isEqualTo(450L);
        assertThat(response.getBody().getOverallUsageRate()).isEqualTo(45.0);
        assertThat(response.getBody().getActiveReservations()).isEqualTo(50L);
        assertThat(response.getBody().getExpiredCoupons()).isEqualTo(100L);
    }

    @Test
    @DisplayName("실시간 통계 - 발급 내역이 없는 경우")
    void getRealtimeStatistics_NoIssued() {
        // given
        RealtimeStatistics emptyStats = RealtimeStatistics.builder()
                .policyId(2L)
                .policyName("신규 쿠폰")
                .maxIssueCount(100)
                .currentIssueCount(0)
                .usedCount(0)
                .reservedCount(0)
                .availableCount(100)
                .usageRate(0.0)
                .lastIssuedAt(null)
                .lastUsedAt(null)
                .build();

        when(getCouponStatisticsUseCase.getRealtimeStatistics(2L))
                .thenReturn(emptyStats);

        // when
        ResponseEntity<RealtimeStatistics> response = statisticsController.getRealtimeStatistics(2L);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCurrentIssueCount()).isEqualTo(0);
        assertThat(response.getBody().getUsageRate()).isEqualTo(0.0);
        assertThat(response.getBody().getLastIssuedAt()).isNull();
    }

    @Test
    @DisplayName("사용자 통계 - 쿠폰 사용 이력이 없는 경우")
    void getUserStatistics_NoHistory() {
        // given
        UserStatistics noHistoryStats = UserStatistics.builder()
                .userId(200L)
                .totalCoupons(0)
                .availableCoupons(0)
                .usedCoupons(0)
                .expiredCoupons(0)
                .firstIssuedAt(null)
                .lastUsedAt(null)
                .couponsByStatus(null)
                .build();

        when(getCouponStatisticsUseCase.getUserStatistics(200L))
                .thenReturn(noHistoryStats);

        // when
        ResponseEntity<UserStatistics> response = statisticsController.getUserStatistics(200L);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalCoupons()).isEqualTo(0);
        assertThat(response.getBody().getAvailableCoupons()).isEqualTo(0);
    }

    @Test
    @DisplayName("전체 통계 - 초기 상태")
    void getGlobalStatistics_InitialState() {
        // given
        GlobalStatistics initialStats = GlobalStatistics.builder()
                .totalPolicies(0)
                .totalIssuedCoupons(0L)
                .totalUsedCoupons(0L)
                .totalReservedCoupons(0L)
                .totalExpiredCoupons(0L)
                .overallUsageRate(0.0)
                .statusDistribution(null)
                .typeDistribution(null)
                .build();

        when(getCouponStatisticsUseCase.getGlobalStatistics())
                .thenReturn(initialStats);

        // when
        ResponseEntity<GlobalStatistics> response = statisticsController.getGlobalStatistics();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalPolicies()).isEqualTo(0);
        assertThat(response.getBody().getTotalIssuedCoupons()).isEqualTo(0L);
    }
}
package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase;
import com.teambind.coupon.application.port.in.GetCouponStatisticsUseCase.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 쿠폰 통계 API 컨트롤러
 * 실시간 쿠폰 통계 정보 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/coupons/statistics")
@RequiredArgsConstructor
public class CouponStatisticsController {

    private final GetCouponStatisticsUseCase getCouponStatisticsUseCase;

    /**
     * 쿠폰 정책별 실시간 통계 조회
     *
     * @param policyId 쿠폰 정책 ID
     * @return 실시간 통계 정보
     */
    @GetMapping("/realtime/{policyId}")
    public ResponseEntity<RealtimeStatistics> getRealtimeStatistics(
            @PathVariable Long policyId) {
        
        log.info("실시간 통계 조회 요청 - policyId: {}", policyId);
        
        RealtimeStatistics statistics = getCouponStatisticsUseCase.getRealtimeStatistics(policyId);
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * 전체 쿠폰 통계 조회
     *
     * @return 전체 통계 정보
     */
    @GetMapping("/global")
    public ResponseEntity<GlobalStatistics> getGlobalStatistics() {
        
        log.info("전체 통계 조회 요청");
        
        GlobalStatistics statistics = getCouponStatisticsUseCase.getGlobalStatistics();
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * 사용자별 쿠폰 통계 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 통계 정보
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserStatistics> getUserStatistics(
            @PathVariable Long userId) {
        
        log.info("사용자 통계 조회 요청 - userId: {}", userId);
        
        UserStatistics statistics = getCouponStatisticsUseCase.getUserStatistics(userId);
        
        return ResponseEntity.ok(statistics);
    }

    /**
     * 실시간 대시보드용 요약 통계
     * 
     * @return 대시보드 통계
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardSummary> getDashboardSummary() {
        
        log.info("대시보드 통계 조회 요청");
        
        GlobalStatistics global = getCouponStatisticsUseCase.getGlobalStatistics();
        
        DashboardSummary summary = DashboardSummary.builder()
                .totalPolicies(global.getTotalPolicies())
                .totalIssuedCoupons(global.getTotalIssuedCoupons())
                .totalUsedCoupons(global.getTotalUsedCoupons())
                .overallUsageRate(global.getOverallUsageRate())
                .activeReservations(global.getTotalReservedCoupons())
                .expiredCoupons(global.getTotalExpiredCoupons())
                .build();
        
        return ResponseEntity.ok(summary);
    }

    /**
     * 대시보드 요약 DTO
     */
    @lombok.Value
    @lombok.Builder
    public static class DashboardSummary {
        Integer totalPolicies;
        Long totalIssuedCoupons;
        Long totalUsedCoupons;
        Double overallUsageRate;
        Long activeReservations;
        Long expiredCoupons;
    }
}

package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ProcessTimeoutReservationsUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.domain.model.CouponIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 쿠폰 타임아웃 처리 서비스
 * 예약 후 일정 시간이 지난 쿠폰을 ISSUED 상태로 복구
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponTimeoutService implements ProcessTimeoutReservationsUseCase {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;

    @Value("${coupon.reservation.timeout:10}")
    private int reservationTimeoutMinutes;

    @Override
    @Transactional
    public int processTimeoutReservations() {
        log.info("예약 타임아웃 처리 시작 - timeout: {}분", reservationTimeoutMinutes);

        // 1. 타임아웃 시간 계산
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(reservationTimeoutMinutes);

        // 2. 타임아웃된 예약 쿠폰 조회
        List<CouponIssue> timeoutReservations = loadCouponIssuePort.loadTimeoutReservations(timeoutTime);

        if (timeoutReservations.isEmpty()) {
            log.debug("타임아웃된 예약 쿠폰이 없습니다");
            return 0;
        }

        log.info("타임아웃된 예약 쿠폰 발견 - count: {}", timeoutReservations.size());

        // 3. 배치 처리를 위한 리스트 준비
        List<CouponIssue> processedCoupons = new ArrayList<>();
        int processedCount = 0;

        // 4. 각 쿠폰을 타임아웃 처리
        for (CouponIssue couponIssue : timeoutReservations) {
            try {
                if (processTimeoutCouponForBatch(couponIssue, timeoutTime)) {
                    processedCoupons.add(couponIssue);
                    processedCount++;

                    // 배치 크기에 도달하면 저장
                    if (processedCoupons.size() >= 100) {
                        saveCouponIssuePort.updateAll(processedCoupons);
                        processedCoupons.clear();
                    }
                }
            } catch (Exception e) {
                log.error("쿠폰 타임아웃 처리 실패 - couponId: {}, error: {}",
                        couponIssue.getId(), e.getMessage(), e);
                // 개별 실패는 무시하고 계속 처리
            }
        }

        // 5. 남은 쿠폰들 저장
        if (!processedCoupons.isEmpty()) {
            saveCouponIssuePort.updateAll(processedCoupons);
        }

        log.info("예약 타임아웃 처리 완료 - processed: {}/{}", processedCount, timeoutReservations.size());

        return processedCount;
    }

    /**
     * 배치 처리를 위한 개별 쿠폰 타임아웃 처리
     * @return 처리 성공 여부
     */
    private boolean processTimeoutCouponForBatch(CouponIssue couponIssue, LocalDateTime timeoutTime) {
        // 타임아웃 확인
        if (!couponIssue.isReservationTimeout(reservationTimeoutMinutes)) {
            log.debug("아직 타임아웃되지 않은 쿠폰 - couponId: {}, reservedAt: {}",
                    couponIssue.getId(), couponIssue.getReservedAt());
            return false;
        }

        String originalReservationId = couponIssue.getReservationId();

        // 타임아웃 처리 (reservationId 유지)
        couponIssue.timeoutReservation();

        log.debug("쿠폰 예약 타임아웃 처리 - couponId: {}, reservationId: {}",
                couponIssue.getId(), originalReservationId);

        return true;
    }
}
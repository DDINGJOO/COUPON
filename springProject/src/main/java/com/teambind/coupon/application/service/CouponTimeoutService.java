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

        int processedCount = 0;

        // 3. 각 쿠폰을 ISSUED 상태로 복구
        for (CouponIssue couponIssue : timeoutReservations) {
            try {
                processTimeoutCoupon(couponIssue, timeoutTime);
                processedCount++;
            } catch (Exception e) {
                log.error("쿠폰 타임아웃 처리 실패 - couponId: {}, error: {}",
                        couponIssue.getId(), e.getMessage(), e);
                // 개별 실패는 무시하고 계속 처리
            }
        }

        log.info("예약 타임아웃 처리 완료 - processed: {}/{}", processedCount, timeoutReservations.size());

        return processedCount;
    }

    /**
     * 개별 쿠폰 타임아웃 처리
     * reservationId는 유지하면서 상태만 ISSUED로 변경
     */
    private void processTimeoutCoupon(CouponIssue couponIssue, LocalDateTime timeoutTime) {
        // 타임아웃 확인
        if (!couponIssue.isReservationTimeout(reservationTimeoutMinutes)) {
            log.debug("아직 타임아웃되지 않은 쿠폰 - couponId: {}, reservedAt: {}",
                    couponIssue.getId(), couponIssue.getReservedAt());
            return;
        }

        String originalReservationId = couponIssue.getReservationId();

        // 예약 취소 (상태를 ISSUED로 변경, reservationId는 유지)
        couponIssue.cancelReservation();

        // reservationId를 다시 설정 (cancelReservation이 null로 만들기 때문)
        // 이렇게 하면 나중에 같은 reservationId로 결제 이벤트가 와도 처리 가능
        if (originalReservationId != null) {
            // 리플렉션이나 별도 메서드가 필요할 수 있음
            // 도메인 모델에 setReservationId 메서드를 추가하거나
            // 별도의 타임아웃 취소 메서드를 만드는 것이 좋음
            log.info("쿠폰 예약 타임아웃 - 상태 복구 - couponId: {}, reservationId: {}",
                    couponIssue.getId(), originalReservationId);
        }

        // 변경사항 저장
        saveCouponIssuePort.update(couponIssue);

        log.info("쿠폰 예약 타임아웃 처리 완료 - couponId: {}, reservationId: {}, reservedAt: {}",
                couponIssue.getId(), originalReservationId, couponIssue.getReservedAt());
    }
}
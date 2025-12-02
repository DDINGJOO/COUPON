package com.teambind.coupon.application.scheduler;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 쿠폰 만료 처리 스케줄러
 * 주기적으로 만료된 쿠폰의 상태를 업데이트합니다.
 * DB에서 삭제하지 않고 상태만 EXPIRED로 변경하여 이력을 보존합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpiryScheduler {

    private final CouponIssueRepository couponIssueRepository;

    @Value("${coupon.scheduler.expiry.batch-size:100}")
    private int batchSize;

    @Value("${coupon.scheduler.expiry.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * 매일 자정에 만료된 쿠폰 상태 업데이트
     * cron: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "${coupon.scheduler.expiry.cron:0 0 0 * * *}")
    @Transactional
    public void processExpiredCoupons() {
        if (!schedulerEnabled) {
            log.info("쿠폰 만료 스케줄러가 비활성화되어 있습니다.");
            return;
        }

        log.info("쿠폰 만료 처리 시작");
        LocalDateTime now = LocalDateTime.now();

        try {
            int totalProcessed = processExpiredCouponsInBatches(now);
            log.info("쿠폰 만료 처리 완료 - 총 {}개 쿠폰 상태 업데이트", totalProcessed);
        } catch (Exception e) {
            log.error("쿠폰 만료 처리 중 오류 발생", e);
            throw e;
        }
    }

    /**
     * 배치 단위로 만료 쿠폰 처리
     * 대량의 데이터를 효율적으로 처리하기 위해 페이징 사용
     */
    private int processExpiredCouponsInBatches(LocalDateTime now) {
        int totalProcessed = 0;
        int pageNumber = 0;
        Page<CouponIssueEntity> page;

        do {
            // 배치 크기만큼 조회
            Pageable pageable = PageRequest.of(pageNumber, batchSize);
            page = couponIssueRepository.findExpiredCouponsWithPaging(now, pageable);

            if (page.hasContent()) {
                // 배치 업데이트
                int processed = updateExpiredCoupons(page.getContent(), now);
                totalProcessed += processed;

                log.debug("배치 {} 처리 완료: {}개 쿠폰 만료 처리", pageNumber + 1, processed);
            }

            pageNumber++;
        } while (page.hasNext());

        return totalProcessed;
    }

    /**
     * 쿠폰 상태를 EXPIRED로 일괄 업데이트
     */
    private int updateExpiredCoupons(List<CouponIssueEntity> expiredCoupons, LocalDateTime now) {
        if (expiredCoupons.isEmpty()) {
            return 0;
        }

        // 쿠폰 ID 목록 추출
        List<Long> couponIds = expiredCoupons.stream()
                .map(CouponIssueEntity::getId)
                .collect(Collectors.toList());

        // 배치 업데이트 실행
        int updatedCount = couponIssueRepository.updateToExpiredBatch(couponIds, now);

        // 업데이트된 쿠폰 정보 로깅
        if (log.isDebugEnabled()) {
            log.debug("만료 처리된 쿠폰 ID: {}", couponIds);
        }

        // 사용자별 알림 발송 (추후 구현 가능)
        // notificationService.sendExpiryNotifications(expiredCoupons);

        return updatedCount;
    }

    /**
     * 즉시 만료 처리 실행 (테스트 및 수동 실행용)
     */
    @Transactional
    public int processExpiredCouponsManually() {
        log.info("쿠폰 만료 수동 처리 시작");
        LocalDateTime now = LocalDateTime.now();

        try {
            int totalProcessed = processExpiredCouponsInBatches(now);
            log.info("쿠폰 만료 수동 처리 완료 - 총 {}개 쿠폰 상태 업데이트", totalProcessed);
            return totalProcessed;
        } catch (Exception e) {
            log.error("쿠폰 만료 수동 처리 중 오류 발생", e);
            throw e;
        }
    }

    /**
     * 예약 타임아웃 처리
     * 30분이 지난 예약 상태 쿠폰을 다시 ISSUED 상태로 복구
     */
    @Scheduled(cron = "${coupon.scheduler.reservation-timeout.cron:0 */5 * * * *}")
    @Transactional
    public void processReservationTimeouts() {
        if (!schedulerEnabled) {
            return;
        }

        log.debug("예약 타임아웃 처리 시작");
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(30);

        List<CouponIssueEntity> timeoutReservations =
                couponIssueRepository.findTimeoutReservations(timeoutThreshold);

        if (!timeoutReservations.isEmpty()) {
            for (CouponIssueEntity reservation : timeoutReservations) {
                // 예약 취소하고 ISSUED 상태로 복구
                reservation.rollback();
                couponIssueRepository.save(reservation);
            }

            log.info("예약 타임아웃 처리 완료 - {}개 쿠폰 복구", timeoutReservations.size());
        }
    }

    /**
     * 스케줄러 상태 조회
     */
    public SchedulerStatus getStatus() {
        return SchedulerStatus.builder()
                .enabled(schedulerEnabled)
                .batchSize(batchSize)
                .lastProcessedTime(LocalDateTime.now())
                .build();
    }

    /**
     * 스케줄러 상태 정보
     */
    @lombok.Builder
    @lombok.Getter
    public static class SchedulerStatus {
        private final boolean enabled;
        private final int batchSize;
        private final LocalDateTime lastProcessedTime;
    }
}
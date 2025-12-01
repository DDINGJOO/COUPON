package com.teambind.coupon.adapter.in.scheduler;

import com.teambind.coupon.application.port.in.ProcessTimeoutReservationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 쿠폰 예약 타임아웃 스케줄러
 * 일정 시간이 지난 예약 상태의 쿠폰을 다시 ISSUED 상태로 복구
 * ShedLock을 사용하여 멀티 인스턴스 환경에서 중복 실행 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "coupon.scheduler.timeout.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CouponTimeoutScheduler {

    private final ProcessTimeoutReservationsUseCase processTimeoutReservationsUseCase;

    /**
     * 예약 타임아웃 처리 스케줄러
     * 매 1분마다 실행되어 타임아웃된 예약을 처리
     * ShedLock을 사용하여 클러스터 환경에서 하나의 인스턴스만 실행
     */
    @Scheduled(cron = "${coupon.scheduler.timeout.cron:0 * * * * *}")
    @SchedulerLock(
            name = "processTimeoutReservations",
            lockAtMostFor = "50s",  // 최대 50초 동안 잠금 유지
            lockAtLeastFor = "10s"  // 최소 10초 동안 잠금 유지
    )
    public void processTimeoutReservations() {
        try {
            log.debug("예약 타임아웃 처리 스케줄러 시작 - time: {}", LocalDateTime.now());

            int processedCount = processTimeoutReservationsUseCase.processTimeoutReservations();

            if (processedCount > 0) {
                log.info("예약 타임아웃 처리 완료 - processedCount: {}", processedCount);
            } else {
                log.debug("처리할 타임아웃 예약이 없습니다");
            }

        } catch (Exception e) {
            log.error("예약 타임아웃 처리 스케줄러 실행 중 오류 발생", e);
            // 에러가 발생해도 다음 스케줄 실행에는 영향을 주지 않도록 예외를 던지지 않음
        }
    }
}
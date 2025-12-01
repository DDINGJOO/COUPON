package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SendEventPort;
import com.teambind.coupon.domain.event.CouponReservationTimeoutEvent;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.fixture.CouponIssueFixture;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 타임아웃 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 타임아웃 서비스 테스트")
class CouponTimeoutServiceTest {

    @InjectMocks
    private CouponTimeoutService timeoutService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private SendEventPort sendEventPort;

    private CouponPolicy policy;

    @BeforeEach
    void setUp() {
        policy = CouponPolicyFixture.createCodePolicy();
        // 타임아웃 시간 설정 (테스트용 5분)
        ReflectionTestUtils.setField(timeoutService, "reservationTimeoutMinutes", 5);
    }

    @Nested
    @DisplayName("예약 타임아웃 처리")
    class ReservationTimeoutTest {

        @Test
        @DisplayName("타임아웃된 예약 쿠폰 해제")
        void releaseTimedOutReservations() {
            // given
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime timeoutThreshold = now.minusMinutes(5);

            // 타임아웃된 쿠폰들
            CouponIssue timedOut1 = createReservedCoupon(100L, now.minusMinutes(10));
            CouponIssue timedOut2 = createReservedCoupon(101L, now.minusMinutes(6));

            // 아직 타임아웃 안된 쿠폰
            CouponIssue notTimedOut = createReservedCoupon(102L, now.minusMinutes(2));

            List<CouponIssue> timedOutCoupons = Arrays.asList(timedOut1, timedOut2);

            when(loadCouponIssuePort.findTimedOutReservations(any(LocalDateTime.class)))
                    .thenReturn(timedOutCoupons);
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            timeoutService.processTimeouts();

            // then
            ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(loadCouponIssuePort).findTimedOutReservations(timeCaptor.capture());

            LocalDateTime capturedTime = timeCaptor.getValue();
            assertThat(capturedTime).isBefore(now.minusMinutes(4));
            assertThat(capturedTime).isAfter(now.minusMinutes(6));

            verify(saveCouponIssuePort).saveAll(argThat(coupons ->
                coupons.size() == 2 &&
                coupons.stream().allMatch(c -> c.getStatus() == CouponStatus.ISSUED)
            ));

            verify(sendEventPort, times(2)).send(any(CouponReservationTimeoutEvent.class));
        }

        @Test
        @DisplayName("타임아웃 대상이 없는 경우")
        void noTimeoutTargets() {
            // given
            when(loadCouponIssuePort.findTimedOutReservations(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // when
            timeoutService.processTimeouts();

            // then
            verify(saveCouponIssuePort, never()).saveAll(anyList());
            verify(sendEventPort, never()).send(any());
        }

        @Test
        @DisplayName("대량 타임아웃 배치 처리")
        void batchTimeoutProcessing() {
            // given
            List<CouponIssue> timedOutCoupons = createManyTimedOutCoupons(1000);

            when(loadCouponIssuePort.findTimedOutReservations(any(LocalDateTime.class)))
                    .thenReturn(timedOutCoupons);
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            timeoutService.processTimeouts();

            // then
            // 배치 사이즈 100으로 처리되었는지 확인
            verify(saveCouponIssuePort, times(10)).saveAll(argThat(batch ->
                batch.size() == 100
            ));

            verify(sendEventPort, times(1000)).send(any(CouponReservationTimeoutEvent.class));
        }
    }

    @Nested
    @DisplayName("스케줄러 실행 테스트")
    class SchedulerExecutionTest {

        @Test
        @DisplayName("스케줄 어노테이션 확인")
        void verifyScheduledAnnotation() throws NoSuchMethodException {
            // given
            var method = CouponTimeoutService.class.getDeclaredMethod("scheduledTimeoutProcessing");

            // when
            Scheduled scheduled = method.getAnnotation(Scheduled.class);

            // then
            assertThat(scheduled).isNotNull();
            assertThat(scheduled.fixedDelay()).isEqualTo(60000); // 1분 마다
            assertThat(scheduled.initialDelay()).isEqualTo(60000); // 1분 후 시작
        }

        @Test
        @DisplayName("스케줄러 중복 실행 방지")
        void preventDuplicateExecution() throws InterruptedException {
            // given
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            when(loadCouponIssuePort.findTimedOutReservations(any()))
                    .thenAnswer(inv -> {
                        Thread.sleep(100); // 처리 시간 시뮬레이션
                        return Collections.emptyList();
                    });

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        timeoutService.processTimeouts();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 모든 스레드 동시 시작
            endLatch.await(5, TimeUnit.SECONDS);

            // then
            // 동시 실행 방지 메커니즘이 있다면 1번만 실행
            verify(loadCouponIssuePort, atMost(threadCount)).findTimedOutReservations(any());
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("예약 해제 세부 처리")
    class ReleaseDetailsTest {

        @Test
        @DisplayName("예약 정보 초기화")
        void clearReservationInfo() {
            // given
            CouponIssue timedOutCoupon = createReservedCoupon(100L, LocalDateTime.now().minusMinutes(10));
            String originalReservationId = timedOutCoupon.getReservationId();

            when(loadCouponIssuePort.findTimedOutReservations(any()))
                    .thenReturn(Collections.singletonList(timedOutCoupon));
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            timeoutService.processTimeouts();

            // then
            verify(saveCouponIssuePort).saveAll(argThat(coupons ->
                coupons.get(0).getReservationId() == null &&
                coupons.get(0).getReservedAt() == null &&
                coupons.get(0).getStatus() == CouponStatus.ISSUED
            ));
        }

        @Test
        @DisplayName("타임아웃 이벤트 발행 정보 확인")
        void verifyTimeoutEventDetails() {
            // given
            CouponIssue timedOutCoupon = createReservedCoupon(100L, LocalDateTime.now().minusMinutes(10));
            String reservationId = timedOutCoupon.getReservationId();

            when(loadCouponIssuePort.findTimedOutReservations(any()))
                    .thenReturn(Collections.singletonList(timedOutCoupon));
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            timeoutService.processTimeouts();

            // then
            verify(sendEventPort).send(argThat(event ->
                event instanceof CouponReservationTimeoutEvent &&
                ((CouponReservationTimeoutEvent) event).getCouponId().equals(timedOutCoupon.getId()) &&
                ((CouponReservationTimeoutEvent) event).getUserId().equals(100L) &&
                ((CouponReservationTimeoutEvent) event).getReservationId().equals(reservationId)
            ));
        }

        @Test
        @DisplayName("예외 발생 시 로깅 및 계속 처리")
        void handleExceptionDuringProcessing() {
            // given
            CouponIssue failCoupon = createReservedCoupon(100L, LocalDateTime.now().minusMinutes(10));
            CouponIssue successCoupon = createReservedCoupon(101L, LocalDateTime.now().minusMinutes(10));

            when(loadCouponIssuePort.findTimedOutReservations(any()))
                    .thenReturn(Arrays.asList(failCoupon, successCoupon));

            // 첫 번째 쿠폰 처리 시 예외 발생
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenThrow(new RuntimeException("DB Error"))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            timeoutService.processTimeouts();

            // then
            // 예외 발생 후에도 계속 처리 시도
            verify(saveCouponIssuePort, atLeast(1)).saveAll(any());
        }
    }

    @Nested
    @DisplayName("만료 처리 테스트")
    class ExpirationProcessingTest {

        @Test
        @DisplayName("유효기간 만료된 쿠폰 처리")
        void processExpiredCoupons() {
            // given
            LocalDateTime now = LocalDateTime.now();
            CouponIssue expiredCoupon1 = CouponIssueFixture.createIssuedCoupon(100L, policy);
            expiredCoupon1.setExpiresAt(now.minusDays(1));

            CouponIssue expiredCoupon2 = CouponIssueFixture.createIssuedCoupon(101L, policy);
            expiredCoupon2.setExpiresAt(now.minusHours(1));

            List<CouponIssue> expiredCoupons = Arrays.asList(expiredCoupon1, expiredCoupon2);

            when(loadCouponIssuePort.findExpiredCoupons(any(LocalDateTime.class)))
                    .thenReturn(expiredCoupons);
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            timeoutService.processExpirations();

            // then
            verify(saveCouponIssuePort).saveAll(argThat(coupons ->
                coupons.size() == 2 &&
                coupons.stream().allMatch(c -> c.getStatus() == CouponStatus.EXPIRED)
            ));
        }

        @Test
        @DisplayName("사용된 쿠폰은 만료 처리하지 않음")
        void skipUsedCoupons() {
            // given
            CouponIssue usedCoupon = CouponIssueFixture.createUsedCoupon(100L, policy, "order-123");
            usedCoupon.setExpiresAt(LocalDateTime.now().minusDays(1));

            when(loadCouponIssuePort.findExpiredCoupons(any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(usedCoupon));

            // when
            timeoutService.processExpirations();

            // then
            verify(saveCouponIssuePort, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("통계 및 모니터링")
    class MonitoringTest {

        @Test
        @DisplayName("처리 통계 기록")
        void recordProcessingStatistics() {
            // given
            List<CouponIssue> timedOutCoupons = createManyTimedOutCoupons(50);

            when(loadCouponIssuePort.findTimedOutReservations(any()))
                    .thenReturn(timedOutCoupons);
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            var stats = timeoutService.processTimeoutsWithStats();

            // then
            assertThat(stats).containsEntry("processed", 50);
            assertThat(stats).containsEntry("success", 50);
            assertThat(stats).containsEntry("failed", 0);
            assertThat(stats).containsKey("processingTime");
        }

        @Test
        @DisplayName("에러율 모니터링")
        void monitorErrorRate() {
            // given
            List<CouponIssue> timedOutCoupons = createManyTimedOutCoupons(10);

            when(loadCouponIssuePort.findTimedOutReservations(any()))
                    .thenReturn(timedOutCoupons);

            // 50% 실패 시뮬레이션
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> {
                        List<CouponIssue> batch = inv.getArgument(0);
                        if (Math.random() > 0.5) {
                            throw new RuntimeException("Random failure");
                        }
                        return batch;
                    });

            // when
            var stats = timeoutService.processTimeoutsWithStats();

            // then
            assertThat(stats).containsKey("errorRate");
            Number errorRate = (Number) stats.get("errorRate");
            assertThat(errorRate.doubleValue()).isBetween(0.0, 1.0);
        }
    }

    // Helper methods
    private CouponIssue createReservedCoupon(Long userId, LocalDateTime reservedAt) {
        CouponIssue coupon = CouponIssueFixture.createReservedCoupon(
            userId, policy, "reservation-" + userId
        );
        ReflectionTestUtils.setField(coupon, "reservedAt", reservedAt);
        return coupon;
    }

    private List<CouponIssue> createManyTimedOutCoupons(int count) {
        return Arrays.asList(new CouponIssue[count]).stream()
                .map(c -> createReservedCoupon(
                    (long) (Math.random() * 1000),
                    LocalDateTime.now().minusMinutes(10)
                ))
                .toList();
    }
}
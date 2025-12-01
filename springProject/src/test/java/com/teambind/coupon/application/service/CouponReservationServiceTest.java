package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ReserveCouponCommand;
import com.teambind.coupon.application.port.in.ReserveCouponUseCase.CouponReservationResult;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 예약 서비스 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 예약 서비스 테스트")
class CouponReservationServiceTest {

    @InjectMocks
    private CouponReservationService reservationService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    private CouponPolicy policy;
    private CouponIssue issuedCoupon;
    private ReserveCouponCommand command;

    @BeforeEach
    void setUp() {
        policy = CouponPolicyFixture.createCodePolicy();
        issuedCoupon = CouponIssueFixture.createIssuedCoupon(100L, policy);
    }

    @Nested
    @DisplayName("정상 예약 케이스")
    class SuccessCase {

        @Test
        @DisplayName("발급된 쿠폰 예약 성공")
        void reserveIssuedCoupon() {
            // given
            String reservationId = UUID.randomUUID().toString();
            command = ReserveCouponCommand.of(100L, issuedCoupon.getId(), reservationId);

            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCouponId()).isEqualTo(issuedCoupon.getId());
            assertThat(result.getReservationId()).isEqualTo(reservationId);
            assertThat(result.getMessage()).contains("예약 성공");

            verify(saveCouponIssuePort).save(argThat(coupon ->
                    coupon.getStatus() == CouponStatus.RESERVED &&
                    coupon.getReservationId().equals(reservationId)
            ));
        }

        @Test
        @DisplayName("동일 예약 ID로 재시도시 멱등성 보장")
        void idempotentReservation() {
            // given
            String reservationId = UUID.randomUUID().toString();
            CouponIssue reservedCoupon = CouponIssueFixture.createReservedCoupon(100L, policy, reservationId);
            command = ReserveCouponCommand.of(100L, reservedCoupon.getId(), reservationId);

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));

            // when - 동일한 예약 ID로 재시도
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("이미 예약됨");
            verify(saveCouponIssuePort, never()).save(any()); // 저장하지 않음
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCase {

        @Test
        @DisplayName("존재하지 않는 쿠폰 예약 시도")
        void reserveNonExistentCoupon() {
            // given
            command = ReserveCouponCommand.of(100L, 9999L, "reservation-123");
            when(loadCouponIssuePort.loadByIdAndUserId(9999L, 100L))
                    .thenReturn(Optional.empty());

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("찾을 수 없음");
            verify(saveCouponIssuePort, never()).save(any());
        }

        @Test
        @DisplayName("다른 사용자의 쿠폰 예약 시도")
        void reserveOtherUserCoupon() {
            // given
            CouponIssue otherUserCoupon = CouponIssueFixture.createIssuedCoupon(200L, policy);
            command = ReserveCouponCommand.of(100L, otherUserCoupon.getId(), "reservation-123");

            when(loadCouponIssuePort.loadByIdAndUserId(otherUserCoupon.getId(), 100L))
                    .thenReturn(Optional.empty()); // 다른 사용자의 쿠폰이므로 조회 안됨

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("찾을 수 없음");
        }

        @Test
        @DisplayName("이미 다른 예약 ID로 예약된 쿠폰")
        void alreadyReservedWithDifferentId() {
            // given
            String existingReservationId = "existing-reservation";
            CouponIssue reservedCoupon = CouponIssueFixture.createReservedCoupon(100L, policy, existingReservationId);
            command = ReserveCouponCommand.of(100L, reservedCoupon.getId(), "new-reservation");

            when(loadCouponIssuePort.loadByIdAndUserId(reservedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(reservedCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태");
            verify(saveCouponIssuePort, never()).save(any());
        }

        @Test
        @DisplayName("이미 사용된 쿠폰 예약 시도")
        void reserveUsedCoupon() {
            // given
            CouponIssue usedCoupon = CouponIssueFixture.createUsedCoupon(100L, policy, "order-123");
            command = ReserveCouponCommand.of(100L, usedCoupon.getId(), "reservation-123");

            when(loadCouponIssuePort.loadByIdAndUserId(usedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(usedCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태");
        }

        @Test
        @DisplayName("만료된 쿠폰 예약 시도")
        void reserveExpiredCoupon() {
            // given
            CouponIssue expiredCoupon = CouponIssueFixture.createExpiredCoupon(100L, policy);
            command = ReserveCouponCommand.of(100L, expiredCoupon.getId(), "reservation-123");

            when(loadCouponIssuePort.loadByIdAndUserId(expiredCoupon.getId(), 100L))
                    .thenReturn(Optional.of(expiredCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태");
        }

        @Test
        @DisplayName("취소된 쿠폰 예약 시도")
        void reserveCancelledCoupon() {
            // given
            CouponIssue cancelledCoupon = CouponIssueFixture.createCancelledCoupon(100L, policy);
            command = ReserveCouponCommand.of(100L, cancelledCoupon.getId(), "reservation-123");

            when(loadCouponIssuePort.loadByIdAndUserId(cancelledCoupon.getId(), 100L))
                    .thenReturn(Optional.of(cancelledCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태");
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동일 쿠폰에 대한 동시 예약 요청")
        void concurrentReservation() throws InterruptedException {
            // given
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));

            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> {
                        CouponIssue coupon = invocation.getArgument(0);
                        // 첫 번째 예약만 성공
                        if (issuedCoupon.getStatus() == CouponStatus.ISSUED) {
                            issuedCoupon.reserve(coupon.getReservationId());
                            return issuedCoupon;
                        }
                        throw new RuntimeException("Already reserved");
                    });

            // when
            for (int i = 0; i < threadCount; i++) {
                final String reservationId = "reservation-" + i;
                executor.submit(() -> {
                    try {
                        ReserveCouponCommand cmd = ReserveCouponCommand.of(
                                100L, issuedCoupon.getId(), reservationId
                        );
                        CouponReservationResult result = reservationService.reserveCoupon(cmd);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(1); // 한 개만 성공
            assertThat(failureCount.get()).isEqualTo(threadCount - 1);
            executor.shutdown();
        }

        @Test
        @DisplayName("여러 쿠폰에 대한 동시 예약")
        void concurrentMultipleCouponReservation() throws InterruptedException {
            // given
            int couponCount = 100;
            CountDownLatch latch = new CountDownLatch(couponCount);
            ExecutorService executor = Executors.newFixedThreadPool(20);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < couponCount; i++) {
                final long userId = 100L + i;
                final long couponId = 1000L + i;
                final String reservationId = UUID.randomUUID().toString();

                executor.submit(() -> {
                    try {
                        CouponIssue coupon = CouponIssueFixture.createIssuedCoupon(userId, policy);
                        when(loadCouponIssuePort.loadByIdAndUserId(couponId, userId))
                                .thenReturn(Optional.of(coupon));
                        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                                .thenAnswer(inv -> inv.getArgument(0));

                        ReserveCouponCommand cmd = ReserveCouponCommand.of(userId, couponId, reservationId);
                        CouponReservationResult result = reservationService.reserveCoupon(cmd);

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(couponCount); // 모두 성공
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCase {

        @Test
        @DisplayName("빈 예약 ID 처리")
        void emptyReservationId() {
            // given
            command = ReserveCouponCommand.of(100L, issuedCoupon.getId(), "");
            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약 ID가 유효하지 않음");
        }

        @Test
        @DisplayName("null 예약 ID 처리")
        void nullReservationId() {
            // given
            command = ReserveCouponCommand.of(100L, issuedCoupon.getId(), null);
            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약 ID가 유효하지 않음");
        }

        @Test
        @DisplayName("매우 긴 예약 ID 처리")
        void veryLongReservationId() {
            // given
            String longReservationId = "a".repeat(1000);
            command = ReserveCouponCommand.of(100L, issuedCoupon.getId(), longReservationId);
            when(loadCouponIssuePort.loadByIdAndUserId(issuedCoupon.getId(), 100L))
                    .thenReturn(Optional.of(issuedCoupon));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue(); // 길이 제한 없으면 성공
        }
    }

    @Nested
    @DisplayName("타임아웃 관련")
    class TimeoutRelated {

        @Test
        @DisplayName("타임아웃 직전 예약 성공")
        void reserveNearTimeout() {
            // given - 29분 59초 경과한 예약
            CouponIssue nearTimeoutCoupon = CouponIssueFixture.createNearTimeoutReservedCoupon(
                    100L, policy, "old-reservation"
            );
            command = ReserveCouponCommand.of(100L, nearTimeoutCoupon.getId(), "new-reservation");

            when(loadCouponIssuePort.loadByIdAndUserId(nearTimeoutCoupon.getId(), 100L))
                    .thenReturn(Optional.of(nearTimeoutCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then - 아직 타임아웃 안됨, 기존 예약 유지
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태");
        }

        @Test
        @DisplayName("타임아웃된 예약 쿠폰 재예약")
        void reserveTimeoutCoupon() {
            // given - 31분 경과한 예약 (타임아웃 대상)
            CouponIssue timeoutCoupon = CouponIssueFixture.createTimeoutReservedCoupon(
                    100L, policy, "old-reservation"
            );
            command = ReserveCouponCommand.of(100L, timeoutCoupon.getId(), "new-reservation");

            when(loadCouponIssuePort.loadByIdAndUserId(timeoutCoupon.getId(), 100L))
                    .thenReturn(Optional.of(timeoutCoupon));

            // when
            CouponReservationResult result = reservationService.reserveCoupon(command);

            // then - 타임아웃 스케줄러가 처리하기 전까지는 예약 상태 유지
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("예약할 수 없는 상태");
        }
    }
}
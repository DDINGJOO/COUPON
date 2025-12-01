package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DirectIssueCouponCommand;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DistributionType;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 직접 발급 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("쿠폰 직접 발급 서비스 테스트")
class CouponDirectIssueServiceTest {

    @InjectMocks
    private CouponDirectIssueService directIssueService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private CouponPolicy directPolicy;

    @BeforeEach
    void setUp() {
        directPolicy = CouponPolicyFixture.createDirectPolicy();
    }

    @Nested
    @DisplayName("정상 발급 케이스")
    class SuccessIssueTest {

        @Test
        @DisplayName("직접 발급 쿠폰 발급 성공")
        void issueDirectCoupon() {
            // given
            Long userId = 100L;
            Long policyId = directPolicy.getId();
            Long couponId = 1000L;

            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(userId, policyId, "admin");

            when(idGenerator.nextId()).thenReturn(couponId);
            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countByUserIdAndPolicyId(userId, policyId))
                    .thenReturn(0L);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCouponId()).isEqualTo(couponId);
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getMessage()).contains("발급 완료");

            verify(saveCouponIssuePort).save(argThat(coupon ->
                coupon.getId().equals(couponId) &&
                coupon.getUserId().equals(userId) &&
                coupon.getPolicyId().equals(policyId) &&
                coupon.getStatus() == CouponStatus.ISSUED
            ));
        }

        @Test
        @DisplayName("다수 사용자 일괄 발급")
        void batchIssueToMultipleUsers() {
            // given
            List<Long> userIds = Arrays.asList(100L, 101L, 102L, 103L, 104L);
            Long policyId = directPolicy.getId();

            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(directPolicy));
            when(idGenerator.nextId())
                    .thenReturn(1000L, 1001L, 1002L, 1003L, 1004L);
            when(loadCouponIssuePort.countByUserIdAndPolicyId(anyLong(), eq(policyId)))
                    .thenReturn(0L);
            when(saveCouponIssuePort.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            List<DirectIssueCouponUseCase.DirectIssueResult> results =
                directIssueService.batchIssue(userIds, policyId, "admin");

            // then
            assertThat(results).hasSize(5);
            assertThat(results).allMatch(DirectIssueCouponUseCase.DirectIssueResult::isSuccess);

            verify(saveCouponIssuePort).saveAll(argThat(coupons ->
                coupons.size() == 5
            ));
        }

        @Test
        @DisplayName("특정 이벤트용 쿠폰 발급")
        void issueEventCoupon() {
            // given
            String eventCode = "BLACK_FRIDAY_2024";
            Long userId = 100L;
            CouponPolicy eventPolicy = CouponPolicyFixture.createEventPolicy(eventCode);

            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                userId, eventPolicy.getId(), "event_system", eventCode
            );

            when(idGenerator.nextId()).thenReturn(2000L);
            when(loadCouponPolicyPort.loadById(eventPolicy.getId()))
                    .thenReturn(Optional.of(eventPolicy));
            when(loadCouponIssuePort.countByUserIdAndPolicyId(userId, eventPolicy.getId()))
                    .thenReturn(0L);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getEventCode()).isEqualTo(eventCode);

            verify(saveCouponIssuePort).save(argThat(coupon ->
                coupon.getMetadata() != null &&
                coupon.getMetadata().contains(eventCode)
            ));
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureIssueTest {

        @Test
        @DisplayName("정책을 찾을 수 없음")
        void policyNotFound() {
            // given
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(100L, 9999L, "admin");

            when(loadCouponPolicyPort.loadById(9999L))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                directIssueService.issueCoupon(command)
            )
            .isInstanceOf(CouponDomainException.CouponPolicyNotFound.class)
            .hasMessageContaining("정책을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("직접 발급이 아닌 정책")
        void notDirectDistributionType() {
            // given
            CouponPolicy codePolicy = CouponPolicyFixture.createCodePolicy();
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                100L, codePolicy.getId(), "admin"
            );

            when(loadCouponPolicyPort.loadById(codePolicy.getId()))
                    .thenReturn(Optional.of(codePolicy));

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("직접 발급 유형이 아닙니다");
        }

        @Test
        @DisplayName("사용자당 최대 발급 수량 초과")
        void exceededMaxUsagePerUser() {
            // given
            Long userId = 100L;
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                userId, directPolicy.getId(), "admin"
            );

            when(loadCouponPolicyPort.loadById(directPolicy.getId()))
                    .thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countByUserIdAndPolicyId(userId, directPolicy.getId()))
                    .thenReturn(2L); // 이미 2개 발급 (최대 2개)

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("최대 발급 수량 초과");
        }

        @Test
        @DisplayName("전체 발급 수량 소진")
        void totalStockExhausted() {
            // given
            directPolicy.exhaustStock(); // 재고 소진 상태로 설정
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                100L, directPolicy.getId(), "admin"
            );

            when(loadCouponPolicyPort.loadById(directPolicy.getId()))
                    .thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countByUserIdAndPolicyId(100L, directPolicy.getId()))
                    .thenReturn(0L);

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("재고 소진");
        }

        @Test
        @DisplayName("비활성화된 정책")
        void inactivePolicy() {
            // given
            directPolicy.deactivate();
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                100L, directPolicy.getId(), "admin"
            );

            when(loadCouponPolicyPort.loadById(directPolicy.getId()))
                    .thenReturn(Optional.of(directPolicy));

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("비활성 상태");
        }

        @Test
        @DisplayName("유효기간이 지난 정책")
        void expiredPolicy() {
            // given
            CouponPolicy expiredPolicy = CouponPolicyFixture.createExpiredPolicy();
            expiredPolicy.setDistributionType(DistributionType.DIRECT);

            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                100L, expiredPolicy.getId(), "admin"
            );

            when(loadCouponPolicyPort.loadById(expiredPolicy.getId()))
                    .thenReturn(Optional.of(expiredPolicy));

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("유효기간이 지났습니다");
        }
    }

    @Nested
    @DisplayName("권한 검증 테스트")
    class AuthorizationTest {

        @Test
        @DisplayName("관리자 권한 발급")
        void adminAuthorization() {
            // given
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                100L, directPolicy.getId(), "admin"
            );

            when(idGenerator.nextId()).thenReturn(1000L);
            when(loadCouponPolicyPort.loadById(directPolicy.getId()))
                    .thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countByUserIdAndPolicyId(100L, directPolicy.getId()))
                    .thenReturn(0L);
            when(saveCouponIssuePort.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(saveCouponIssuePort).save(argThat(coupon ->
                coupon.getIssuedBy().equals("admin")
            ));
        }

        @Test
        @DisplayName("시스템 자동 발급")
        void systemAutomaticIssue() {
            // given
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                100L, directPolicy.getId(), "system_scheduler"
            );

            when(idGenerator.nextId()).thenReturn(1000L);
            when(loadCouponPolicyPort.loadById(directPolicy.getId()))
                    .thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countByUserIdAndPolicyId(100L, directPolicy.getId()))
                    .thenReturn(0L);
            when(saveCouponIssuePort.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueService.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            verify(saveCouponIssuePort).save(argThat(coupon ->
                coupon.getIssuedBy().equals("system_scheduler")
            ));
        }

        @Test
        @DisplayName("권한 없는 발급 시도")
        void unauthorizedIssue() {
            // given
            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                100L, directPolicy.getId(), null
            );

            // when & then
            assertThatThrownBy(() ->
                directIssueService.issueCoupon(command)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("발급자 정보가 필요합니다");
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시 다발적 발급 요청")
        void concurrentIssueRequests() throws InterruptedException {
            // given
            int threadCount = 10;
            Long policyId = directPolicy.getId();
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(directPolicy));
            when(idGenerator.nextId())
                    .thenAnswer(inv -> System.nanoTime());
            when(loadCouponIssuePort.countByUserIdAndPolicyId(anyLong(), eq(policyId)))
                    .thenReturn(0L);
            when(saveCouponIssuePort.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            for (int i = 0; i < threadCount; i++) {
                final Long userId = 100L + i;
                executor.submit(() -> {
                    try {
                        DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                            userId, policyId, "system"
                        );
                        DirectIssueCouponUseCase.DirectIssueResult result =
                            directIssueService.issueCoupon(command);

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failureCount.get()).isEqualTo(0);
            executor.shutdown();
        }

        @Test
        @DisplayName("재고 소진 경합 상황")
        void stockExhaustionRace() throws InterruptedException {
            // given
            directPolicy.setMaxIssueCount(5); // 총 5개만 발급 가능
            int threadCount = 10;
            Long policyId = directPolicy.getId();
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(directPolicy));
            when(idGenerator.nextId())
                    .thenAnswer(inv -> System.nanoTime());
            when(loadCouponIssuePort.countByUserIdAndPolicyId(anyLong(), eq(policyId)))
                    .thenReturn(0L);
            when(saveCouponIssuePort.save(any()))
                    .thenAnswer(inv -> {
                        if (directPolicy.canIssue()) {
                            directPolicy.decrementStock();
                            return inv.getArgument(0);
                        }
                        throw new CouponDomainException("재고 소진");
                    });

            // when
            for (int i = 0; i < threadCount; i++) {
                final Long userId = 200L + i;
                executor.submit(() -> {
                    try {
                        DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                            userId, policyId, "system"
                        );
                        DirectIssueCouponUseCase.DirectIssueResult result =
                            directIssueService.issueCoupon(command);

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // 재고 소진 예외 처리
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isLessThanOrEqualTo(5); // 최대 5개만 성공
            executor.shutdown();
        }
    }
}
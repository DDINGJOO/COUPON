package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DirectIssueCouponCommand;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase.DirectIssueResult;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.DistributionType;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * CouponDirectIssueService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 직접 발급 서비스 테스트")
class CouponDirectIssueServiceTest {

    @InjectMocks
    private CouponDirectIssueService couponDirectIssueService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponPolicyPort saveCouponPolicyPort;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    private CouponPolicy directPolicy;
    private DirectIssueCouponCommand command;

    @BeforeEach
    void setUp() {
        directPolicy = CouponPolicyFixture.createDirectPolicy();
    }

    @Nested
    @DisplayName("정상 발급 케이스")
    class SuccessCase {

        @Test
        @DisplayName("단일 사용자에게 쿠폰 발급 성공")
        void issueToSingleUser() {
            // given
            command = DirectIssueCouponCommand.forSingleUser(1L, 100L, "admin", "이벤트 보상");
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L)).thenReturn(0);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> {
                        CouponIssue issue = invocation.getArgument(0);
                        return CouponIssue.builder()
                                .id(1000L)
                                .policyId(issue.getPolicyId())
                                .userId(issue.getUserId())
                                .status(issue.getStatus())
                                .issuedAt(issue.getIssuedAt())
                                .couponName(issue.getCouponName())
                                .discountPolicy(issue.getDiscountPolicy())
                                .build();
                    });
            when(saveCouponPolicyPort.decrementStock(1L)).thenReturn(true);

            // when
            DirectIssueResult result = couponDirectIssueService.directIssue(command);

            // then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.failedCount()).isEqualTo(0);
            assertThat(result.issuedCoupons()).hasSize(1);
            verify(saveCouponIssuePort, times(1)).save(any(CouponIssue.class));
            verify(saveCouponPolicyPort, times(1)).decrementStock(1L);
        }

        @Test
        @DisplayName("다중 사용자에게 쿠폰 발급 성공")
        void issueToMultipleUsers() {
            // given
            List<Long> userIds = Arrays.asList(100L, 101L, 102L, 103L, 104L);
            command = DirectIssueCouponCommand.forMultipleUsers(
                    1L, userIds, "admin", "대량 발급 이벤트", 2
            );
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countUserIssuance(anyLong(), eq(1L))).thenReturn(0);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> {
                        CouponIssue issue = invocation.getArgument(0);
                        return CouponIssue.builder()
                                .id(System.currentTimeMillis())
                                .policyId(issue.getPolicyId())
                                .userId(issue.getUserId())
                                .status(issue.getStatus())
                                .issuedAt(issue.getIssuedAt())
                                .couponName(issue.getCouponName())
                                .discountPolicy(issue.getDiscountPolicy())
                                .build();
                    });
            when(saveCouponPolicyPort.decrementStock(1L)).thenReturn(true);

            // when
            DirectIssueResult result = couponDirectIssueService.directIssue(command);

            // then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.successCount()).isEqualTo(10); // 5명 * 2개씩
            assertThat(result.failedCount()).isEqualTo(0);
            assertThat(result.issuedCoupons()).hasSize(10);
            verify(saveCouponIssuePort, times(10)).save(any(CouponIssue.class));
            verify(saveCouponPolicyPort, times(10)).decrementStock(1L);
        }

        @Test
        @DisplayName("검증 스킵 옵션으로 발급 성공")
        void issueWithSkipValidation() {
            // given
            CouponPolicy expiredPolicy = CouponPolicyFixture.createExpiredPolicy();
            expiredPolicy.setCurrentIssueCount(new AtomicInteger(0));
            command = DirectIssueCouponCommand.builder()
                    .couponPolicyId(4L)
                    .userIds(List.of(100L))
                    .issuedBy("admin")
                    .reason("특별 발급")
                    .quantityPerUser(1)
                    .skipValidation(true) // 검증 스킵
                    .build();

            when(loadCouponPolicyPort.loadById(4L)).thenReturn(Optional.of(expiredPolicy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(saveCouponPolicyPort.decrementStock(4L)).thenReturn(true);

            // when
            DirectIssueResult result = couponDirectIssueService.directIssue(command);

            // then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.successCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCase {

        @Test
        @DisplayName("쿠폰 정책을 찾을 수 없는 경우")
        void policyNotFound() {
            // given
            command = DirectIssueCouponCommand.forSingleUser(999L, 100L, "admin", "테스트");
            when(loadCouponPolicyPort.loadById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponDirectIssueService.directIssue(command))
                    .isInstanceOf(CouponDomainException.CouponNotFound.class);
        }

        @Test
        @DisplayName("DIRECT 타입이 아닌 쿠폰 정책인 경우")
        void notDirectType() {
            // given
            CouponPolicy codePolicy = CouponPolicyFixture.createCodePolicy();
            command = DirectIssueCouponCommand.forSingleUser(1L, 100L, "admin", "테스트");
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(codePolicy));

            // when & then
            assertThatThrownBy(() -> couponDirectIssueService.directIssue(command))
                    .isInstanceOf(CouponDomainException.class)
                    .hasMessageContaining("DIRECT 타입이 아닙니다");
        }

        @Test
        @DisplayName("비활성화된 쿠폰 정책인 경우")
        void inactivePolicy() {
            // given
            CouponPolicy inactivePolicy = CouponPolicyFixture.createInactivePolicy();
            command = DirectIssueCouponCommand.forSingleUser(7L, 100L, "admin", "테스트");
            when(loadCouponPolicyPort.loadById(7L)).thenReturn(Optional.of(inactivePolicy));

            // when & then
            assertThatThrownBy(() -> couponDirectIssueService.directIssue(command))
                    .isInstanceOf(CouponDomainException.class)
                    .hasMessageContaining("비활성화된 쿠폰 정책");
        }

        @Test
        @DisplayName("재고가 부족한 경우")
        void insufficientStock() {
            // given
            directPolicy.setCurrentIssueCount(new AtomicInteger(998)); // 남은 재고 2개
            command = DirectIssueCouponCommand.forMultipleUsers(
                    1L, Arrays.asList(100L, 101L, 102L), "admin", "테스트", 1
            );
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(directPolicy));

            // when
            DirectIssueResult result = couponDirectIssueService.directIssue(command);

            // then
            assertThat(result.isFullySuccessful()).isFalse();
            assertThat(result.successCount()).isEqualTo(0);
            assertThat(result.failedCount()).isEqualTo(3);
            assertThat(result.failures()).hasSize(3);
            assertThat(result.failures().get(0).errorCode()).isEqualTo("STOCK_EXHAUSTED");
        }

        @Test
        @DisplayName("사용자별 발급 제한 초과")
        void userLimitExceeded() {
            // given
            command = DirectIssueCouponCommand.forSingleUser(1L, 100L, "admin", "테스트");
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(directPolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L)).thenReturn(3); // 이미 3개 발급됨

            // when & then
            assertThatThrownBy(() -> couponDirectIssueService.directIssue(command))
                    .isInstanceOf(CouponDomainException.UserCouponLimitExceeded.class);
        }
    }

    @Nested
    @DisplayName("부분 성공 케이스")
    class PartialSuccessCase {

        @Test
        @DisplayName("일부 사용자만 발급 성공")
        void partialSuccess() {
            // given
            List<Long> userIds = Arrays.asList(100L, 101L, 102L);
            command = DirectIssueCouponCommand.forMultipleUsers(
                    1L, userIds, "admin", "부분 발급", 1
            );
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(directPolicy));

            // 100번 사용자: 성공
            when(loadCouponIssuePort.countUserIssuance(100L, 1L)).thenReturn(0);

            // 101번 사용자: 이미 제한 초과
            when(loadCouponIssuePort.countUserIssuance(101L, 1L)).thenReturn(3);

            // 102번 사용자: 성공
            when(loadCouponIssuePort.countUserIssuance(102L, 1L)).thenReturn(0);

            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(saveCouponPolicyPort.decrementStock(1L)).thenReturn(true);

            // when
            DirectIssueResult result = couponDirectIssueService.directIssue(command);

            // then
            assertThat(result.isPartiallySuccessful()).isTrue();
            assertThat(result.successCount()).isEqualTo(2);
            assertThat(result.failedCount()).isEqualTo(1);
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0).userId()).isEqualTo(101L);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시 발급 요청 시 재고 관리")
        void concurrentIssue() throws InterruptedException {
            // given
            int threadCount = 10;
            int issuePerThread = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            CouponPolicy policy = CouponPolicyFixture.createDirectPolicy("동시성 테스트", 50);
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(policy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(saveCouponPolicyPort.decrementStock(1L))
                    .thenAnswer(invocation -> {
                        int current = policy.getCurrentIssueCount().get();
                        if (current < policy.getMaxIssueCount()) {
                            policy.getCurrentIssueCount().incrementAndGet();
                            return true;
                        }
                        return false;
                    });

            // when
            for (int i = 0; i < threadCount; i++) {
                final int userId = 100 + i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < issuePerThread; j++) {
                            DirectIssueCouponCommand cmd = DirectIssueCouponCommand.forSingleUser(
                                    1L, (long) userId, "admin", "동시 발급"
                            );
                            try {
                                DirectIssueResult result = couponDirectIssueService.directIssue(cmd);
                                if (result.isFullySuccessful()) {
                                    successCount.incrementAndGet();
                                } else {
                                    failureCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                failureCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // then
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // 전체 발급 시도: 100개, 성공: 50개 (재고 한계)
            assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount * issuePerThread);
            assertThat(policy.getCurrentIssueCount().get()).isLessThanOrEqualTo(50);
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCase {

        @Test
        @DisplayName("빈 사용자 목록으로 발급 시도")
        void emptyUserList() {
            // given
            command = DirectIssueCouponCommand.builder()
                    .couponPolicyId(1L)
                    .userIds(List.of())
                    .issuedBy("admin")
                    .reason("빈 리스트")
                    .quantityPerUser(1)
                    .build();
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(directPolicy));

            // when
            DirectIssueResult result = couponDirectIssueService.directIssue(command);

            // then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.successCount()).isEqualTo(0);
            assertThat(result.requestedCount()).isEqualTo(0);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("잘못된 수량으로 발급 시도")
        void invalidQuantity(int quantity) {
            // given & when & then
            assertThatThrownBy(() ->
                    DirectIssueCouponCommand.builder()
                            .couponPolicyId(1L)
                            .userIds(List.of(100L))
                            .issuedBy("admin")
                            .reason("테스트")
                            .quantityPerUser(quantity)
                            .build()
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null 값 처리")
        void handleNullValues() {
            // given
            command = DirectIssueCouponCommand.builder()
                    .couponPolicyId(1L)
                    .userIds(List.of(100L))
                    .issuedBy("admin")
                    .reason(null) // null reason
                    .quantityPerUser(1)
                    .build();
            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(directPolicy));
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(saveCouponPolicyPort.decrementStock(1L)).thenReturn(true);

            // when
            DirectIssueResult result = couponDirectIssueService.directIssue(command);

            // then
            assertThat(result.isFullySuccessful()).isTrue();
        }
    }

    @Nested
    @DisplayName("성능 검증")
    class PerformanceTest {

        @Test
        @DisplayName("대량 발급 성능 테스트")
        void bulkIssuePerformance() {
            // given
            int userCount = 1000;
            List<Long> userIds = Stream.iterate(1L, n -> n + 1)
                    .limit(userCount)
                    .toList();

            CouponPolicy policy = CouponPolicyFixture.createDirectPolicy("대량 발급", null);
            command = DirectIssueCouponCommand.forMultipleUsers(
                    1L, userIds, "admin", "성능 테스트", 1
            );

            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(policy));
            when(loadCouponIssuePort.countUserIssuance(anyLong(), eq(1L))).thenReturn(0);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(saveCouponPolicyPort.decrementStock(1L)).thenReturn(true);

            // when
            long startTime = System.currentTimeMillis();
            DirectIssueResult result = couponDirectIssueService.directIssue(command);
            long endTime = System.currentTimeMillis();

            // then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.successCount()).isEqualTo(userCount);
            assertThat(endTime - startTime).isLessThan(5000); // 5초 이내 완료
            System.out.println("대량 발급 소요 시간: " + (endTime - startTime) + "ms");
        }
    }

    // 파라미터 제공 메서드
    private static Stream<Arguments> provideInvalidCommands() {
        return Stream.of(
                Arguments.of(null, "Null command"),
                Arguments.of(
                        DirectIssueCouponCommand.builder()
                                .couponPolicyId(null)
                                .userIds(List.of(100L))
                                .issuedBy("admin")
                                .reason("test")
                                .quantityPerUser(1)
                                .build(),
                        "Null policy ID"
                ),
                Arguments.of(
                        DirectIssueCouponCommand.builder()
                                .couponPolicyId(1L)
                                .userIds(null)
                                .issuedBy("admin")
                                .reason("test")
                                .quantityPerUser(1)
                                .build(),
                        "Null user IDs"
                )
        );
    }
}
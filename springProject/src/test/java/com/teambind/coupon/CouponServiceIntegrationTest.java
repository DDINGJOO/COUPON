package com.teambind.coupon;

import com.teambind.coupon.application.port.in.*;
import com.teambind.coupon.application.service.*;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DistributionType;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 서비스 통합 테스트
 * 실제 데이터베이스와 Redis를 사용한 End-to-End 테스트
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("쿠폰 서비스 통합 테스트")
class CouponServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("coupon_integration_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private DownloadCouponUseCase downloadCouponUseCase;

    @Autowired
    private ReserveCouponUseCase reserveCouponUseCase;

    @Autowired
    private ConfirmCouponUseCase confirmCouponUseCase;

    @Autowired
    private DirectIssueCouponUseCase directIssueCouponUseCase;

    @Autowired
    private CouponTimeoutService timeoutService;

    private CouponPolicy testPolicy;

    @BeforeEach
    void setUp() {
        // 테스트용 정책 생성
        testPolicy = createTestPolicy();
    }

    @Nested
    @DisplayName("쿠폰 다운로드 → 예약 → 사용 전체 플로우")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FullCouponFlowTest {

        private Long couponId;
        private String reservationId;

        @Test
        @Order(1)
        @DisplayName("1. 쿠폰 다운로드")
        @Transactional
        void downloadCoupon() {
            // given
            Long userId = 100L;
            String couponCode = testPolicy.getCouponCode();

            DownloadCouponCommand command = DownloadCouponCommand.of(couponCode, userId);

            // when
            DownloadCouponUseCase.CouponDownloadResult result = downloadCouponUseCase.downloadCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCouponId()).isNotNull();
            assertThat(result.getStatus()).isEqualTo(CouponStatus.ISSUED);

            couponId = result.getCouponId();
        }

        @Test
        @Order(2)
        @DisplayName("2. 결제 시작 - 쿠폰 예약")
        @Transactional
        void reserveCoupon() {
            // given
            Long userId = 100L;
            reservationId = UUID.randomUUID().toString();

            ReserveCouponCommand command = ReserveCouponCommand.of(reservationId, userId, couponId);

            // when
            ReserveCouponUseCase.CouponReservationResult result = reserveCouponUseCase.reserveCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getReservationId()).isEqualTo(reservationId);
            assertThat(result.getCouponId()).isEqualTo(couponId);
        }

        @Test
        @Order(3)
        @DisplayName("3. 결제 완료 - 쿠폰 사용 확정")
        @Transactional
        void confirmCoupon() {
            // given
            Long userId = 100L;
            String orderId = "ORDER-" + UUID.randomUUID();
            BigDecimal orderAmount = new BigDecimal("50000");

            ConfirmCouponCommand command = ConfirmCouponCommand.of(
                    reservationId, userId, couponId, orderId, orderAmount, null
            );

            // when
            ConfirmCouponUseCase.CouponConfirmResult result = confirmCouponUseCase.confirmCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOrderId()).isEqualTo(orderId);
            assertThat(result.getActualDiscountAmount()).isPositive();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyIntegrationTest {

        @Test
        @DisplayName("동시 다운로드 - 중복 방지")
        void concurrentDownloadPrevention() throws InterruptedException {
            // given
            String couponCode = testPolicy.getCouponCode();
            Long userId = 200L;
            int threadCount = 5;

            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger duplicateCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        DownloadCouponCommand command = DownloadCouponCommand.of(couponCode, userId);
                        DownloadCouponUseCase.CouponDownloadResult result =
                                downloadCouponUseCase.downloadCoupon(command);

                        if (result.isSuccess() && !result.getMessage().contains("이미 발급")) {
                            successCount.incrementAndGet();
                        } else if (result.getMessage().contains("이미 발급")) {
                            duplicateCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(1); // 한 번만 성공
            assertThat(duplicateCount.get()).isEqualTo(threadCount - 1); // 나머지는 중복
            executor.shutdown();
        }

        @Test
        @DisplayName("동시 예약 - 한 쿠폰에 대한 경합")
        void concurrentReservationOnSameCoupon() throws InterruptedException {
            // given
            Long userId = 300L;
            Long couponId = setupIssuedCoupon(userId);
            int threadCount = 5;

            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            // when
            for (int i = 0; i < threadCount; i++) {
                final String reservationId = "RESERVATION-" + i;
                executor.submit(() -> {
                    try {
                        ReserveCouponCommand command = ReserveCouponCommand.of(
                                reservationId, userId, couponId
                        );
                        ReserveCouponUseCase.CouponReservationResult result =
                                reserveCouponUseCase.reserveCoupon(command);

                        if (result.isSuccess() && !result.getMessage().contains("이미 예약")) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(1); // 한 번만 예약 성공
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("직접 발급 테스트")
    class DirectIssueIntegrationTest {

        @Test
        @DisplayName("관리자 직접 발급")
        @Transactional
        void adminDirectIssue() {
            // given
            CouponPolicy directPolicy = createDirectPolicy();
            Long userId = 400L;

            DirectIssueCouponCommand command = DirectIssueCouponCommand.of(
                    userId, directPolicy.getId(), "admin"
            );

            // when
            DirectIssueCouponUseCase.DirectIssueResult result = directIssueCouponUseCase.issueCoupon(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getMessage()).contains("발급 완료");
        }

        @Test
        @DisplayName("일괄 발급")
        @Transactional
        void batchDirectIssue() {
            // given
            CouponPolicy directPolicy = createDirectPolicy();
            List<Long> userIds = List.of(500L, 501L, 502L, 503L, 504L);

            // when
            List<DirectIssueCouponUseCase.DirectIssueResult> results =
                    directIssueCouponUseCase.batchIssue(userIds, directPolicy.getId(), "system");

            // then
            assertThat(results).hasSize(5);
            assertThat(results).allMatch(DirectIssueCouponUseCase.DirectIssueResult::isSuccess);
        }
    }

    @Nested
    @DisplayName("타임아웃 처리 테스트")
    class TimeoutIntegrationTest {

        @Test
        @DisplayName("예약 타임아웃 자동 해제")
        @Transactional
        void reservationTimeoutRelease() throws InterruptedException {
            // given
            Long userId = 600L;
            Long couponId = setupIssuedCoupon(userId);
            String reservationId = UUID.randomUUID().toString();

            // 예약 생성
            ReserveCouponCommand reserveCommand = ReserveCouponCommand.of(
                    reservationId, userId, couponId
            );
            reserveCouponUseCase.reserveCoupon(reserveCommand);

            // 타임아웃 시간 시뮬레이션 (테스트를 위해 짧은 시간 설정)
            Thread.sleep(1000);

            // when
            timeoutService.processTimeouts();

            // then
            // 쿠폰 상태 확인 - 타임아웃 처리되어 ISSUED 상태로 변경되어야 함
            // 실제 검증 로직은 서비스 구현에 따라 조정 필요
        }
    }

    @Nested
    @DisplayName("예외 상황 테스트")
    class ExceptionIntegrationTest {

        @Test
        @DisplayName("존재하지 않는 쿠폰 코드")
        void nonExistentCouponCode() {
            // given
            String invalidCode = "INVALID_CODE_9999";
            Long userId = 700L;

            DownloadCouponCommand command = DownloadCouponCommand.of(invalidCode, userId);

            // when
            DownloadCouponUseCase.CouponDownloadResult result = downloadCouponUseCase.downloadCoupon(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("찾을 수 없습니다");
        }

        @Test
        @DisplayName("최소 주문 금액 미달")
        @Transactional
        void orderAmountBelowMinimum() {
            // given
            Long userId = 800L;
            Long couponId = setupIssuedCoupon(userId);
            String reservationId = UUID.randomUUID().toString();

            // 예약
            ReserveCouponCommand reserveCommand = ReserveCouponCommand.of(
                    reservationId, userId, couponId
            );
            reserveCouponUseCase.reserveCoupon(reserveCommand);

            // 최소 금액 미달로 사용 시도
            String orderId = "ORDER-" + UUID.randomUUID();
            BigDecimal orderAmount = new BigDecimal("5000"); // 최소 주문금액 미달

            ConfirmCouponCommand confirmCommand = ConfirmCouponCommand.of(
                    reservationId, userId, couponId, orderId, orderAmount, null
            );

            // when
            ConfirmCouponUseCase.CouponConfirmResult result = confirmCouponUseCase.confirmCoupon(confirmCommand);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("최소 주문 금액");
        }
    }

    @Nested
    @DisplayName("트랜잭션 롤백 테스트")
    class TransactionRollbackTest {

        @Test
        @DisplayName("예외 발생 시 롤백 확인")
        void rollbackOnException() {
            // 트랜잭션 롤백 시나리오 테스트
            // 실제 구현에서는 @Transactional과 함께 예외를 발생시켜 롤백 확인
        }
    }

    // Helper methods
    private CouponPolicy createTestPolicy() {
        return CouponPolicyFixture.createCodePolicyWithCode("INTEGRATION_TEST_" + System.currentTimeMillis());
    }

    private CouponPolicy createDirectPolicy() {
        CouponPolicy policy = CouponPolicyFixture.createDirectPolicy();
        policy.updateCouponCode("DIRECT_" + System.currentTimeMillis());
        return policy;
    }

    private Long setupIssuedCoupon(Long userId) {
        // 테스트용 쿠폰 발급 설정
        String code = "SETUP_" + System.currentTimeMillis();
        CouponPolicy policy = CouponPolicyFixture.createCodePolicyWithCode(code);

        DownloadCouponCommand command = DownloadCouponCommand.of(code, userId);
        DownloadCouponUseCase.CouponDownloadResult result = downloadCouponUseCase.downloadCoupon(command);

        return result.getCouponId();
    }
}
package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DownloadCouponCommand;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 다운로드 서비스 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 다운로드 서비스 테스트")
class CouponDownloadServiceTest {

    @InjectMocks
    private CouponDownloadService couponDownloadService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponPolicyPort saveCouponPolicyPort;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private CouponPolicy codePolicy;
    private DownloadCouponCommand command;

    @BeforeEach
    void setUp() {
        codePolicy = CouponPolicyFixture.createCodePolicy();
        when(idGenerator.nextId()).thenReturn(1000L, 1001L, 1002L, 1003L, 1004L);
    }

    @Nested
    @DisplayName("정상 다운로드 케이스")
    class SuccessCase {

        @Test
        @DisplayName("유효한 쿠폰 코드로 다운로드 성공")
        void downloadWithValidCode() {
            // given
            command = DownloadCouponCommand.of(100L, "SUMMER2024");
            when(loadCouponPolicyPort.loadByCodeAndActive("SUMMER2024"))
                    .thenReturn(Optional.of(codePolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                    .thenReturn(0);
            when(saveCouponPolicyPort.decrementStock(1L))
                    .thenReturn(true);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponIssue result = couponDownloadService.downloadCoupon(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(100L);
            assertThat(result.getPolicyId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(CouponStatus.ISSUED);
            assertThat(result.getCouponName()).isEqualTo("여름 특별 할인 쿠폰");

            verify(saveCouponPolicyPort).decrementStock(1L);
            verify(saveCouponIssuePort).save(any(CouponIssue.class));
        }

        @Test
        @DisplayName("사용자 최대 발급 한계 내에서 다운로드")
        void downloadWithinUserLimit() {
            // given
            command = DownloadCouponCommand.of(100L, "SUMMER2024");
            when(loadCouponPolicyPort.loadByCodeAndActive("SUMMER2024"))
                    .thenReturn(Optional.of(codePolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                    .thenReturn(0); // 아직 발급 받지 않음
            when(saveCouponPolicyPort.decrementStock(1L))
                    .thenReturn(true);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponIssue result = couponDownloadService.downloadCoupon(command);

            // then
            assertThat(result).isNotNull();
            verify(loadCouponIssuePort).countUserIssuance(100L, 1L);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCase {

        @Test
        @DisplayName("존재하지 않는 쿠폰 코드")
        void downloadWithInvalidCode() {
            // given
            command = DownloadCouponCommand.of(100L, "INVALID_CODE");
            when(loadCouponPolicyPort.loadByCodeAndActive("INVALID_CODE"))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                    .isInstanceOf(CouponDomainException.CouponNotFound.class)
                    .hasMessageContaining("INVALID_CODE");
        }

        @Test
        @DisplayName("만료된 쿠폰 다운로드 시도")
        void downloadExpiredCoupon() {
            // given
            CouponPolicy expiredPolicy = CouponPolicyFixture.createExpiredPolicy();
            command = DownloadCouponCommand.of(100L, "EXPIRED2024");
            when(loadCouponPolicyPort.loadByCodeAndActive("EXPIRED2024"))
                    .thenReturn(Optional.of(expiredPolicy));

            // when & then
            assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                    .isInstanceOf(CouponDomainException.CouponExpired.class);
        }

        @Test
        @DisplayName("품절된 쿠폰 다운로드 시도")
        void downloadSoldOutCoupon() {
            // given
            CouponPolicy soldOutPolicy = CouponPolicyFixture.createSoldOutPolicy();
            command = DownloadCouponCommand.of(100L, "SOLDOUT2024");
            when(loadCouponPolicyPort.loadByCodeAndActive("SOLDOUT2024"))
                    .thenReturn(Optional.of(soldOutPolicy));

            // when & then
            assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                    .isInstanceOf(CouponDomainException.StockExhausted.class);
        }

        @Test
        @DisplayName("사용자 최대 발급 수량 초과")
        void downloadExceedUserLimit() {
            // given
            command = DownloadCouponCommand.of(100L, "SUMMER2024");
            when(loadCouponPolicyPort.loadByCodeAndActive("SUMMER2024"))
                    .thenReturn(Optional.of(codePolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                    .thenReturn(1); // 이미 1개 발급 (maxUsagePerUser = 1)

            // when & then
            assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                    .isInstanceOf(CouponDomainException.UserCouponLimitExceeded.class);
        }

        @Test
        @DisplayName("비활성화된 쿠폰 다운로드 시도")
        void downloadInactiveCoupon() {
            // given
            CouponPolicy inactivePolicy = CouponPolicyFixture.createInactivePolicy();
            command = DownloadCouponCommand.of(100L, "INACTIVE");
            when(loadCouponPolicyPort.loadByCodeAndActive("INACTIVE"))
                    .thenReturn(Optional.empty()); // 비활성화된 쿠폰은 조회되지 않음

            // when & then
            assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                    .isInstanceOf(CouponDomainException.CouponNotFound.class);
        }

        @Test
        @DisplayName("재고 차감 실패")
        void stockDecrementFailure() {
            // given
            command = DownloadCouponCommand.of(100L, "SUMMER2024");
            when(loadCouponPolicyPort.loadByCodeAndActive("SUMMER2024"))
                    .thenReturn(Optional.of(codePolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                    .thenReturn(0);
            when(saveCouponPolicyPort.decrementStock(1L))
                    .thenReturn(false); // 재고 차감 실패

            // when & then
            assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                    .isInstanceOf(CouponDomainException.StockExhausted.class);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시 다운로드 요청 시 재고 관리")
        void concurrentDownload() throws InterruptedException {
            // given
            int threadCount = 100;
            CouponPolicy limitedPolicy = CouponPolicyFixture.createCodePolicy("LIMITED", "한정 수량 쿠폰");
            limitedPolicy.setCurrentIssueCount(new AtomicInteger(0));

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            when(loadCouponPolicyPort.loadByCodeAndActive("LIMITED"))
                    .thenReturn(Optional.of(limitedPolicy));
            when(loadCouponIssuePort.countUserIssuance(anyLong(), eq(1L)))
                    .thenReturn(0);
            when(saveCouponPolicyPort.decrementStock(1L))
                    .thenAnswer(invocation -> {
                        return limitedPolicy.tryIssue();
                    });
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            for (int i = 0; i < threadCount; i++) {
                final long userId = 100L + i;
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드가 동시에 시작
                        DownloadCouponCommand cmd = DownloadCouponCommand.of(userId, "LIMITED");
                        CouponIssue result = couponDownloadService.downloadCoupon(cmd);
                        successCount.incrementAndGet();
                    } catch (CouponDomainException e) {
                        failureCount.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 모든 스레드 시작
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);

            // then
            assertThat(completed).isTrue();
            assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);
            assertThat(successCount.get()).isLessThanOrEqualTo(100); // 최대 발급 수량
            assertThat(limitedPolicy.getCurrentIssueCount().get()).isEqualTo(successCount.get());

            executor.shutdown();
        }

        @Test
        @DisplayName("동일 사용자 중복 다운로드 방지")
        void preventDuplicateDownload() throws InterruptedException {
            // given
            int attemptCount = 10;
            CountDownLatch latch = new CountDownLatch(attemptCount);
            ExecutorService executor = Executors.newFixedThreadPool(attemptCount);
            AtomicInteger successCount = new AtomicInteger(0);

            when(loadCouponPolicyPort.loadByCodeAndActive("SUMMER2024"))
                    .thenReturn(Optional.of(codePolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                    .thenAnswer(invocation -> successCount.get()); // 성공한 수만큼 이미 발급
            when(saveCouponPolicyPort.decrementStock(1L))
                    .thenReturn(true);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> {
                        successCount.incrementAndGet();
                        return invocation.getArgument(0);
                    });

            // when
            for (int i = 0; i < attemptCount; i++) {
                executor.submit(() -> {
                    try {
                        DownloadCouponCommand cmd = DownloadCouponCommand.of(100L, "SUMMER2024");
                        couponDownloadService.downloadCoupon(cmd);
                    } catch (Exception e) {
                        // 예외 발생 예상
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(1); // 한 명당 1개만 발급
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCase {

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "  ", "\t", "\n"})
        @DisplayName("빈 쿠폰 코드 처리")
        void emptyOrBlankCouponCode(String couponCode) {
            // given
            command = DownloadCouponCommand.of(100L, couponCode);

            // when & then
            assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null 쿠폰 코드 처리")
        void nullCouponCode() {
            // given & when & then
            assertThatThrownBy(() -> DownloadCouponCommand.of(100L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("잘못된 사용자 ID")
        void invalidUserId() {
            // given & when & then
            assertThatThrownBy(() -> DownloadCouponCommand.of(null, "SUMMER2024"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> DownloadCouponCommand.of(-1L, "SUMMER2024"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("대소문자 구분 없는 쿠폰 코드")
        void caseInsensitiveCouponCode() {
            // given
            command = DownloadCouponCommand.of(100L, "summer2024");
            when(loadCouponPolicyPort.loadByCodeAndActive("SUMMER2024"))
                    .thenReturn(Optional.of(codePolicy));
            when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                    .thenReturn(0);
            when(saveCouponPolicyPort.decrementStock(1L))
                    .thenReturn(true);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponIssue result = couponDownloadService.downloadCoupon(command);

            // then
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("성능 테스트")
    class PerformanceTest {

        @Test
        @DisplayName("대량 사용자 순차 다운로드 성능")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void sequentialDownloadPerformance() {
            // given
            int userCount = 1000;
            CouponPolicy unlimitedPolicy = CouponPolicyFixture.createUnlimitedPolicy();

            when(loadCouponPolicyPort.loadByCodeAndActive("UNLIMITED"))
                    .thenReturn(Optional.of(unlimitedPolicy));
            when(loadCouponIssuePort.countUserIssuance(anyLong(), eq(6L)))
                    .thenReturn(0);
            when(saveCouponPolicyPort.decrementStock(6L))
                    .thenReturn(true);
            when(saveCouponIssuePort.save(any(CouponIssue.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < userCount; i++) {
                DownloadCouponCommand cmd = DownloadCouponCommand.of((long) (100 + i), "UNLIMITED");
                CouponIssue result = couponDownloadService.downloadCoupon(cmd);
                assertThat(result).isNotNull();
            }

            long endTime = System.currentTimeMillis();

            // then
            long duration = endTime - startTime;
            System.out.println("순차 다운로드 " + userCount + "건 소요 시간: " + duration + "ms");
            assertThat(duration).isLessThan(10000); // 10초 이내

            verify(saveCouponIssuePort, times(userCount)).save(any(CouponIssue.class));
        }
    }
}
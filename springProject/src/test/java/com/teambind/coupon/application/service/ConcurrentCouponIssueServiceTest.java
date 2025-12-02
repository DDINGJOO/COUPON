package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 동시성 제어 쿠폰 발급 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentCouponIssueServiceTest {

    @InjectMocks
    private ConcurrentCouponIssueService service;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private CouponStockService stockService;

    @Mock
    private RedisDistributedLock distributedLock;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private CouponPolicy testPolicy;

    @BeforeEach
    void setUp() {
        testPolicy = CouponPolicy.builder()
                .id(1L)
                .couponCode("TEST_COUPON")
                .couponName("테스트 쿠폰")
                .distributionType(DistributionType.EVENT)
                .maxIssueCount(100)
                .maxUsagePerUser(1)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();

        // 기본 currentIssueCount 설정
        testPolicy.getCurrentIssueCount().set(0);
    }

    @Test
    @DisplayName("정상적인 쿠폰 발급")
    void issueCoupon_success() {
        // given
        Long userId = 100L;
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(testPolicy));
        when(stockService.decrementStock(1L, 1)).thenReturn(true);
        when(stockService.incrementUserIssueCount(userId, 1L, 1)).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(12345L);
        when(saveCouponIssuePort.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        CouponIssue result = service.issueCoupon(1L, userId, "admin");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(CouponStatus.ISSUED);

        verify(stockService).decrementStock(1L, 1);
        verify(stockService).incrementUserIssueCount(userId, 1L, 1);
        verify(saveCouponIssuePort).save(any());
    }

    @Test
    @DisplayName("재고 부족 시 발급 실패")
    void issueCoupon_stockExhausted() {
        // given
        Long userId = 100L;
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(testPolicy));
        when(stockService.decrementStock(1L, 1)).thenReturn(false); // 재고 차감 실패

        // when & then
        assertThatThrownBy(() -> service.issueCoupon(1L, userId, "admin"))
                .isInstanceOf(CouponDomainException.StockExhausted.class);

        verify(stockService).decrementStock(1L, 1);
        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("사용자별 발급 한도 초과")
    void issueCoupon_userLimitExceeded() {
        // given
        Long userId = 100L;
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(testPolicy));
        when(stockService.decrementStock(1L, 1)).thenReturn(true);
        when(stockService.incrementUserIssueCount(userId, 1L, 1)).thenReturn(false); // 사용자 한도 초과

        // when & then
        assertThatThrownBy(() -> service.issueCoupon(1L, userId, "admin"))
                .isInstanceOf(CouponDomainException.UserCouponLimitExceeded.class);

        // 재고는 롤백되어야 함
        verify(stockService).incrementStock(1L, 1);
        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("배치 발급 성공")
    void batchIssueCoupons_success() {
        // given
        List<Long> userIds = Arrays.asList(100L, 101L, 102L);
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(testPolicy));
        when(stockService.batchDecrementStock(1L, userIds, 1)).thenReturn(true);
        when(stockService.incrementUserIssueCount(anyLong(), eq(1L), eq(1))).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(12345L, 12346L, 12347L);
        when(saveCouponIssuePort.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        ConcurrentCouponIssueService.BatchIssueResult result =
                service.batchIssueCoupons(1L, userIds, "admin");

        // then
        assertThat(result.getSucceeded()).isEqualTo(3);
        assertThat(result.getFailed()).isEqualTo(0);
        assertThat(result.isFullySuccessful()).isTrue();

        verify(stockService).batchDecrementStock(1L, userIds, 1);
        verify(saveCouponIssuePort, times(3)).save(any());
    }

    @Test
    @DisplayName("배치 발급 부분 실패")
    void batchIssueCoupons_partialFailure() {
        // given
        List<Long> userIds = Arrays.asList(100L, 101L, 102L);
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(testPolicy));
        when(stockService.batchDecrementStock(1L, userIds, 1)).thenReturn(true);

        // 첫 번째 사용자는 성공, 나머지는 한도 초과
        when(stockService.incrementUserIssueCount(100L, 1L, 1)).thenReturn(true);
        when(stockService.incrementUserIssueCount(101L, 1L, 1)).thenReturn(false);
        when(stockService.incrementUserIssueCount(102L, 1L, 1)).thenReturn(false);

        when(idGenerator.nextId()).thenReturn(12345L);
        when(saveCouponIssuePort.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        ConcurrentCouponIssueService.BatchIssueResult result =
                service.batchIssueCoupons(1L, userIds, "admin");

        // then
        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.isPartiallySuccessful()).isTrue();

        // 실패한 수량만큼 재고 복구
        verify(stockService).incrementStock(1L, 2);
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 - 중복 요청 방지")
    void issueFCFSCoupon_preventDuplicate() {
        // given
        Long userId = 100L;

        // 첫 번째 요청은 락 획득, 두 번째는 실패
        when(distributedLock.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true, false);

        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(testPolicy));
        when(stockService.decrementStock(1L, 1)).thenReturn(true);
        when(stockService.incrementUserIssueCount(userId, 1L, 1)).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(12345L);
        when(saveCouponIssuePort.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        CouponIssue result1 = service.issueFCFSCoupon(1L, userId, "admin");

        // 두 번째 요청은 락 획득 실패로 예외
        assertThatThrownBy(() -> service.issueFCFSCoupon(1L, userId, "admin"))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("이미 발급 요청이 진행 중입니다");

        // then
        assertThat(result1).isNotNull();
        verify(saveCouponIssuePort, times(1)).save(any()); // 한 번만 저장
    }

    @Test
    @DisplayName("동시 요청 시뮬레이션 - 재고 정확성")
    void concurrentIssuance_stockAccuracy() throws InterruptedException {
        // given
        int threadCount = 100;
        int stockCount = 50;
        // 새로운 정책 객체 생성
        CouponPolicy concurrentTestPolicy = CouponPolicy.builder()
                .id(1L)
                .couponCode("TEST_COUPON")
                .couponName("테스트 쿠폰")
                .distributionType(DistributionType.EVENT)
                .maxIssueCount(stockCount)
                .maxUsagePerUser(1)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(concurrentTestPolicy));
        when(idGenerator.nextId()).thenReturn(12345L);
        when(saveCouponIssuePort.save(any())).thenAnswer(i -> i.getArgument(0));

        // 재고 차감 시뮬레이션
        AtomicInteger remainingStock = new AtomicInteger(stockCount);
        when(stockService.decrementStock(1L, 1)).thenAnswer(i -> {
            int current = remainingStock.get();
            if (current > 0) {
                remainingStock.decrementAndGet();
                return true;
            }
            return false;
        });

        when(stockService.incrementUserIssueCount(anyLong(), eq(1L), eq(1))).thenReturn(true);

        // when
        for (int i = 0; i < threadCount; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    service.issueCoupon(1L, userId, "admin");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(stockCount);
        assertThat(failCount.get()).isEqualTo(threadCount - stockCount);
        assertThat(remainingStock.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("비활성화된 정책 검증")
    void validatePolicy_inactive() {
        // given
        CouponPolicy inactivePolicy = CouponPolicy.builder()
                .id(1L)
                .couponCode("TEST_COUPON")
                .couponName("테스트 쿠폰")
                .distributionType(DistributionType.EVENT)
                .maxIssueCount(100)
                .maxUsagePerUser(1)
                .isActive(false)  // 비활성화
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(inactivePolicy));

        // when & then
        assertThatThrownBy(() -> service.issueCoupon(1L, 100L, "admin"))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("비활성화된 쿠폰 정책입니다");

        verify(stockService, never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("만료된 정책 검증")
    void validatePolicy_expired() {
        // given
        CouponPolicy expiredPolicy = CouponPolicy.builder()
                .id(1L)
                .couponCode("TEST_COUPON")
                .couponName("테스트 쿠폰")
                .distributionType(DistributionType.EVENT)
                .maxIssueCount(100)
                .maxUsagePerUser(1)
                .isActive(true)
                .validFrom(LocalDateTime.now().minusDays(30))
                .validUntil(LocalDateTime.now().minusDays(1))  // 만료됨
                .build();
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(expiredPolicy));

        // when & then
        assertThatThrownBy(() -> service.issueCoupon(1L, 100L, "admin"))
                .isInstanceOf(CouponDomainException.CouponExpired.class);

        verify(stockService, never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("재고 롤백 테스트")
    void stockRollback_onFailure() {
        // given
        Long userId = 100L;
        when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(testPolicy));
        when(stockService.decrementStock(1L, 1)).thenReturn(true);
        when(stockService.incrementUserIssueCount(userId, 1L, 1)).thenReturn(true);
        when(idGenerator.nextId()).thenReturn(12345L);

        // DB 저장 실패 시뮬레이션
        when(saveCouponIssuePort.save(any())).thenThrow(new RuntimeException("DB Error"));

        // when & then
        assertThatThrownBy(() -> service.issueCoupon(1L, userId, "admin"))
                .isInstanceOf(RuntimeException.class);

        // 재고 롤백 확인
        verify(stockService).incrementStock(1L, 1);
    }
}
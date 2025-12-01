package com.teambind.coupon.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.coupon.IntegrationTestBase;
import com.teambind.coupon.adapter.in.web.dto.*;
import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.domain.model.DiscountPolicy;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import com.teambind.coupon.domain.model.ItemApplicableRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * 쿠폰 시스템 E2E 통합 테스트
 * 실제 시나리오를 기반으로 한 전체 플로우 테스트
 */
@DisplayName("쿠폰 시스템 E2E 통합 테스트")
public class CouponE2EIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CouponPolicyRepository policyRepository;

    @Autowired
    private CouponIssueRepository issueRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private CouponPolicyEntity codePolicy;
    private CouponPolicyEntity directPolicy;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        issueRepository.deleteAll();
        policyRepository.deleteAll();

        // CODE 타입 쿠폰 정책 생성
        codePolicy = policyRepository.save(CouponPolicyEntity.builder()
                .couponName("E2E 테스트 CODE 쿠폰")
                .couponCode("E2ETEST2024")
                .description("E2E 통합 테스트용 CODE 쿠폰")
                .discountPolicy(DiscountPolicy.builder()
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(new BigDecimal("10"))
                        .minOrderAmount(new BigDecimal("10000"))
                        .maxDiscountAmount(new BigDecimal("5000"))
                        .build())
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(100)
                .maxUsagePerUser(2)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build());

        // DIRECT 타입 쿠폰 정책 생성
        directPolicy = policyRepository.save(CouponPolicyEntity.builder()
                .couponName("E2E 테스트 DIRECT 쿠폰")
                .description("E2E 통합 테스트용 DIRECT 쿠폰")
                .discountPolicy(DiscountPolicy.builder()
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(new BigDecimal("5000"))
                        .minOrderAmount(new BigDecimal("30000"))
                        .build())
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(500)
                .maxUsagePerUser(3)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build());
    }

    @Nested
    @DisplayName("쿠폰 다운로드 시나리오")
    class DownloadScenario {

        @Test
        @DisplayName("전체 다운로드 플로우: 다운로드 → 예약 → 결제 완료 → 사용")
        void completeDownloadFlow() throws Exception {
            // 1. 쿠폰 다운로드
            DownloadCouponRequest downloadRequest = DownloadCouponRequest.builder()
                    .userId(100L)
                    .couponCode("E2ETEST2024")
                    .build();

            MvcResult downloadResult = mockMvc.perform(post("/api/coupons/download")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(downloadRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ISSUED"))
                    .andExpect(jsonPath("$.couponName").value("E2E 테스트 CODE 쿠폰"))
                    .andReturn();

            CouponIssueResponse issueResponse = objectMapper.readValue(
                    downloadResult.getResponse().getContentAsString(),
                    CouponIssueResponse.class
            );

            Long couponId = issueResponse.getCouponId();

            // 2. 쿠폰 예약
            String reservationId = UUID.randomUUID().toString();
            ReserveCouponRequest reserveRequest = ReserveCouponRequest.builder()
                    .userId(100L)
                    .couponId(couponId)
                    .reservationId(reservationId)
                    .build();

            mockMvc.perform(post("/api/coupons/reserve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reserveRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.couponId").value(couponId))
                    .andExpect(jsonPath("$.reservationId").value(reservationId));

            // 3. 결제 완료 이벤트 발송 (Kafka)
            Map<String, Object> paymentEvent = Map.of(
                    "orderId", "ORDER-E2E-001",
                    "reservationId", reservationId,
                    "userId", 100L,
                    "couponId", couponId,
                    "paymentAmount", 50000,
                    "discountAmount", 5000,
                    "completedAt", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("payment.completed", paymentEvent);

            // 4. 처리 대기 및 검증
            Thread.sleep(2000); // 이벤트 처리 대기

            CouponIssueEntity usedCoupon = issueRepository.findById(couponId)
                    .orElseThrow();

            assertThat(usedCoupon.getStatus().name()).isEqualTo("USED");
            assertThat(usedCoupon.getOrderId()).isEqualTo("ORDER-E2E-001");
            assertThat(usedCoupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("사용자별 다운로드 제한 테스트")
        void userDownloadLimit() throws Exception {
            Long userId = 200L;

            // 첫 번째 다운로드 - 성공
            DownloadCouponRequest request1 = DownloadCouponRequest.builder()
                    .userId(userId)
                    .couponCode("E2ETEST2024")
                    .build();

            mockMvc.perform(post("/api/coupons/download")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isCreated());

            // 두 번째 다운로드 - 성공 (maxUsagePerUser = 2)
            mockMvc.perform(post("/api/coupons/download")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isCreated());

            // 세 번째 다운로드 - 실패 (한도 초과)
            mockMvc.perform(post("/api/coupons/download")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("동시 다운로드 요청 처리")
        void concurrentDownload() throws Exception {
            int threadCount = 50;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<Boolean>> futures = new ArrayList<>();

            // 50명이 동시에 다운로드 시도
            for (int i = 0; i < threadCount; i++) {
                final int userId = 1000 + i;
                Future<Boolean> future = executor.submit(() -> {
                    try {
                        DownloadCouponRequest request = DownloadCouponRequest.builder()
                                .userId((long) userId)
                                .couponCode("E2ETEST2024")
                                .build();

                        MvcResult result = mockMvc.perform(post("/api/coupons/download")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andReturn();

                        return result.getResponse().getStatus() == 201;
                    } catch (Exception e) {
                        return false;
                    } finally {
                        latch.countDown();
                    }
                });
                futures.add(future);
            }

            latch.await(30, TimeUnit.SECONDS);

            // 결과 집계
            long successCount = futures.stream()
                    .map(f -> {
                        try {
                            return f.get() ? 1L : 0L;
                        } catch (Exception e) {
                            return 0L;
                        }
                    })
                    .mapToLong(Long::longValue)
                    .sum();

            // 재고 확인
            CouponPolicyEntity updatedPolicy = policyRepository.findById(codePolicy.getId())
                    .orElseThrow();

            assertThat(successCount).isLessThanOrEqualTo(100); // maxIssueCount = 100
            assertThat(issueRepository.countByPolicyId(codePolicy.getId()))
                    .isEqualTo(successCount);

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("직접 발급 시나리오")
    class DirectIssueScenario {

        @Test
        @DisplayName("관리자 대량 발급 시나리오")
        void bulkDirectIssue() throws Exception {
            List<Long> userIds = IntStream.rangeClosed(3000, 3099)
                    .mapToObj(Long::valueOf)
                    .toList();

            DirectIssueRequest request = DirectIssueRequest.builder()
                    .couponPolicyId(directPolicy.getId())
                    .userIds(userIds)
                    .issuedBy("admin@test.com")
                    .reason("E2E 테스트 대량 발급")
                    .quantityPerUser(2)
                    .skipValidation(false)
                    .build();

            mockMvc.perform(post("/api/coupons/direct-issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.requestedCount").value(200)) // 100명 * 2개
                    .andExpect(jsonPath("$.successCount").value(200))
                    .andExpect(jsonPath("$.failedCount").value(0))
                    .andExpect(jsonPath("$.issuedCoupons", hasSize(200)));

            // DB 검증
            assertThat(issueRepository.countByPolicyId(directPolicy.getId()))
                    .isEqualTo(200);
        }

        @Test
        @DisplayName("부분 성공 발급 시나리오")
        void partialSuccessDirectIssue() throws Exception {
            // 일부 사용자는 이미 최대 수량 도달하도록 설정
            Long userId1 = 4000L;
            Long userId2 = 4001L;
            Long userId3 = 4002L;

            // userId1에게 미리 3개 발급 (최대 도달)
            for (int i = 0; i < 3; i++) {
                issueRepository.save(CouponIssueEntity.builder()
                        .policyId(directPolicy.getId())
                        .userId(userId1)
                        .status("ISSUED")
                        .issuedAt(LocalDateTime.now())
                        .couponName(directPolicy.getCouponName())
                        .discountPolicy(directPolicy.getDiscountPolicy())
                        .build());
            }

            DirectIssueRequest request = DirectIssueRequest.builder()
                    .couponPolicyId(directPolicy.getId())
                    .userIds(List.of(userId1, userId2, userId3))
                    .issuedBy("admin@test.com")
                    .reason("부분 성공 테스트")
                    .quantityPerUser(1)
                    .skipValidation(false)
                    .build();

            mockMvc.perform(post("/api/coupons/direct-issue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isMultiStatus())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.successCount").value(2))
                    .andExpect(jsonPath("$.failedCount").value(1))
                    .andExpect(jsonPath("$.failures", hasSize(1)))
                    .andExpect(jsonPath("$.failures[0].userId").value(userId1));
        }
    }

    @Nested
    @DisplayName("결제 플로우 시나리오")
    class PaymentFlowScenario {

        @Test
        @DisplayName("결제 실패 후 쿠폰 복구 시나리오")
        void paymentFailureAndRecovery() throws Exception {
            // 1. 쿠폰 다운로드
            DownloadCouponRequest downloadRequest = DownloadCouponRequest.builder()
                    .userId(5000L)
                    .couponCode("E2ETEST2024")
                    .build();

            MvcResult downloadResult = mockMvc.perform(post("/api/coupons/download")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(downloadRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            CouponIssueResponse issueResponse = objectMapper.readValue(
                    downloadResult.getResponse().getContentAsString(),
                    CouponIssueResponse.class
            );

            Long couponId = issueResponse.getCouponId();

            // 2. 쿠폰 예약
            String reservationId = UUID.randomUUID().toString();
            ReserveCouponRequest reserveRequest = ReserveCouponRequest.builder()
                    .userId(5000L)
                    .couponId(couponId)
                    .reservationId(reservationId)
                    .build();

            mockMvc.perform(post("/api/coupons/reserve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reserveRequest)))
                    .andExpect(status().isOk());

            // 3. 결제 실패 이벤트 발송
            Map<String, Object> failureEvent = Map.of(
                    "orderId", "ORDER-FAIL-001",
                    "reservationId", reservationId,
                    "userId", 5000L,
                    "couponId", couponId,
                    "failureReason", "잔액 부족",
                    "failedAt", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("payment.failed", failureEvent);

            // 4. 처리 대기 및 검증
            Thread.sleep(2000);

            CouponIssueEntity releasedCoupon = issueRepository.findById(couponId)
                    .orElseThrow();

            assertThat(releasedCoupon.getStatus().name()).isEqualTo("ISSUED");
            assertThat(releasedCoupon.getReservationId()).isNull();
            assertThat(releasedCoupon.getReservedAt()).isNull();
        }

        @Test
        @DisplayName("지연 결제 처리 시나리오")
        void delayedPaymentProcessing() throws Exception {
            // 1. 쿠폰 다운로드
            DownloadCouponRequest downloadRequest = DownloadCouponRequest.builder()
                    .userId(6000L)
                    .couponCode("E2ETEST2024")
                    .build();

            MvcResult downloadResult = mockMvc.perform(post("/api/coupons/download")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(downloadRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            CouponIssueResponse issueResponse = objectMapper.readValue(
                    downloadResult.getResponse().getContentAsString(),
                    CouponIssueResponse.class
            );

            Long couponId = issueResponse.getCouponId();

            // 2. 쿠폰 예약
            String reservationId = UUID.randomUUID().toString();
            ReserveCouponRequest reserveRequest = ReserveCouponRequest.builder()
                    .userId(6000L)
                    .couponId(couponId)
                    .reservationId(reservationId)
                    .build();

            mockMvc.perform(post("/api/coupons/reserve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reserveRequest)))
                    .andExpect(status().isOk());

            // 3. 타임아웃 시뮬레이션 (DB 직접 업데이트)
            CouponIssueEntity entity = issueRepository.findById(couponId).orElseThrow();
            entity.setStatus("ISSUED");
            entity.setReservationId(null);
            entity.setReservedAt(null);
            issueRepository.save(entity);

            // 4. 지연 결제 완료 이벤트 발송 (reservationId 포함)
            Map<String, Object> delayedPaymentEvent = Map.of(
                    "orderId", "ORDER-DELAYED-001",
                    "reservationId", reservationId,
                    "userId", 6000L,
                    "couponId", couponId,
                    "paymentAmount", 50000,
                    "discountAmount", 5000,
                    "completedAt", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("payment.completed", delayedPaymentEvent);

            // 5. 처리 대기 및 검증
            Thread.sleep(2000);

            CouponIssueEntity usedCoupon = issueRepository.findById(couponId)
                    .orElseThrow();

            // 지연 결제도 정상 처리되어야 함
            assertThat(usedCoupon.getStatus().name()).isEqualTo("USED");
            assertThat(usedCoupon.getOrderId()).isEqualTo("ORDER-DELAYED-001");
        }
    }

    @Nested
    @DisplayName("성능 및 부하 테스트")
    class PerformanceTest {

        @Test
        @DisplayName("대량 동시 접속 시나리오")
        void massiveConcurrentAccess() throws Exception {
            int userCount = 200;
            int requestsPerUser = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(userCount);
            ExecutorService executor = Executors.newFixedThreadPool(50);

            List<Future<Long>> futures = new ArrayList<>();

            for (int i = 0; i < userCount; i++) {
                final int userId = 10000 + i;
                Future<Long> future = executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드가 동시 시작
                        long successCount = 0;

                        for (int j = 0; j < requestsPerUser; j++) {
                            try {
                                // 쿠폰 코드 유효성 확인
                                mockMvc.perform(get("/api/coupons/validate/E2ETEST2024"))
                                        .andExpect(status().isOk());
                                successCount++;
                            } catch (Exception e) {
                                // 실패 무시
                            }
                        }
                        return successCount;
                    } catch (Exception e) {
                        return 0L;
                    } finally {
                        endLatch.countDown();
                    }
                });
                futures.add(future);
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown(); // 모든 요청 시작

            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            assertThat(completed).isTrue();

            long totalSuccess = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            return 0L;
                        }
                    })
                    .mapToLong(Long::longValue)
                    .sum();

            long duration = endTime - startTime;
            double tps = (double) totalSuccess / (duration / 1000.0);

            System.out.println("=== 성능 테스트 결과 ===");
            System.out.println("총 요청 수: " + (userCount * requestsPerUser));
            System.out.println("성공 요청 수: " + totalSuccess);
            System.out.println("소요 시간: " + duration + "ms");
            System.out.println("TPS: " + String.format("%.2f", tps));

            assertThat(tps).isGreaterThan(100); // 최소 100 TPS 이상

            executor.shutdown();
        }
    }
}
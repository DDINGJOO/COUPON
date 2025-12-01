package com.teambind.coupon.adapter.in.message;

import com.teambind.coupon.adapter.in.message.dto.PaymentCompletedEvent;
import com.teambind.coupon.adapter.in.message.dto.PaymentFailedEvent;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase.PaymentResult;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.fixture.CouponIssueFixture;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 결제 이벤트 컨슈머 테스트
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {
                "payment.completed",
                "payment.failed",
                "payment.cancelled"
        },
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9092",
                "port=9092"
        }
)
@DisplayName("결제 이벤트 컨슈머 테스트")
class PaymentEventConsumerTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockBean
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private PaymentEventConsumer paymentEventConsumer;

    private KafkaTemplate<String, Object> kafkaTemplate;
    private CouponPolicy policy;

    @BeforeEach
    void setUp() {
        // Kafka 프로듀서 설정
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        policy = CouponPolicyFixture.createCodePolicy();
    }

    @Nested
    @DisplayName("결제 완료 이벤트")
    class PaymentCompletedEventTest {

        @Test
        @DisplayName("정상 결제 완료 처리")
        void handlePaymentCompleted() throws Exception {
            // given
            String reservationId = UUID.randomUUID().toString();
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .orderId("ORDER-123")
                    .reservationId(reservationId)
                    .userId(100L)
                    .couponId(1000L)
                    .paymentAmount(new BigDecimal("50000"))
                    .discountAmount(new BigDecimal("5000"))
                    .completedAt(LocalDateTime.now())
                    .build();

            CouponIssue reservedCoupon = CouponIssueFixture.createReservedCoupon(100L, policy, reservationId);
            PaymentResult expectedResult = new PaymentResult(
                    true, reservedCoupon, "결제 완료 처리 성공"
            );

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenReturn(expectedResult);

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return expectedResult;
            }).when(processPaymentUseCase).processPaymentCompleted(any());

            // when
            kafkaTemplate.send("payment.completed", event);

            // then
            boolean processed = latch.await(5, TimeUnit.SECONDS);
            assertThat(processed).isTrue();

            ArgumentCaptor<ProcessPaymentUseCase.PaymentCompletedCommand> captor =
                    ArgumentCaptor.forClass(ProcessPaymentUseCase.PaymentCompletedCommand.class);
            verify(processPaymentUseCase, timeout(5000)).processPaymentCompleted(captor.capture());

            ProcessPaymentUseCase.PaymentCompletedCommand capturedCommand = captor.getValue();
            assertThat(capturedCommand.orderId()).isEqualTo("ORDER-123");
            assertThat(capturedCommand.reservationId()).isEqualTo(reservationId);
            assertThat(capturedCommand.userId()).isEqualTo(100L);
            assertThat(capturedCommand.couponId()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("타임아웃 후 지연 결제 처리")
        void handleDelayedPayment() throws Exception {
            // given - 예약 후 35분 경과한 지연 결제
            String reservationId = UUID.randomUUID().toString();
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .orderId("ORDER-456")
                    .reservationId(reservationId)
                    .userId(100L)
                    .couponId(1001L)
                    .paymentAmount(new BigDecimal("100000"))
                    .discountAmount(new BigDecimal("10000"))
                    .completedAt(LocalDateTime.now())
                    .build();

            // 타임아웃되어 AVAILABLE 상태로 돌아간 쿠폰
            CouponIssue availableCoupon = CouponIssueFixture.createIssuedCoupon(100L, policy);
            PaymentResult expectedResult = new PaymentResult(
                    true, availableCoupon, "지연 결제 처리 완료"
            );

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenReturn(expectedResult);

            // when
            kafkaTemplate.send("payment.completed", event);

            // then
            verify(processPaymentUseCase, timeout(5000)).processPaymentCompleted(any());
        }

        @Test
        @DisplayName("중복 결제 이벤트 처리 (멱등성)")
        void handleDuplicatePayment() throws Exception {
            // given
            String reservationId = UUID.randomUUID().toString();
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .orderId("ORDER-789")
                    .reservationId(reservationId)
                    .userId(100L)
                    .couponId(1002L)
                    .paymentAmount(new BigDecimal("30000"))
                    .discountAmount(new BigDecimal("3000"))
                    .completedAt(LocalDateTime.now())
                    .build();

            CouponIssue usedCoupon = CouponIssueFixture.createUsedCoupon(100L, policy, "ORDER-789");
            PaymentResult expectedResult = new PaymentResult(
                    true, usedCoupon, "이미 처리된 결제"
            );

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenReturn(expectedResult);

            // when - 동일 이벤트 2번 전송
            kafkaTemplate.send("payment.completed", event);
            Thread.sleep(100);
            kafkaTemplate.send("payment.completed", event);

            // then
            verify(processPaymentUseCase, timeout(5000).times(2))
                    .processPaymentCompleted(any());
        }
    }

    @Nested
    @DisplayName("결제 실패 이벤트")
    class PaymentFailedEventTest {

        @Test
        @DisplayName("결제 실패로 예약 취소")
        void handlePaymentFailed() throws Exception {
            // given
            String reservationId = UUID.randomUUID().toString();
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .orderId("ORDER-FAIL-123")
                    .reservationId(reservationId)
                    .userId(100L)
                    .couponId(2000L)
                    .failureReason("잔액 부족")
                    .failedAt(LocalDateTime.now())
                    .build();

            CouponIssue releasedCoupon = CouponIssueFixture.createIssuedCoupon(100L, policy);
            PaymentResult expectedResult = new PaymentResult(
                    true, releasedCoupon, "예약 취소 완료"
            );

            when(processPaymentUseCase.processPaymentFailed(any()))
                    .thenReturn(expectedResult);

            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return expectedResult;
            }).when(processPaymentUseCase).processPaymentFailed(any());

            // when
            kafkaTemplate.send("payment.failed", event);

            // then
            boolean processed = latch.await(5, TimeUnit.SECONDS);
            assertThat(processed).isTrue();

            ArgumentCaptor<ProcessPaymentUseCase.PaymentFailedCommand> captor =
                    ArgumentCaptor.forClass(ProcessPaymentUseCase.PaymentFailedCommand.class);
            verify(processPaymentUseCase, timeout(5000)).processPaymentFailed(captor.capture());

            ProcessPaymentUseCase.PaymentFailedCommand capturedCommand = captor.getValue();
            assertThat(capturedCommand.reservationId()).isEqualTo(reservationId);
            assertThat(capturedCommand.failureReason()).isEqualTo("잔액 부족");
        }

        @Test
        @DisplayName("이미 사용된 쿠폰의 결제 실패 처리")
        void handleFailedPaymentForUsedCoupon() throws Exception {
            // given
            String reservationId = UUID.randomUUID().toString();
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .orderId("ORDER-FAIL-456")
                    .reservationId(reservationId)
                    .userId(100L)
                    .couponId(2001L)
                    .failureReason("카드 한도 초과")
                    .failedAt(LocalDateTime.now())
                    .build();

            // 이미 다른 결제로 사용된 쿠폰
            CouponIssue usedCoupon = CouponIssueFixture.createUsedCoupon(100L, policy, "ORDER-OTHER");
            PaymentResult expectedResult = new PaymentResult(
                    false, usedCoupon, "이미 사용된 쿠폰"
            );

            when(processPaymentUseCase.processPaymentFailed(any()))
                    .thenReturn(expectedResult);

            // when
            kafkaTemplate.send("payment.failed", event);

            // then
            verify(processPaymentUseCase, timeout(5000)).processPaymentFailed(any());
        }
    }

    @Nested
    @DisplayName("이벤트 처리 실패")
    class EventProcessingFailure {

        @Test
        @DisplayName("이벤트 처리 중 예외 발생")
        void handleProcessingException() throws Exception {
            // given
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .orderId("ORDER-ERROR")
                    .reservationId("invalid-reservation")
                    .userId(100L)
                    .couponId(9999L)
                    .paymentAmount(new BigDecimal("50000"))
                    .discountAmount(new BigDecimal("5000"))
                    .completedAt(LocalDateTime.now())
                    .build();

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenThrow(new RuntimeException("쿠폰을 찾을 수 없음"));

            // when
            kafkaTemplate.send("payment.completed", event);

            // then
            verify(processPaymentUseCase, timeout(5000)).processPaymentCompleted(any());
            // 에러 로깅 확인 (실제로는 로그 검증 프레임워크 사용)
        }

        @Test
        @DisplayName("잘못된 이벤트 포맷")
        void handleInvalidEventFormat() throws Exception {
            // given - 필수 필드가 누락된 이벤트
            String invalidJson = "{\"orderId\":\"ORDER-123\"}"; // reservationId 누락

            // when
            kafkaTemplate.send("payment.completed", invalidJson);

            // then
            Thread.sleep(1000);
            verify(processPaymentUseCase, never()).processPaymentCompleted(any());
        }
    }

    @Nested
    @DisplayName("대량 이벤트 처리")
    class BulkEventProcessing {

        @Test
        @DisplayName("대량 결제 이벤트 순차 처리")
        void handleBulkPaymentEvents() throws Exception {
            // given
            int eventCount = 100;
            CountDownLatch latch = new CountDownLatch(eventCount);

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenAnswer(invocation -> {
                        latch.countDown();
                        CouponIssue coupon = CouponIssueFixture.createUsedCoupon(
                                100L, policy, "ORDER-" + latch.getCount()
                        );
                        return new PaymentResult(true, coupon, "처리 완료");
                    });

            // when
            for (int i = 0; i < eventCount; i++) {
                PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                        .orderId("ORDER-" + i)
                        .reservationId("RESERVATION-" + i)
                        .userId(100L + i)
                        .couponId(1000L + i)
                        .paymentAmount(new BigDecimal("50000"))
                        .discountAmount(new BigDecimal("5000"))
                        .completedAt(LocalDateTime.now())
                        .build();

                kafkaTemplate.send("payment.completed", event);
            }

            // then
            boolean allProcessed = latch.await(30, TimeUnit.SECONDS);
            assertThat(allProcessed).isTrue();
            verify(processPaymentUseCase, times(eventCount)).processPaymentCompleted(any());
        }

        @Test
        @DisplayName("동시 다발적 이벤트 처리")
        void handleConcurrentEvents() throws Exception {
            // given
            int completedCount = 50;
            int failedCount = 50;
            CountDownLatch latch = new CountDownLatch(completedCount + failedCount);

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenAnswer(invocation -> {
                        latch.countDown();
                        Thread.sleep(10); // 처리 시간 시뮬레이션
                        return new PaymentResult(true, null, "완료");
                    });

            when(processPaymentUseCase.processPaymentFailed(any()))
                    .thenAnswer(invocation -> {
                        latch.countDown();
                        Thread.sleep(10); // 처리 시간 시뮬레이션
                        return new PaymentResult(true, null, "실패 처리");
                    });

            // when - 완료와 실패 이벤트 동시 발송
            for (int i = 0; i < completedCount; i++) {
                PaymentCompletedEvent completedEvent = PaymentCompletedEvent.builder()
                        .orderId("COMPLETED-" + i)
                        .reservationId("RES-C-" + i)
                        .userId(100L + i)
                        .couponId(1000L + i)
                        .paymentAmount(new BigDecimal("50000"))
                        .discountAmount(new BigDecimal("5000"))
                        .completedAt(LocalDateTime.now())
                        .build();
                kafkaTemplate.send("payment.completed", completedEvent);
            }

            for (int i = 0; i < failedCount; i++) {
                PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                        .orderId("FAILED-" + i)
                        .reservationId("RES-F-" + i)
                        .userId(200L + i)
                        .couponId(2000L + i)
                        .failureReason("실패 이유 " + i)
                        .failedAt(LocalDateTime.now())
                        .build();
                kafkaTemplate.send("payment.failed", failedEvent);
            }

            // then
            boolean allProcessed = latch.await(30, TimeUnit.SECONDS);
            assertThat(allProcessed).isTrue();
            verify(processPaymentUseCase, times(completedCount)).processPaymentCompleted(any());
            verify(processPaymentUseCase, times(failedCount)).processPaymentFailed(any());
        }
    }
}
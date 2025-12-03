package com.teambind.coupon.adapter.in.message;

import com.teambind.coupon.adapter.in.message.dto.PaymentCompletedEvent;
import com.teambind.coupon.adapter.in.message.dto.PaymentFailedEvent;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PaymentEventConsumer 단위 테스트
 * Kafka 메시지 처리 로직을 Mockito로 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer 단위 테스트")
class PaymentEventConsumerUnitTest {

    @Mock
    private ProcessPaymentUseCase processPaymentUseCase;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    @Nested
    @DisplayName("결제 완료 이벤트 처리")
    class PaymentCompletedEventTests {

        @Test
        @DisplayName("쿠폰이 포함된 결제 완료 이벤트를 성공적으로 처리한다")
        void handlePaymentCompletedWithCoupon() {
            // given
            String reservationId = "RES-20240101-001";
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .paymentId("PAY-001")
                    .orderId("ORD-001")
                    .userId(100L)
                    .reservationId(reservationId)
                    .paymentAmount(new BigDecimal("50000"))
                    .discountAmount(new BigDecimal("5000"))
                    .paymentAt(LocalDateTime.now())
                    .paymentMethod("CREDIT_CARD")
                    .build();

            PaymentResult successResult = PaymentResult.builder()
                    .success(true)
                    .reservationId(reservationId)
                    .orderId("ORD-001")
                    .message("쿠폰 사용 확정 완료")
                    .build();

            when(processPaymentUseCase.processPaymentCompleted(any(PaymentCompletedCommand.class)))
                    .thenReturn(successResult);

            // when
            paymentEventConsumer.handlePaymentCompleted(
                    event,
                    "payment.completed.v1",
                    0,
                    100L,
                    acknowledgment
            );

            // then
            ArgumentCaptor<PaymentCompletedCommand> commandCaptor =
                    ArgumentCaptor.forClass(PaymentCompletedCommand.class);
            verify(processPaymentUseCase).processPaymentCompleted(commandCaptor.capture());

            PaymentCompletedCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.getOrderId()).isEqualTo("ORD-001");
            assertThat(capturedCommand.getReservationId()).isEqualTo(reservationId);
            assertThat(capturedCommand.getUserId()).isEqualTo(100L);
            assertThat(capturedCommand.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("5000"));

            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("쿠폰이 없는 결제 완료 이벤트는 처리하지 않는다")
        void handlePaymentCompletedWithoutCoupon() {
            // given
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .paymentId("PAY-002")
                    .orderId("ORD-002")
                    .userId(100L)
                    .reservationId(null)  // 쿠폰 없음
                    .paymentAmount(new BigDecimal("50000"))
                    .discountAmount(BigDecimal.ZERO)
                    .paymentAt(LocalDateTime.now())
                    .paymentMethod("CREDIT_CARD")
                    .build();

            // when
            paymentEventConsumer.handlePaymentCompleted(
                    event,
                    "payment.completed.v1",
                    0,
                    101L,
                    acknowledgment
            );

            // then
            verify(processPaymentUseCase, never()).processPaymentCompleted(any());
            verify(acknowledgment).acknowledge(); // 쿠폰이 없어도 ACK는 수행
        }

        @Test
        @DisplayName("빈 reservationId는 쿠폰이 없는 것으로 처리한다")
        void handlePaymentCompletedWithEmptyReservationId() {
            // given
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .paymentId("PAY-003")
                    .orderId("ORD-003")
                    .userId(100L)
                    .reservationId("")  // 빈 문자열
                    .paymentAmount(new BigDecimal("50000"))
                    .discountAmount(BigDecimal.ZERO)
                    .paymentAt(LocalDateTime.now())
                    .paymentMethod("CREDIT_CARD")
                    .build();

            // when
            paymentEventConsumer.handlePaymentCompleted(
                    event,
                    "payment.completed.v1",
                    0,
                    102L,
                    acknowledgment
            );

            // then
            verify(processPaymentUseCase, never()).processPaymentCompleted(any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("처리 중 예외 발생 시 ACK하지 않는다")
        void handlePaymentCompletedWithException() {
            // given
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .paymentId("PAY-004")
                    .orderId("ORD-004")
                    .userId(100L)
                    .reservationId("RES-004")
                    .paymentAmount(new BigDecimal("50000"))
                    .discountAmount(new BigDecimal("5000"))
                    .paymentAt(LocalDateTime.now())
                    .paymentMethod("CREDIT_CARD")
                    .build();

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenThrow(new RuntimeException("데이터베이스 연결 오류"));

            // when
            paymentEventConsumer.handlePaymentCompleted(
                    event,
                    "payment.completed.v1",
                    0,
                    103L,
                    acknowledgment
            );

            // then
            verify(processPaymentUseCase).processPaymentCompleted(any());
            verify(acknowledgment, never()).acknowledge(); // 예외 발생 시 ACK 하지 않음
        }

        @Test
        @DisplayName("처리 결과가 실패여도 ACK는 수행한다")
        void handlePaymentCompletedWithFailureResult() {
            // given
            String reservationId = "RES-005";
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .paymentId("PAY-005")
                    .orderId("ORD-005")
                    .userId(100L)
                    .reservationId(reservationId)
                    .paymentAmount(new BigDecimal("50000"))
                    .discountAmount(new BigDecimal("5000"))
                    .paymentAt(LocalDateTime.now())
                    .paymentMethod("CREDIT_CARD")
                    .build();

            PaymentResult failureResult = PaymentResult.builder()
                    .success(false)
                    .reservationId(reservationId)
                    .message("이미 사용된 쿠폰입니다")
                    .build();

            when(processPaymentUseCase.processPaymentCompleted(any()))
                    .thenReturn(failureResult);

            // when
            paymentEventConsumer.handlePaymentCompleted(
                    event,
                    "payment.completed.v1",
                    0,
                    104L,
                    acknowledgment
            );

            // then
            verify(processPaymentUseCase).processPaymentCompleted(any());
            verify(acknowledgment).acknowledge(); // 비즈니스 로직 실패여도 ACK 수행
        }
    }

    @Nested
    @DisplayName("결제 실패 이벤트 처리")
    class PaymentFailedEventTests {

        @Test
        @DisplayName("쿠폰 예약이 있는 결제 실패 이벤트를 성공적으로 처리한다")
        void handlePaymentFailedWithReservation() {
            // given
            String reservationId = "RES-FAIL-001";
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .paymentId("PAY-FAIL-001")
                    .orderId("ORD-FAIL-001")
                    .userId(100L)
                    .reservationId(reservationId)
                    .failureReason("잔액 부족")
                    .failedAt(LocalDateTime.now())
                    .errorCode("INSUFFICIENT_BALANCE")
                    .build();

            PaymentResult successResult = PaymentResult.builder()
                    .success(true)
                    .reservationId(reservationId)
                    .orderId("ORD-FAIL-001")
                    .message("쿠폰 예약 해제 완료")
                    .build();

            when(processPaymentUseCase.processPaymentFailed(any(PaymentFailedCommand.class)))
                    .thenReturn(successResult);

            // when
            paymentEventConsumer.handlePaymentFailed(
                    event,
                    "payment.failed.v1",
                    1,
                    200L,
                    acknowledgment
            );

            // then
            ArgumentCaptor<PaymentFailedCommand> commandCaptor =
                    ArgumentCaptor.forClass(PaymentFailedCommand.class);
            verify(processPaymentUseCase).processPaymentFailed(commandCaptor.capture());

            PaymentFailedCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.getOrderId()).isEqualTo("ORD-FAIL-001");
            assertThat(capturedCommand.getReservationId()).isEqualTo(reservationId);
            assertThat(capturedCommand.getUserId()).isEqualTo(100L);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("쿠폰 예약이 없는 결제 실패 이벤트는 처리하지 않는다")
        void handlePaymentFailedWithoutReservation() {
            // given
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .paymentId("PAY-FAIL-002")
                    .orderId("ORD-FAIL-002")
                    .userId(100L)
                    .reservationId(null)  // 예약 없음
                    .failureReason("카드 한도 초과")
                    .failedAt(LocalDateTime.now())
                    .build();

            // when
            paymentEventConsumer.handlePaymentFailed(
                    event,
                    "payment.failed.v1",
                    1,
                    201L,
                    acknowledgment
            );

            // then
            verify(processPaymentUseCase, never()).processPaymentFailed(any());
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("처리 중 예외 발생 시 ACK하지 않는다")
        void handlePaymentFailedWithException() {
            // given
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .paymentId("PAY-FAIL-003")
                    .orderId("ORD-FAIL-003")
                    .userId(100L)
                    .reservationId("RES-FAIL-003")
                    .failureReason("시스템 오류")
                    .failedAt(LocalDateTime.now())
                    .build();

            when(processPaymentUseCase.processPaymentFailed(any()))
                    .thenThrow(new RuntimeException("네트워크 오류"));

            // when
            paymentEventConsumer.handlePaymentFailed(
                    event,
                    "payment.failed.v1",
                    1,
                    202L,
                    acknowledgment
            );

            // then
            verify(processPaymentUseCase).processPaymentFailed(any());
            verify(acknowledgment, never()).acknowledge();
        }

        @Test
        @DisplayName("처리 결과가 실패여도 ACK는 수행한다")
        void handlePaymentFailedWithFailureResult() {
            // given
            String reservationId = "RES-FAIL-004";
            PaymentFailedEvent event = PaymentFailedEvent.builder()
                    .paymentId("PAY-FAIL-004")
                    .orderId("ORD-FAIL-004")
                    .userId(100L)
                    .reservationId(reservationId)
                    .failureReason("타임아웃")
                    .failedAt(LocalDateTime.now())
                    .build();

            PaymentResult failureResult = PaymentResult.builder()
                    .success(false)
                    .reservationId(reservationId)
                    .message("이미 해제된 예약입니다")
                    .build();

            when(processPaymentUseCase.processPaymentFailed(any()))
                    .thenReturn(failureResult);

            // when
            paymentEventConsumer.handlePaymentFailed(
                    event,
                    "payment.failed.v1",
                    1,
                    203L,
                    acknowledgment
            );

            // then
            verify(processPaymentUseCase).processPaymentFailed(any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("이벤트 검증")
    class EventValidationTests {

        @Test
        @DisplayName("PaymentCompletedEvent의 hasCouponReservation 메서드를 올바르게 판단한다")
        void testHasCouponReservation() {
            // given
            PaymentCompletedEvent withReservation = PaymentCompletedEvent.builder()
                    .reservationId("RES-001")
                    .build();

            PaymentCompletedEvent withoutReservation = PaymentCompletedEvent.builder()
                    .reservationId(null)
                    .build();

            PaymentCompletedEvent withEmptyReservation = PaymentCompletedEvent.builder()
                    .reservationId("")
                    .build();

            // then
            assertThat(withReservation.hasCouponReservation()).isTrue();
            assertThat(withoutReservation.hasCouponReservation()).isFalse();
            assertThat(withEmptyReservation.hasCouponReservation()).isFalse();
        }

        @Test
        @DisplayName("PaymentFailedEvent의 needsCouponRelease 메서드를 올바르게 판단한다")
        void testNeedsCouponRelease() {
            // given
            PaymentFailedEvent withReservation = PaymentFailedEvent.builder()
                    .reservationId("RES-001")
                    .build();

            PaymentFailedEvent withoutReservation = PaymentFailedEvent.builder()
                    .reservationId(null)
                    .build();

            PaymentFailedEvent withEmptyReservation = PaymentFailedEvent.builder()
                    .reservationId("")
                    .build();

            // then
            assertThat(withReservation.needsCouponRelease()).isTrue();
            assertThat(withoutReservation.needsCouponRelease()).isFalse();
            assertThat(withEmptyReservation.needsCouponRelease()).isFalse();
        }
    }
}
package com.teambind.coupon.adapter.in.message;

import com.teambind.coupon.adapter.in.message.dto.PaymentCompletedEvent;
import com.teambind.coupon.adapter.in.message.dto.PaymentFailedEvent;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PaymentEventConsumer 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer 테스트")
class PaymentEventConsumerTest {

    @Mock
    private ProcessPaymentUseCase processPaymentUseCase;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentEventConsumer consumer;

    private PaymentCompletedEvent completedEvent;
    private PaymentFailedEvent failedEvent;

    @BeforeEach
    void setUp() {
        completedEvent = PaymentCompletedEvent.builder()
                .orderId("ORD123")
                .reservationId("RES123")
                .userId(1000L)
                .orderAmount(100000L)
                .discountAmount(10000L)
                .paymentAmount(90000L)
                .paymentMethod("CARD")
                .build();

        failedEvent = PaymentFailedEvent.builder()
                .orderId("ORD456")
                .reservationId("RES456")
                .userId(2000L)
                .failReason("INSUFFICIENT_BALANCE")
                .build();
    }

    @Test
    @DisplayName("결제 완료 이벤트 처리 - 쿠폰 예약 있음, 성공")
    void handlePaymentCompleted_WithReservation_Success() {
        // given
        PaymentResult successResult = PaymentResult.success("쿠폰 사용 확정 완료");
        when(processPaymentUseCase.processPaymentCompleted(any(PaymentCompletedCommand.class)))
                .thenReturn(successResult);

        // when
        consumer.handlePaymentCompleted(
                completedEvent,
                "payment.completed.v1",
                0,
                12345L,
                acknowledgment
        );

        // then
        ArgumentCaptor<PaymentCompletedCommand> commandCaptor =
                ArgumentCaptor.forClass(PaymentCompletedCommand.class);
        verify(processPaymentUseCase).processPaymentCompleted(commandCaptor.capture());

        PaymentCompletedCommand capturedCommand = commandCaptor.getValue();
        assertThat(capturedCommand.getOrderId()).isEqualTo("ORD123");
        assertThat(capturedCommand.getReservationId()).isEqualTo("RES123");
        assertThat(capturedCommand.getUserId()).isEqualTo(1000L);
        assertThat(capturedCommand.getDiscountAmount()).isEqualTo(10000L);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("결제 완료 이벤트 처리 - 쿠폰 예약 있음, 실패")
    void handlePaymentCompleted_WithReservation_Failed() {
        // given
        PaymentResult failedResult = PaymentResult.failure("예약을 찾을 수 없음");
        when(processPaymentUseCase.processPaymentCompleted(any(PaymentCompletedCommand.class)))
                .thenReturn(failedResult);

        // when
        consumer.handlePaymentCompleted(
                completedEvent,
                "payment.completed.v1",
                0,
                12345L,
                acknowledgment
        );

        // then
        verify(processPaymentUseCase).processPaymentCompleted(any(PaymentCompletedCommand.class));
        verify(acknowledgment).acknowledge(); // 실패해도 acknowledge
    }

    @Test
    @DisplayName("결제 완료 이벤트 처리 - 쿠폰 예약 없음")
    void handlePaymentCompleted_WithoutReservation() {
        // given
        PaymentCompletedEvent eventWithoutReservation = PaymentCompletedEvent.builder()
                .orderId("ORD789")
                .userId(3000L)
                .orderAmount(50000L)
                .paymentAmount(50000L)
                .paymentMethod("CARD")
                .build();

        // when
        consumer.handlePaymentCompleted(
                eventWithoutReservation,
                "payment.completed.v1",
                0,
                12345L,
                acknowledgment
        );

        // then
        verify(processPaymentUseCase, never()).processPaymentCompleted(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("결제 완료 이벤트 처리 - 예외 발생")
    void handlePaymentCompleted_Exception() {
        // given
        when(processPaymentUseCase.processPaymentCompleted(any(PaymentCompletedCommand.class)))
                .thenThrow(new RuntimeException("처리 중 오류"));

        // when
        consumer.handlePaymentCompleted(
                completedEvent,
                "payment.completed.v1",
                0,
                12345L,
                acknowledgment
        );

        // then
        verify(processPaymentUseCase).processPaymentCompleted(any(PaymentCompletedCommand.class));
        verify(acknowledgment, never()).acknowledge(); // 예외 시 acknowledge 하지 않음
    }

    @Test
    @DisplayName("결제 실패 이벤트 처리 - 쿠폰 해제 필요, 성공")
    void handlePaymentFailed_NeedsRelease_Success() {
        // given
        PaymentResult successResult = PaymentResult.success("쿠폰 예약 해제 완료");
        when(processPaymentUseCase.processPaymentFailed(any(PaymentFailedCommand.class)))
                .thenReturn(successResult);

        // when
        consumer.handlePaymentFailed(
                failedEvent,
                "payment.failed.v1",
                1,
                67890L,
                acknowledgment
        );

        // then
        ArgumentCaptor<PaymentFailedCommand> commandCaptor =
                ArgumentCaptor.forClass(PaymentFailedCommand.class);
        verify(processPaymentUseCase).processPaymentFailed(commandCaptor.capture());

        PaymentFailedCommand capturedCommand = commandCaptor.getValue();
        assertThat(capturedCommand.getOrderId()).isEqualTo("ORD456");
        assertThat(capturedCommand.getReservationId()).isEqualTo("RES456");
        assertThat(capturedCommand.getUserId()).isEqualTo(2000L);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("결제 실패 이벤트 처리 - 쿠폰 해제 필요, 실패")
    void handlePaymentFailed_NeedsRelease_Failed() {
        // given
        PaymentResult failedResult = PaymentResult.failure("이미 해제된 예약");
        when(processPaymentUseCase.processPaymentFailed(any(PaymentFailedCommand.class)))
                .thenReturn(failedResult);

        // when
        consumer.handlePaymentFailed(
                failedEvent,
                "payment.failed.v1",
                1,
                67890L,
                acknowledgment
        );

        // then
        verify(processPaymentUseCase).processPaymentFailed(any(PaymentFailedCommand.class));
        verify(acknowledgment).acknowledge(); // 실패해도 acknowledge
    }

    @Test
    @DisplayName("결제 실패 이벤트 처리 - 쿠폰 해제 불필요")
    void handlePaymentFailed_NoReleaseNeeded() {
        // given
        PaymentFailedEvent eventWithoutReservation = PaymentFailedEvent.builder()
                .orderId("ORD999")
                .userId(4000L)
                .failReason("CARD_ERROR")
                .build();

        // when
        consumer.handlePaymentFailed(
                eventWithoutReservation,
                "payment.failed.v1",
                1,
                67890L,
                acknowledgment
        );

        // then
        verify(processPaymentUseCase, never()).processPaymentFailed(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("결제 실패 이벤트 처리 - 예외 발생")
    void handlePaymentFailed_Exception() {
        // given
        when(processPaymentUseCase.processPaymentFailed(any(PaymentFailedCommand.class)))
                .thenThrow(new RuntimeException("처리 중 오류"));

        // when
        consumer.handlePaymentFailed(
                failedEvent,
                "payment.failed.v1",
                1,
                67890L,
                acknowledgment
        );

        // then
        verify(processPaymentUseCase).processPaymentFailed(any(PaymentFailedCommand.class));
        verify(acknowledgment, never()).acknowledge(); // 예외 시 acknowledge 하지 않음
    }

    @Test
    @DisplayName("다양한 파티션과 오프셋 처리")
    void handleVariousPartitionAndOffset() {
        // given
        PaymentResult successResult = PaymentResult.success("처리 완료");
        when(processPaymentUseCase.processPaymentCompleted(any()))
                .thenReturn(successResult);
        when(processPaymentUseCase.processPaymentFailed(any()))
                .thenReturn(successResult);

        // when - partition 0, offset 100
        consumer.handlePaymentCompleted(
                completedEvent,
                "payment.completed.v1",
                0,
                100L,
                acknowledgment
        );

        // when - partition 5, offset 999999
        consumer.handlePaymentFailed(
                failedEvent,
                "payment.failed.v1",
                5,
                999999L,
                acknowledgment
        );

        // then
        verify(acknowledgment, times(2)).acknowledge();
    }
}
package com.teambind.coupon.adapter.in.message;

import com.teambind.coupon.adapter.in.message.dto.PaymentCompletedEvent;
import com.teambind.coupon.adapter.in.message.dto.PaymentFailedEvent;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase;
import com.teambind.coupon.application.port.in.ProcessPaymentUseCase.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 결제 이벤트 Kafka Consumer
 * 결제 완료/실패 이벤트를 처리하여 쿠폰 상태를 업데이트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ProcessPaymentUseCase processPaymentUseCase;

    @KafkaListener(
            topics = "${kafka.topics.payment-completed:payment.completed.v1}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("결제 완료 이벤트 수신: topic={}, partition={}, offset={}, event={}",
                topic, partition, offset, event);

        try {
            // 쿠폰 예약이 있는 경우에만 처리
            if (event.hasCouponReservation()) {
                PaymentCompletedCommand command = PaymentCompletedCommand.builder()
                        .orderId(event.getOrderId())
                        .reservationId(event.getReservationId())
                        .userId(event.getUserId())
                        .discountAmount(event.getDiscountAmount())
                        .build();

                PaymentResult result = processPaymentUseCase.processPaymentCompleted(command);

                if (result.isSuccess()) {
                    log.info("쿠폰 사용 확정 완료: reservationId={}, orderId={}",
                            event.getReservationId(), event.getOrderId());
                } else {
                    // 비즈니스 로직 실패는 재처리가 필요하므로 ACK하지 않음
                    log.error("쿠폰 사용 확정 실패 - 재처리 필요: reservationId={}, message={}",
                            event.getReservationId(), result.getMessage());
                    return; // ACK하지 않고 리턴
                }
            }

            // 성공적으로 처리된 경우에만 ACK
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패: {}", event, e);
            // 실패 시 재시도를 위해 acknowledge하지 않음
            // DLQ(Dead Letter Queue) 설정에 따라 처리됨
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.payment-failed:payment.failed.v1}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("결제 실패 이벤트 수신: topic={}, partition={}, offset={}, event={}",
                topic, partition, offset, event);

        try {
            // 쿠폰 예약 해제가 필요한 경우에만 처리
            if (event.needsCouponRelease()) {
                PaymentFailedCommand command = PaymentFailedCommand.builder()
                        .orderId(event.getOrderId())
                        .reservationId(event.getReservationId())
                        .userId(event.getUserId())
                        .build();

                PaymentResult result = processPaymentUseCase.processPaymentFailed(command);

                if (result.isSuccess()) {
                    log.info("쿠폰 예약 해제 완료: reservationId={}", event.getReservationId());
                } else {
                    // 비즈니스 로직 실패는 재처리가 필요하므로 ACK하지 않음
                    log.error("쿠폰 예약 해제 실패 - 재처리 필요: reservationId={}, message={}",
                            event.getReservationId(), result.getMessage());
                    return; // ACK하지 않고 리턴
                }
            }

            // 성공적으로 처리된 경우에만 ACK
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("결제 실패 이벤트 처리 실패: {}", event, e);
            // 실패 시 재시도를 위해 acknowledge하지 않음
        }
    }
}
package com.teambind.coupon.adapter.in.kafka;

import com.teambind.coupon.adapter.in.kafka.dto.PaymentCompletedEvent;
import com.teambind.coupon.application.port.in.ConfirmCouponUseCommand;
import com.teambind.coupon.application.port.in.ConfirmCouponUseUseCase;
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
 * 결제 서비스로부터 결제 완료 이벤트를 수신하여 쿠폰 사용 확정 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ConfirmCouponUseUseCase confirmCouponUseUseCase;

    @KafkaListener(
            topics = "${kafka.topics.payment-completed:payment-completed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompletedEvent(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("결제 완료 이벤트 수신 - topic: {}, partition: {}, offset: {}, paymentId: {}, reservationId: {}",
                topic, partition, offset, event.getPaymentId(), event.getReservationId());

        try {
            // 1. 이벤트 유효성 검증
            if (!event.isValid()) {
                log.warn("유효하지 않은 결제 이벤트 - event: {}", event);
                acknowledgment.acknowledge();
                return;
            }

            // 2. 쿠폰 사용 확정 처리
            ConfirmCouponUseCommand command = ConfirmCouponUseCommand.of(
                    event.getReservationId(),
                    event.getOrderId(),
                    event.getAmount()
            );

            confirmCouponUseUseCase.confirmCouponUse(command);

            log.info("결제 완료 이벤트 처리 성공 - paymentId: {}, reservationId: {}",
                    event.getPaymentId(), event.getReservationId());

            // 3. 메시지 처리 완료 확인
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패 - paymentId: {}, reservationId: {}, error: {}",
                    event.getPaymentId(), event.getReservationId(), e.getMessage(), e);

            // 에러 처리 전략
            // 1. 재처리 가능한 에러: nack() 후 재시도
            // 2. 재처리 불가능한 에러: DLQ로 이동 또는 로그 기록 후 acknowledge()

            // 여기서는 로그 기록 후 acknowledge 처리
            acknowledgment.acknowledge();

            // TODO: DLQ(Dead Letter Queue) 처리 또는 에러 이벤트 발행
        }
    }
}
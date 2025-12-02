package com.teambind.coupon.adapter.out.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 메시지 전송 추상 어댑터
 * Kafka 메시지 전송 기능 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public abstract class AbstractMessageAdapter {

    protected final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 메시지 전송
     *
     * @param topic   토픽
     * @param key     메시지 키
     * @param payload 메시지 페이로드
     */
    protected void sendMessage(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("메시지 전송 성공 - topic: {}, key: {}, offset: {}",
                        topic, key, result.getRecordMetadata().offset());
            } else {
                log.error("메시지 전송 실패 - topic: {}, key: {}, error: {}",
                        topic, key, ex.getMessage(), ex);
            }
        });
    }

    /**
     * 동기 메시지 전송
     *
     * @param topic   토픽
     * @param key     메시지 키
     * @param payload 메시지 페이로드
     * @return 전송 결과
     */
    protected SendResult<String, Object> sendMessageSync(String topic, String key, Object payload) {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, key, payload).get();
            log.info("동기 메시지 전송 성공 - topic: {}, key: {}, offset: {}",
                    topic, key, result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("동기 메시지 전송 실패 - topic: {}, key: {}, error: {}",
                    topic, key, e.getMessage(), e);
            throw new RuntimeException("메시지 전송 실패", e);
        }
    }
}
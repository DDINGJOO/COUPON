package com.teambind.coupon.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * Redis를 사용한 분산 락 구현
 * SETNX와 Lua 스크립트를 활용한 원자적 연산 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_PREFIX = "lock:";

    // Lua script for atomic unlock operation
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 분산 락 획득 시도
     *
     * @param key 락 키
     * @param value 락 값 (보통 요청 ID나 스레드 ID)
     * @param timeout 락 타임아웃
     * @return 락 획득 성공 여부
     */
    public boolean tryLock(String key, String value, Duration timeout) {
        try {
            String lockKey = LOCK_PREFIX + key;
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, value, timeout);

            boolean acquired = result != null && result;

            if (acquired) {
                log.debug("Lock acquired - key: {}, value: {}, timeout: {}ms",
                        lockKey, value, timeout.toMillis());
            } else {
                log.debug("Failed to acquire lock - key: {}", lockKey);
            }

            return acquired;
        } catch (Exception e) {
            log.error("Error acquiring lock - key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 분산 락 해제
     * Lua 스크립트를 사용하여 원자적으로 처리
     *
     * @param key 락 키
     * @param value 락 값 (획득할 때 사용한 값과 동일해야 함)
     * @return 락 해제 성공 여부
     */
    public boolean unlock(String key, String value) {
        try {
            String lockKey = LOCK_PREFIX + key;

            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(UNLOCK_SCRIPT);
            redisScript.setResultType(Long.class);

            Long result = redisTemplate.execute(
                    redisScript,
                    Collections.singletonList(lockKey),
                    value
            );

            boolean released = result != null && result > 0;

            if (released) {
                log.debug("Lock released - key: {}, value: {}", lockKey, value);
            } else {
                log.warn("Failed to release lock - key: {}, value: {} (lock not owned or already expired)",
                        lockKey, value);
            }

            return released;
        } catch (Exception e) {
            log.error("Error releasing lock - key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 락 연장 (재획득)
     *
     * @param key 락 키
     * @param value 락 값
     * @param newTimeout 새로운 타임아웃
     * @return 연장 성공 여부
     */
    public boolean extendLock(String key, String value, Duration newTimeout) {
        try {
            String lockKey = LOCK_PREFIX + key;

            // 현재 값 확인
            Object currentValue = redisTemplate.opsForValue().get(lockKey);
            if (value.equals(currentValue)) {
                // 값이 일치하면 만료 시간 연장
                return redisTemplate.expire(lockKey, newTimeout);
            }

            return false;
        } catch (Exception e) {
            log.error("Error extending lock - key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 락 존재 여부 확인
     *
     * @param key 락 키
     * @return 락 존재 여부
     */
    public boolean isLocked(String key) {
        try {
            String lockKey = LOCK_PREFIX + key;
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.error("Error checking lock existence - key: {}, error: {}", key, e.getMessage());
            return false;
        }
    }
}
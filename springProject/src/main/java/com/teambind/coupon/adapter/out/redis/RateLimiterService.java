package com.teambind.coupon.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Rate Limiter 서비스
 * Sliding Window Counter 방식으로 요청 제한
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;

    // Lua script for atomic rate limiting
    private static final String RATE_LIMIT_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])

            -- Remove old entries
            redis.call('ZREMRANGEBYSCORE', key, 0, current_time - window * 1000)

            -- Count current requests
            local current = redis.call('ZCARD', key)

            if current < limit then
                -- Add new request
                redis.call('ZADD', key, current_time, current_time)
                redis.call('EXPIRE', key, window)
                return {1, current + 1, limit}
            else
                return {0, current, limit}
            end
            """;

    /**
     * 요청 허용 여부 확인
     *
     * @param key    제한 키 (예: "user:123", "ip:192.168.1.1")
     * @param limit  제한 횟수
     * @param window 시간 윈도우 (초)
     * @return 허용 여부
     */
    public boolean allowRequest(String key, int limit, int window) {
        String rateLimitKey = generateKey(key);

        try {
            RedisScript<List> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, List.class);
            List<Long> result = redisTemplate.execute(
                    script,
                    Arrays.asList(rateLimitKey),
                    String.valueOf(limit),
                    String.valueOf(window),
                    String.valueOf(System.currentTimeMillis())
            );

            if (result != null && !result.isEmpty()) {
                boolean allowed = result.get(0) == 1L;
                long current = result.get(1);
                long maxLimit = result.get(2);

                if (allowed) {
                    log.debug("Rate limit 허용 - key: {}, current: {}/{}", key, current, maxLimit);
                } else {
                    log.warn("Rate limit 초과 - key: {}, current: {}/{}", key, current, maxLimit);
                }

                return allowed;
            }

            return false;
        } catch (Exception e) {
            log.error("Rate limiter 오류 - key: {}", key, e);
            // 에러 발생 시 요청 허용 (fail open)
            return true;
        }
    }

    /**
     * 다단계 Rate Limiting
     * 분당, 시간당, 일당 제한을 모두 확인
     *
     * @param key         제한 키
     * @param perMinute   분당 제한
     * @param perHour     시간당 제한
     * @param perDay      일당 제한
     * @return 허용 여부
     */
    public boolean allowRequestMultiLevel(String key, int perMinute, int perHour, int perDay) {
        // 분당 제한 확인
        if (perMinute > 0 && !allowRequest(key + ":minute", perMinute, 60)) {
            return false;
        }

        // 시간당 제한 확인
        if (perHour > 0 && !allowRequest(key + ":hour", perHour, 3600)) {
            return false;
        }

        // 일당 제한 확인
        if (perDay > 0 && !allowRequest(key + ":day", perDay, 86400)) {
            return false;
        }

        return true;
    }

    /**
     * 현재 요청 수 조회
     *
     * @param key    제한 키
     * @param window 시간 윈도우 (초)
     * @return 현재 요청 수
     */
    public long getCurrentRequestCount(String key, int window) {
        String rateLimitKey = generateKey(key);

        try {
            // 현재 시간 기준으로 유효한 요청 수 계산
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - (window * 1000L);

            // 오래된 엔트리 제거
            redisTemplate.opsForZSet().removeRangeByScore(
                    rateLimitKey, 0, windowStart
            );

            // 현재 요청 수 반환
            Long count = redisTemplate.opsForZSet().count(
                    rateLimitKey, windowStart, currentTime
            );

            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("요청 수 조회 오류 - key: {}", key, e);
            return 0;
        }
    }

    /**
     * Rate limit 리셋
     *
     * @param key 제한 키
     */
    public void resetLimit(String key) {
        String rateLimitKey = generateKey(key);

        try {
            redisTemplate.delete(rateLimitKey);
            log.info("Rate limit 리셋 - key: {}", key);
        } catch (Exception e) {
            log.error("Rate limit 리셋 오류 - key: {}", key, e);
        }
    }

    /**
     * 남은 요청 가능 횟수 조회
     *
     * @param key    제한 키
     * @param limit  제한 횟수
     * @param window 시간 윈도우 (초)
     * @return 남은 요청 가능 횟수
     */
    public long getRemainingRequests(String key, int limit, int window) {
        long current = getCurrentRequestCount(key, window);
        return Math.max(0, limit - current);
    }

    /**
     * Rate limit 정보 조회
     *
     * @param key    제한 키
     * @param limit  제한 횟수
     * @param window 시간 윈도우 (초)
     * @return Rate limit 정보
     */
    public RateLimitInfo getRateLimitInfo(String key, int limit, int window) {
        long current = getCurrentRequestCount(key, window);
        long remaining = Math.max(0, limit - current);
        long resetTime = System.currentTimeMillis() + (window * 1000L);

        return new RateLimitInfo(limit, remaining, resetTime, current >= limit);
    }

    /**
     * Redis 키 생성
     *
     * @param key 원본 키
     * @return Redis 키
     */
    private String generateKey(String key) {
        return "rate_limit:" + key;
    }

    /**
     * Rate Limit 정보 DTO
     */
    public record RateLimitInfo(
            int limit,
            long remaining,
            long resetTime,
            boolean exceeded
    ) {
        public Duration getTimeUntilReset() {
            long now = System.currentTimeMillis();
            return Duration.ofMillis(Math.max(0, resetTime - now));
        }
    }
}
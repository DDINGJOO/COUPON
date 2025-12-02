package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 재고 관리 서비스
 * Redis를 활용한 원자적 재고 관리와 동시성 제어
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponStockService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisDistributedLock distributedLock;

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String USER_ISSUE_KEY_PREFIX = "coupon:user:issue:";
    private static final String LOCK_KEY_PREFIX = "coupon:stock:lock:";

    /**
     * 재고 초기화
     * DB의 실제 재고를 Redis에 동기화
     */
    public void initializeStock(Long policyId, int totalStock) {
        String stockKey = STOCK_KEY_PREFIX + policyId;

        // 이미 존재하는 경우 덮어쓰지 않음
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
            stockKey,
            String.valueOf(totalStock),
            Duration.ofDays(7)  // 7일 TTL
        );

        if (Boolean.TRUE.equals(success)) {
            log.info("재고 초기화 완료 - policyId: {}, stock: {}", policyId, totalStock);
        } else {
            log.debug("재고 이미 존재 - policyId: {}", policyId);
        }
    }

    /**
     * 재고 차감 (원자적 연산)
     * Lua 스크립트를 사용하여 check-and-decrement를 원자적으로 수행
     */
    public boolean decrementStock(Long policyId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + policyId;

        // Lua 스크립트: 재고 확인 후 차감
        String luaScript = """
            local stock = redis.call('get', KEYS[1])
            if stock == false then
                return -1
            end
            stock = tonumber(stock)
            local quantity = tonumber(ARGV[1])
            if stock >= quantity then
                redis.call('decrby', KEYS[1], quantity)
                return stock - quantity
            else
                return -1
            end
            """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        Long result = redisTemplate.execute(
            script,
            Collections.singletonList(stockKey),
            String.valueOf(quantity)
        );

        boolean success = result != null && result >= 0;

        if (success) {
            log.debug("재고 차감 성공 - policyId: {}, quantity: {}, remaining: {}",
                     policyId, quantity, result);
        } else {
            log.warn("재고 부족 - policyId: {}, requested: {}", policyId, quantity);
        }

        return success;
    }

    /**
     * 재고 복구 (취소/롤백 시)
     */
    public void incrementStock(Long policyId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        Long newStock = redisTemplate.opsForValue().increment(stockKey, quantity);
        log.info("재고 복구 - policyId: {}, quantity: {}, newStock: {}",
                 policyId, quantity, newStock);
    }

    /**
     * 현재 재고 조회
     */
    public int getCurrentStock(Long policyId) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        String stock = redisTemplate.opsForValue().get(stockKey);

        if (stock == null) {
            log.warn("Redis에 재고 정보 없음 - policyId: {}", policyId);
            return -1;  // 재고 정보 없음
        }

        return Integer.parseInt(stock);
    }

    /**
     * 사용자별 발급 수량 증가 (원자적)
     * 사용자별 발급 제한 체크를 위한 카운터
     */
    public boolean incrementUserIssueCount(Long userId, Long policyId, int maxPerUser) {
        String userKey = USER_ISSUE_KEY_PREFIX + policyId + ":" + userId;

        // Lua 스크립트: 현재 카운트 확인 후 증가
        String luaScript = """
            local current = redis.call('get', KEYS[1])
            if current == false then
                current = 0
            else
                current = tonumber(current)
            end
            local max = tonumber(ARGV[1])
            if current < max then
                redis.call('incr', KEYS[1])
                redis.call('expire', KEYS[1], 86400 * 7)  -- 7일 TTL
                return 1
            else
                return 0
            end
            """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        Long result = redisTemplate.execute(
            script,
            Collections.singletonList(userKey),
            String.valueOf(maxPerUser)
        );

        boolean success = result != null && result == 1;

        if (!success) {
            log.warn("사용자 발급 한도 초과 - userId: {}, policyId: {}, max: {}",
                     userId, policyId, maxPerUser);
        }

        return success;
    }

    /**
     * 사용자 발급 수량 조회
     */
    public int getUserIssueCount(Long userId, Long policyId) {
        String userKey = USER_ISSUE_KEY_PREFIX + policyId + ":" + userId;
        String count = redisTemplate.opsForValue().get(userKey);
        return count != null ? Integer.parseInt(count) : 0;
    }

    /**
     * 배치 재고 차감 (여러 개를 한 번에)
     * 트랜잭션으로 묶어서 처리
     */
    public boolean batchDecrementStock(Long policyId, List<Long> userIds, int quantityPerUser) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        int totalRequired = userIds.size() * quantityPerUser;

        // Lua 스크립트: 전체 재고 확인 후 차감
        String luaScript = """
            local stock = redis.call('get', KEYS[1])
            if stock == false then
                return -1
            end
            stock = tonumber(stock)
            local total = tonumber(ARGV[1])
            if stock >= total then
                redis.call('decrby', KEYS[1], total)
                return stock - total
            else
                return -1
            end
            """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
        Long result = redisTemplate.execute(
            script,
            Collections.singletonList(stockKey),
            String.valueOf(totalRequired)
        );

        return result != null && result >= 0;
    }

    /**
     * 재고 동기화
     * DB와 Redis 재고를 동기화 (스케줄러에서 주기적으로 실행)
     */
    public void syncStock(Long policyId, int actualStock) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        String lockKey = LOCK_KEY_PREFIX + policyId;

        // 동기화 중 발급 방지를 위한 락
        if (distributedLock.tryLock(lockKey, "sync", Duration.ofSeconds(5))) {
            try {
                redisTemplate.opsForValue().set(
                    stockKey,
                    String.valueOf(actualStock),
                    Duration.ofDays(7)
                );
                log.info("재고 동기화 완료 - policyId: {}, stock: {}", policyId, actualStock);
            } finally {
                distributedLock.unlock(lockKey, "sync");
            }
        } else {
            log.warn("재고 동기화 락 획득 실패 - policyId: {}", policyId);
        }
    }

    /**
     * 재고 정보 삭제 (정책 종료 시)
     */
    public void removeStock(Long policyId) {
        String stockKey = STOCK_KEY_PREFIX + policyId;
        Boolean deleted = redisTemplate.delete(stockKey);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("재고 정보 삭제 - policyId: {}", policyId);
        }
    }

    /**
     * 사용자별 발급 정보 초기화 (테스트용)
     */
    public void resetUserIssueCount(Long userId, Long policyId) {
        String userKey = USER_ISSUE_KEY_PREFIX + policyId + ":" + userId;
        redisTemplate.delete(userKey);
    }
}
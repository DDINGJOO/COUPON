package com.teambind.coupon.adapter.out.redis;

import com.teambind.coupon.common.exceptions.CustomException;
import com.teambind.coupon.common.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 기반 분산 락 서비스 구현체
 * Redis를 사용하여 분산 환경에서의 동시성 제어 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonDistributedLockService implements DistributedLockService {

    private final RedissonClient redissonClient;

    // 기본 락 설정
    private static final long DEFAULT_WAIT_TIME = 3L;
    private static final long DEFAULT_LEASE_TIME = 10L;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    @Override
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task) {
        String lockKeyWithPrefix = generateLockKey(lockKey);
        RLock lock = redissonClient.getLock(lockKeyWithPrefix);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            if (!acquired) {
                log.warn("락 획득 실패 - lockKey: {}, waitTime: {}{}", lockKeyWithPrefix, waitTime, unit);
                throw new CustomException(ErrorCode.LOCK_ACQUISITION_FAILED,
                        String.format("락 획득에 실패했습니다. lockKey: %s", lockKey));
            }

            log.debug("락 획득 성공 - lockKey: {}", lockKeyWithPrefix);

            // 작업 실행
            T result = task.get();

            log.debug("작업 실행 완료 - lockKey: {}", lockKeyWithPrefix);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("락 획득 중 인터럽트 발생 - lockKey: {}", lockKeyWithPrefix, e);
            throw new CustomException(ErrorCode.LOCK_INTERRUPTED,
                    String.format("락 획득 중 인터럽트가 발생했습니다. lockKey: %s", lockKey));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("락 해제 완료 - lockKey: {}", lockKeyWithPrefix);
            }
        }
    }

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> task) {
        return executeWithLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, DEFAULT_TIME_UNIT, task);
    }

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        String lockKeyWithPrefix = generateLockKey(lockKey);
        RLock lock = redissonClient.getLock(lockKeyWithPrefix);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            if (acquired) {
                log.debug("락 획득 성공 - lockKey: {}", lockKeyWithPrefix);
            } else {
                log.debug("락 획득 실패 - lockKey: {}", lockKeyWithPrefix);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("락 획득 중 인터럽트 발생 - lockKey: {}", lockKeyWithPrefix, e);
            return false;
        }
    }

    @Override
    public void unlock(String lockKey) {
        String lockKeyWithPrefix = generateLockKey(lockKey);
        RLock lock = redissonClient.getLock(lockKeyWithPrefix);

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("락 해제 완료 - lockKey: {}", lockKeyWithPrefix);
        } else {
            log.warn("현재 스레드가 보유하지 않은 락 해제 시도 - lockKey: {}", lockKeyWithPrefix);
        }
    }

    @Override
    public void forceUnlock(String lockKey) {
        String lockKeyWithPrefix = generateLockKey(lockKey);
        RLock lock = redissonClient.getLock(lockKeyWithPrefix);

        if (lock.isLocked()) {
            lock.forceUnlock();
            log.warn("락 강제 해제 - lockKey: {}", lockKeyWithPrefix);
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        String lockKeyWithPrefix = generateLockKey(lockKey);
        RLock lock = redissonClient.getLock(lockKeyWithPrefix);
        boolean locked = lock.isLocked();

        log.debug("락 상태 확인 - lockKey: {}, locked: {}", lockKeyWithPrefix, locked);
        return locked;
    }

    /**
     * 락 키에 프리픽스 추가
     *
     * @param lockKey 원본 락 키
     * @return 프리픽스가 추가된 락 키
     */
    private String generateLockKey(String lockKey) {
        return "coupon:lock:" + lockKey;
    }
}
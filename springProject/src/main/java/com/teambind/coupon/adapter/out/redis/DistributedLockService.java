package com.teambind.coupon.adapter.out.redis;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 분산 락 서비스 인터페이스
 * Redis를 사용한 분산 환경에서의 동시성 제어
 */
public interface DistributedLockService {

    /**
     * 락을 획득하고 작업 실행
     *
     * @param lockKey     락 키
     * @param waitTime    락 획득 대기 시간
     * @param leaseTime   락 유지 시간
     * @param unit        시간 단위
     * @param task        실행할 작업
     * @param <T>         반환 타입
     * @return 작업 결과
     */
    <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task);

    /**
     * 락을 획득하고 작업 실행 (기본 설정 사용)
     *
     * @param lockKey 락 키
     * @param task    실행할 작업
     * @param <T>     반환 타입
     * @return 작업 결과
     */
    <T> T executeWithLock(String lockKey, Supplier<T> task);

    /**
     * 락 획득 시도
     *
     * @param lockKey   락 키
     * @param waitTime  대기 시간
     * @param leaseTime 유지 시간
     * @param unit      시간 단위
     * @return 락 획득 성공 여부
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 락 해제
     *
     * @param lockKey 락 키
     */
    void unlock(String lockKey);

    /**
     * 락 강제 해제 (주의: 다른 스레드가 획득한 락도 해제됨)
     *
     * @param lockKey 락 키
     */
    void forceUnlock(String lockKey);

    /**
     * 락이 현재 사용 중인지 확인
     *
     * @param lockKey 락 키
     * @return 사용 중 여부
     */
    boolean isLocked(String lockKey);
}
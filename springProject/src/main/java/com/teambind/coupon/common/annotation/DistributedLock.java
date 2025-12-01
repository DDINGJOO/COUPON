package com.teambind.coupon.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산 락을 적용하기 위한 어노테이션
 * 메서드에 적용하여 분산 환경에서 동시성 제어
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키를 생성하기 위한 SpEL 표현식
     * 예: "#couponPolicyId", "#command.userId + ':' + #command.couponPolicyId"
     */
    String key();

    /**
     * 락 키 프리픽스
     * 기본값: "lock"
     */
    String prefix() default "lock";

    /**
     * 락 획득 대기 시간
     * 기본값: 3초
     */
    long waitTime() default 3;

    /**
     * 락 유지 시간
     * 기본값: 10초
     */
    long leaseTime() default 10;

    /**
     * 시간 단위
     * 기본값: SECONDS
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락 획득 실패 시 예외 발생 여부
     * 기본값: true (예외 발생)
     */
    boolean throwExceptionOnFail() default true;
}
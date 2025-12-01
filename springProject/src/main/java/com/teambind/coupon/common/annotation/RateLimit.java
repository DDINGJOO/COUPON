package com.teambind.coupon.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate Limiting 어노테이션
 * 메서드 레벨에서 요청 속도 제한 적용
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Rate limit 키 (SpEL 표현식 지원)
     * 예: "#userId", "#request.ip"
     */
    String key() default "";

    /**
     * 분당 제한 횟수
     */
    int perMinute() default 60;

    /**
     * 시간당 제한 횟수
     */
    int perHour() default 600;

    /**
     * 일당 제한 횟수 (0 = 제한 없음)
     */
    int perDay() default 0;

    /**
     * 제한 초과 시 에러 메시지
     */
    String message() default "요청 속도 제한을 초과했습니다";

    /**
     * 차단 시 차단 시간 (분)
     */
    int blockDurationMinutes() default 60;
}
package com.teambind.coupon.common.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 유스케이스 구현체를 표시하는 어노테이션
 * 헥사고날 아키텍처에서 Application Service Layer를 명시
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface UseCase {

    /**
     * 빈 이름 지정
     */
    @AliasFor(annotation = Component.class)
    String value() default "";
}
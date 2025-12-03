package com.teambind.coupon.common;

import jakarta.validation.*;
import java.util.Set;

/**
 * 자기 검증 기능을 제공하는 추상 클래스
 * 생성 시점에 자동으로 유효성을 검증합니다.
 */
public abstract class SelfValidating<T> {

    private Validator validator;

    public SelfValidating() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * 객체의 유효성을 검증합니다.
     * @throws ConstraintViolationException 유효성 검증 실패 시
     */
    protected void validateSelf() {
        @SuppressWarnings("unchecked")
        Set<ConstraintViolation<T>> violations = validator.validate((T) this);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
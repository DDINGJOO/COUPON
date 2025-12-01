package com.teambind.coupon.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 쿠폰 코드 생성기 팩토리
 * 요청에 따라 적절한 코드 생성기 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponCodeGeneratorFactory {

    private final List<CouponCodeGenerator> generators;
    private final Map<CouponCodeGenerator.GeneratorType, CouponCodeGenerator> generatorMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // 생성기들을 타입별로 맵에 등록
        generators.forEach(generator -> {
            generatorMap.put(generator.getType(), generator);
            log.info("쿠폰 코드 생성기 등록: {}", generator.getType());
        });
    }

    /**
     * 타입에 따른 생성기 반환
     *
     * @param type 생성기 타입
     * @return 쿠폰 코드 생성기
     */
    public CouponCodeGenerator getGenerator(CouponCodeGenerator.GeneratorType type) {
        CouponCodeGenerator generator = generatorMap.get(type);

        if (generator == null) {
            log.warn("요청된 생성기 타입을 찾을 수 없습니다. 기본 생성기 사용: {}", type);
            generator = generatorMap.get(CouponCodeGenerator.GeneratorType.ALPHANUMERIC);
        }

        return generator;
    }

    /**
     * 기본 생성기 반환 (ALPHANUMERIC)
     *
     * @return 기본 쿠폰 코드 생성기
     */
    public CouponCodeGenerator getDefaultGenerator() {
        return getGenerator(CouponCodeGenerator.GeneratorType.ALPHANUMERIC);
    }

    /**
     * 보안이 중요한 쿠폰용 생성기 반환
     *
     * @return 보안 강화 쿠폰 코드 생성기
     */
    public CouponCodeGenerator getSecureGenerator() {
        return getGenerator(CouponCodeGenerator.GeneratorType.SECURE_RANDOM);
    }

    /**
     * 커스텀 패턴 생성기 반환
     *
     * @return 커스텀 패턴 쿠폰 코드 생성기
     */
    public CouponCodeGenerator getCustomGenerator() {
        return getGenerator(CouponCodeGenerator.GeneratorType.CUSTOM);
    }

    /**
     * 사용 가능한 모든 생성기 타입 반환
     *
     * @return 생성기 타입 배열
     */
    public CouponCodeGenerator.GeneratorType[] getAvailableTypes() {
        return generatorMap.keySet().toArray(new CouponCodeGenerator.GeneratorType[0]);
    }
}
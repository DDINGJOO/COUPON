package com.teambind.coupon.domain.service;

/**
 * 쿠폰 코드 생성기 인터페이스
 * 다양한 전략으로 유니크한 쿠폰 코드 생성
 */
public interface CouponCodeGenerator {

    /**
     * 쿠폰 코드 생성
     *
     * @return 생성된 쿠폰 코드
     */
    String generate();

    /**
     * 프리픽스를 포함한 쿠폰 코드 생성
     *
     * @param prefix 쿠폰 코드 프리픽스
     * @return 생성된 쿠폰 코드
     */
    String generate(String prefix);

    /**
     * 여러 개의 쿠폰 코드 일괄 생성
     *
     * @param count 생성할 코드 개수
     * @return 생성된 쿠폰 코드 배열
     */
    String[] generateBatch(int count);

    /**
     * 생성기 타입 반환
     *
     * @return 생성기 타입
     */
    GeneratorType getType();

    /**
     * 쿠폰 코드 생성 전략 타입
     */
    enum GeneratorType {
        ALPHANUMERIC,    // 영숫자 조합
        NUMERIC,         // 숫자만
        CUSTOM,          // 커스텀 패턴
        UUID,            // UUID 기반
        SECURE_RANDOM    // 보안 랜덤
    }
}
package com.teambind.coupon.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * 영숫자 조합 쿠폰 코드 생성기
 * 대소문자 영문자와 숫자를 조합하여 쿠폰 코드 생성
 */
@Slf4j
@Component
public class AlphanumericCodeGenerator implements CouponCodeGenerator {

    // 사용할 문자 집합 (혼동하기 쉬운 문자 제외: 0, O, I, l)
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int DEFAULT_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate() {
        return generateCode(DEFAULT_LENGTH);
    }

    @Override
    public String generate(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return generate();
        }

        // 프리픽스 정규화 (대문자 변환, 특수문자 제거)
        String normalizedPrefix = prefix.toUpperCase().replaceAll("[^A-Z0-9]", "");

        // 프리픽스 + 구분자 + 랜덤 코드
        String randomPart = generateCode(DEFAULT_LENGTH - normalizedPrefix.length() - 1);
        return String.format("%s-%s", normalizedPrefix, formatCode(randomPart));
    }

    @Override
    public String[] generateBatch(int count) {
        if (count <= 0 || count > 10000) {
            throw new IllegalArgumentException("생성 개수는 1~10000 사이여야 합니다");
        }

        Set<String> codes = new HashSet<>(count);
        String[] result = new String[count];

        // 중복 방지를 위해 Set 사용
        while (codes.size() < count) {
            codes.add(generate());
        }

        codes.toArray(result);
        log.info("쿠폰 코드 {} 개 생성 완료", count);

        return result;
    }

    @Override
    public GeneratorType getType() {
        return GeneratorType.ALPHANUMERIC;
    }

    /**
     * 지정된 길이의 랜덤 코드 생성
     *
     * @param length 코드 길이
     * @return 생성된 코드
     */
    private String generateCode(int length) {
        StringBuilder code = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHARS.length());
            code.append(CHARS.charAt(index));
        }

        return code.toString();
    }

    /**
     * 코드를 읽기 쉽게 포맷팅 (4자리씩 구분)
     *
     * @param code 원본 코드
     * @return 포맷된 코드
     */
    private String formatCode(String code) {
        if (code == null || code.length() <= 4) {
            return code;
        }

        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < code.length(); i += 4) {
            if (i > 0) {
                formatted.append("-");
            }
            int end = Math.min(i + 4, code.length());
            formatted.append(code.substring(i, end));
        }

        return formatted.toString();
    }
}
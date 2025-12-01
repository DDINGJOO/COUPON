package com.teambind.coupon.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * 커스텀 패턴 기반 쿠폰 코드 생성기
 * 지정된 패턴에 따라 쿠폰 코드 생성
 *
 * 패턴 예시:
 * - {PREFIX}: 프리픽스
 * - {YYYY}: 연도
 * - {MM}: 월
 * - {DD}: 일
 * - {RAND:n}: n자리 랜덤 문자
 * - {NUM:n}: n자리 랜덤 숫자
 */
@Slf4j
@Component
public class CustomPatternCodeGenerator implements CouponCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHA_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUM_CHARS = "0123456789";

    @Value("${coupon.code.pattern:COUP-{YYYY}{MM}-{RAND:4}-{NUM:4}}")
    private String defaultPattern;

    @Override
    public String generate() {
        return generateByPattern(defaultPattern);
    }

    @Override
    public String generate(String prefix) {
        // 프리픽스를 포함한 패턴 생성
        String pattern = "{PREFIX}-{YYYY}{MM}-{RAND:4}-{NUM:4}";
        return generateByPattern(pattern, prefix);
    }

    @Override
    public String[] generateBatch(int count) {
        if (count <= 0 || count > 5000) {
            throw new IllegalArgumentException("생성 개수는 1~5000 사이여야 합니다");
        }

        Set<String> codes = new HashSet<>(count);
        String[] result = new String[count];

        while (codes.size() < count) {
            codes.add(generate());
        }

        codes.toArray(result);
        log.info("커스텀 패턴 쿠폰 코드 {} 개 생성 완료", count);

        return result;
    }

    @Override
    public GeneratorType getType() {
        return GeneratorType.CUSTOM;
    }

    /**
     * 패턴에 따라 코드 생성
     *
     * @param pattern 코드 패턴
     * @return 생성된 코드
     */
    public String generateByPattern(String pattern) {
        return generateByPattern(pattern, null);
    }

    /**
     * 패턴과 프리픽스를 사용하여 코드 생성
     *
     * @param pattern 코드 패턴
     * @param prefix  프리픽스
     * @return 생성된 코드
     */
    public String generateByPattern(String pattern, String prefix) {
        String result = pattern;
        LocalDateTime now = LocalDateTime.now();

        // 프리픽스 치환
        if (prefix != null && !prefix.isEmpty()) {
            result = result.replace("{PREFIX}", prefix.toUpperCase());
        } else {
            result = result.replace("{PREFIX}-", "").replace("{PREFIX}", "");
        }

        // 날짜 패턴 치환
        result = result.replace("{YYYY}", now.format(DateTimeFormatter.ofPattern("yyyy")));
        result = result.replace("{YY}", now.format(DateTimeFormatter.ofPattern("yy")));
        result = result.replace("{MM}", now.format(DateTimeFormatter.ofPattern("MM")));
        result = result.replace("{DD}", now.format(DateTimeFormatter.ofPattern("dd")));
        result = result.replace("{HH}", now.format(DateTimeFormatter.ofPattern("HH")));

        // 랜덤 문자 패턴 치환
        result = replaceRandomPattern(result, "{RAND:", ALPHA_CHARS);

        // 랜덤 숫자 패턴 치환
        result = replaceRandomPattern(result, "{NUM:", NUM_CHARS);

        // 시퀀스 패턴 치환 (간단한 구현)
        if (result.contains("{SEQ:")) {
            result = replaceSequencePattern(result);
        }

        return result;
    }

    /**
     * 랜덤 패턴 치환
     *
     * @param input    입력 문자열
     * @param pattern  찾을 패턴
     * @param charSet  사용할 문자 집합
     * @return 치환된 문자열
     */
    private String replaceRandomPattern(String input, String pattern, String charSet) {
        String result = input;

        while (result.contains(pattern)) {
            int startIdx = result.indexOf(pattern);
            int endIdx = result.indexOf("}", startIdx);

            if (endIdx == -1) {
                break;
            }

            String fullPattern = result.substring(startIdx, endIdx + 1);
            String lengthStr = fullPattern.substring(pattern.length(), fullPattern.length() - 1);

            try {
                int length = Integer.parseInt(lengthStr);
                String randomStr = generateRandom(charSet, length);
                result = result.replace(fullPattern, randomStr);
            } catch (NumberFormatException e) {
                log.warn("잘못된 패턴 길이: {}", lengthStr);
                break;
            }
        }

        return result;
    }

    /**
     * 시퀀스 패턴 치환 (간단한 구현)
     *
     * @param input 입력 문자열
     * @return 치환된 문자열
     */
    private String replaceSequencePattern(String input) {
        // 실제로는 데이터베이스나 Redis를 통해 시퀀스 관리 필요
        String result = input;

        if (result.contains("{SEQ:")) {
            int startIdx = result.indexOf("{SEQ:");
            int endIdx = result.indexOf("}", startIdx);

            if (endIdx != -1) {
                String fullPattern = result.substring(startIdx, endIdx + 1);
                String lengthStr = fullPattern.substring(5, fullPattern.length() - 1);

                try {
                    int length = Integer.parseInt(lengthStr);
                    // 간단한 구현을 위해 랜덤 숫자 사용 (실제로는 시퀀스 사용)
                    long sequence = System.currentTimeMillis() % (long) Math.pow(10, length);
                    String seqStr = String.format("%0" + length + "d", sequence);
                    result = result.replace(fullPattern, seqStr);
                } catch (NumberFormatException e) {
                    log.warn("잘못된 시퀀스 길이: {}", lengthStr);
                }
            }
        }

        return result;
    }

    /**
     * 지정된 문자 집합에서 랜덤 문자열 생성
     *
     * @param charSet 문자 집합
     * @param length  길이
     * @return 생성된 문자열
     */
    private String generateRandom(String charSet, int length) {
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(charSet.length());
            sb.append(charSet.charAt(index));
        }

        return sb.toString();
    }
}
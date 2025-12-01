package com.teambind.coupon.common.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * 쿠폰 코드 생성기
 * 다양한 전략으로 쿠폰 코드를 생성
 */
@Component
public class CouponCodeGenerator {

    private static final SecureRandom random = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * 알파뉴메릭 전략
     */
    public static class AlphanumericStrategy {

        public String generate(int length) {
            if (length <= 0) {
                throw new IllegalArgumentException("Length must be positive");
            }

            StringBuilder code = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                code.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
            }
            return code.toString();
        }

        public Set<String> generateBatch(int length, int count) {
            Set<String> codes = new HashSet<>();
            int maxAttempts = count * 10; // 충돌 방지를 위한 최대 시도 횟수
            int attempts = 0;

            while (codes.size() < count && attempts < maxAttempts) {
                codes.add(generate(length));
                attempts++;
            }

            if (codes.size() < count) {
                throw new RuntimeException("Could not generate enough unique codes");
            }

            return codes;
        }
    }

    /**
     * 프리픽스 기반 전략
     */
    public static class PrefixBasedStrategy {

        public String generate(String prefix, int suffixLength) {
            if (prefix == null || prefix.isEmpty()) {
                throw new IllegalArgumentException("Prefix cannot be null or empty");
            }
            if (suffixLength <= 0) {
                throw new IllegalArgumentException("Suffix length must be positive");
            }

            StringBuilder suffix = new StringBuilder(suffixLength);
            for (int i = 0; i < suffixLength; i++) {
                suffix.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
            }
            return prefix + suffix.toString();
        }

        public Set<String> generateBatch(String prefix, int suffixLength, int count) {
            Set<String> codes = new HashSet<>();
            int maxAttempts = count * 10;
            int attempts = 0;

            while (codes.size() < count && attempts < maxAttempts) {
                codes.add(generate(prefix, suffixLength));
                attempts++;
            }

            if (codes.size() < count) {
                throw new RuntimeException("Could not generate enough unique codes");
            }

            return codes;
        }
    }

    /**
     * 날짜 기반 전략
     */
    public static class DateBasedStrategy {

        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

        public String generate(LocalDateTime date, int randomLength) {
            if (date == null) {
                date = LocalDateTime.now();
            }
            if (randomLength <= 0) {
                throw new IllegalArgumentException("Random length must be positive");
            }

            String datePrefix = date.format(FORMATTER);
            StringBuilder randomSuffix = new StringBuilder(randomLength);
            for (int i = 0; i < randomLength; i++) {
                randomSuffix.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
            }
            return datePrefix + randomSuffix.toString();
        }

        public String generateWithCategory(LocalDateTime date, String category, int randomLength) {
            if (category == null || category.isEmpty()) {
                throw new IllegalArgumentException("Category cannot be null or empty");
            }

            String dateCode = generate(date, randomLength);
            return category + "_" + dateCode;
        }
    }

    /**
     * 커스텀 패턴 전략
     */
    public static class CustomPatternStrategy {

        /**
         * 패턴 예시: {PREFIX}-{YYYY}-{MM}-{RAND:4}
         * 결과 예시: COUPON-2024-01-ABCD
         */
        public String generate(String pattern, LocalDateTime date) {
            if (pattern == null || pattern.isEmpty()) {
                throw new IllegalArgumentException("Pattern cannot be null or empty");
            }
            if (date == null) {
                date = LocalDateTime.now();
            }

            String result = pattern;

            // 년도 치환
            result = result.replace("{YYYY}", String.format("%04d", date.getYear()));
            result = result.replace("{YY}", String.format("%02d", date.getYear() % 100));

            // 월 치환
            result = result.replace("{MM}", String.format("%02d", date.getMonthValue()));

            // 일 치환
            result = result.replace("{DD}", String.format("%02d", date.getDayOfMonth()));

            // 랜덤 문자 치환
            while (result.contains("{RAND:")) {
                int start = result.indexOf("{RAND:");
                int end = result.indexOf("}", start);
                if (end > start) {
                    String lengthStr = result.substring(start + 6, end);
                    try {
                        int length = Integer.parseInt(lengthStr);
                        StringBuilder rand = new StringBuilder(length);
                        for (int i = 0; i < length; i++) {
                            rand.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
                        }
                        result = result.substring(0, start) + rand + result.substring(end + 1);
                    } catch (NumberFormatException e) {
                        break;
                    }
                }
            }

            return result;
        }
    }

    // 기본 전략 인스턴스
    private final AlphanumericStrategy alphanumeric = new AlphanumericStrategy();
    private final PrefixBasedStrategy prefixBased = new PrefixBasedStrategy();
    private final DateBasedStrategy dateBased = new DateBasedStrategy();
    private final CustomPatternStrategy customPattern = new CustomPatternStrategy();

    /**
     * 간단한 알파뉴메릭 코드 생성
     */
    public String generateSimple(int length) {
        return alphanumeric.generate(length);
    }

    /**
     * 프리픽스가 있는 코드 생성
     */
    public String generateWithPrefix(String prefix, int suffixLength) {
        return prefixBased.generate(prefix, suffixLength);
    }

    /**
     * 날짜 기반 코드 생성
     */
    public String generateDateBased(int randomLength) {
        return dateBased.generate(LocalDateTime.now(), randomLength);
    }

    /**
     * 커스텀 패턴 코드 생성
     */
    public String generateCustom(String pattern) {
        return customPattern.generate(pattern, LocalDateTime.now());
    }
}
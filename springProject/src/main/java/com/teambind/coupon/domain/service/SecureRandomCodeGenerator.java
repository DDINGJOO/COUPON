package com.teambind.coupon.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * 보안 강화 랜덤 쿠폰 코드 생성기
 * HMAC과 SecureRandom을 조합하여 예측 불가능한 쿠폰 코드 생성
 */
@Slf4j
@Component
public class SecureRandomCodeGenerator implements CouponCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SECRET_KEY = "CouponSecretKey2024!@#"; // 실제로는 설정 파일에서 관리
    private static final int CODE_LENGTH = 16;

    @Override
    public String generate() {
        try {
            // 1. 랜덤 바이트 생성
            byte[] randomBytes = new byte[16];
            SECURE_RANDOM.nextBytes(randomBytes);

            // 2. 타임스탬프 추가
            long timestamp = Instant.now().toEpochMilli();
            String input = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes) + timestamp;

            // 3. HMAC 생성
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));

            // 4. Base64 인코딩 후 URL-safe 변환
            String code = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hmacBytes)
                    .substring(0, CODE_LENGTH)
                    .toUpperCase();

            return formatSecureCode(code);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("보안 코드 생성 실패", e);
            throw new RuntimeException("쿠폰 코드 생성 중 오류가 발생했습니다", e);
        }
    }

    @Override
    public String generate(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return generate();
        }

        String normalizedPrefix = prefix.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String secureCode = generate();

        // 프리픽스와 보안 코드 조합
        return String.format("%s:%s", normalizedPrefix, secureCode);
    }

    @Override
    public String[] generateBatch(int count) {
        if (count <= 0 || count > 1000) {
            throw new IllegalArgumentException("보안 코드 생성은 1~1000개까지 가능합니다");
        }

        Set<String> codes = new HashSet<>(count);
        String[] result = new String[count];

        // 중복 방지
        while (codes.size() < count) {
            codes.add(generate());
        }

        codes.toArray(result);
        log.info("보안 쿠폰 코드 {} 개 생성 완료", count);

        return result;
    }

    @Override
    public GeneratorType getType() {
        return GeneratorType.SECURE_RANDOM;
    }

    /**
     * 보안 코드 포맷팅
     * XXXX-XXXX-XXXX-XXXX 형식
     *
     * @param code 원본 코드
     * @return 포맷된 코드
     */
    private String formatSecureCode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return code;
        }

        return String.format("%s-%s-%s-%s",
                code.substring(0, 4),
                code.substring(4, 8),
                code.substring(8, 12),
                code.substring(12, 16));
    }
}
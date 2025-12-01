package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.domain.service.CouponCodeGenerator;
import com.teambind.coupon.domain.service.CouponCodeGeneratorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 쿠폰 코드 관리 서비스
 * 쿠폰 코드 생성, 검증, 중복 확인 등 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponCodeService {

    private final CouponCodeGeneratorFactory generatorFactory;
    private final LoadCouponPolicyPort loadCouponPolicyPort;

    /**
     * 단일 쿠폰 코드 생성
     *
     * @param type   생성기 타입
     * @param prefix 프리픽스 (선택)
     * @return 생성된 쿠폰 코드
     */
    public String generateCode(CouponCodeGenerator.GeneratorType type, String prefix) {
        CouponCodeGenerator generator = generatorFactory.getGenerator(type);

        String code;
        int attempts = 0;
        final int maxAttempts = 10;

        // 중복 체크하며 유니크한 코드 생성
        do {
            if (prefix != null && !prefix.isEmpty()) {
                code = generator.generate(prefix);
            } else {
                code = generator.generate();
            }
            attempts++;

            if (attempts >= maxAttempts) {
                log.error("쿠폰 코드 생성 실패 - 최대 시도 횟수 초과");
                throw new RuntimeException("유니크한 쿠폰 코드 생성에 실패했습니다");
            }
        } while (isCodeExists(code));

        log.info("쿠폰 코드 생성 완료 - type: {}, code: {}", type, code);
        return code;
    }

    /**
     * 기본 타입으로 쿠폰 코드 생성
     *
     * @return 생성된 쿠폰 코드
     */
    public String generateCode() {
        return generateCode(CouponCodeGenerator.GeneratorType.ALPHANUMERIC, null);
    }

    /**
     * 여러 개의 쿠폰 코드 일괄 생성
     *
     * @param type   생성기 타입
     * @param count  생성할 개수
     * @param prefix 프리픽스 (선택)
     * @return 생성된 쿠폰 코드 리스트
     */
    public List<String> generateCodes(CouponCodeGenerator.GeneratorType type, int count, String prefix) {
        if (count <= 0 || count > 10000) {
            throw new IllegalArgumentException("생성 개수는 1~10000 사이여야 합니다");
        }

        CouponCodeGenerator generator = generatorFactory.getGenerator(type);
        Set<String> codes = new HashSet<>(count);
        int attempts = 0;
        final int maxTotalAttempts = count * 10;

        while (codes.size() < count && attempts < maxTotalAttempts) {
            String code;
            if (prefix != null && !prefix.isEmpty()) {
                code = generator.generate(prefix);
            } else {
                code = generator.generate();
            }

            // 중복 체크
            if (!isCodeExists(code) && codes.add(code)) {
                log.debug("쿠폰 코드 생성: {}/{}", codes.size(), count);
            }
            attempts++;
        }

        if (codes.size() < count) {
            log.warn("요청된 개수만큼 쿠폰 코드를 생성하지 못했습니다. 요청: {}, 생성: {}", count, codes.size());
        }

        List<String> result = new ArrayList<>(codes);
        log.info("쿠폰 코드 일괄 생성 완료 - type: {}, count: {}", type, result.size());

        return result;
    }

    /**
     * 쿠폰 코드 유효성 검증
     *
     * @param code 검증할 코드
     * @return 유효성 여부
     */
    public boolean validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        // 기본 형식 검증 (영숫자와 하이픈만 허용)
        if (!code.matches("^[A-Z0-9\\-:]+$")) {
            log.debug("쿠폰 코드 형식 검증 실패: {}", code);
            return false;
        }

        // 최소/최대 길이 검증
        String cleanCode = code.replaceAll("[\\-:]", "");
        if (cleanCode.length() < 8 || cleanCode.length() > 32) {
            log.debug("쿠폰 코드 길이 검증 실패: {}", code);
            return false;
        }

        return true;
    }

    /**
     * 쿠폰 코드 정규화
     * 입력된 코드를 표준 형식으로 변환
     *
     * @param code 원본 코드
     * @return 정규화된 코드
     */
    public String normalizeCode(String code) {
        if (code == null) {
            return null;
        }

        // 공백 제거, 대문자 변환
        return code.trim().toUpperCase()
                .replaceAll("\\s+", "")  // 모든 공백 제거
                .replaceAll("[ _]", "-"); // 언더스코어를 하이픈으로 변환
    }

    /**
     * 쿠폰 코드 마스킹
     * 보안을 위해 일부만 표시
     *
     * @param code 원본 코드
     * @return 마스킹된 코드
     */
    public String maskCode(String code) {
        if (code == null || code.length() < 8) {
            return code;
        }

        // 앞 4자리와 뒤 4자리만 표시
        String clean = code.replaceAll("[\\-:]", "");
        if (clean.length() <= 8) {
            return code;
        }

        return clean.substring(0, 4) + "****" + clean.substring(clean.length() - 4);
    }

    /**
     * 코드 존재 여부 확인
     *
     * @param code 확인할 코드
     * @return 존재 여부
     */
    @Cacheable(value = "couponCodeExists", key = "#code")
    private boolean isCodeExists(String code) {
        // 실제로는 데이터베이스에서 확인
        return loadCouponPolicyPort.existsByCode(code);
    }

    /**
     * 코드 생성 통계
     */
    public static class CodeGenerationStats {
        private final Map<CouponCodeGenerator.GeneratorType, Integer> generatedCounts = new HashMap<>();
        private int totalGenerated = 0;
        private int duplicatesAvoided = 0;

        public void recordGeneration(CouponCodeGenerator.GeneratorType type, int count) {
            generatedCounts.merge(type, count, Integer::sum);
            totalGenerated += count;
        }

        public void recordDuplicate() {
            duplicatesAvoided++;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalGenerated", totalGenerated);
            stats.put("duplicatesAvoided", duplicatesAvoided);
            stats.put("byType", generatedCounts);
            return stats;
        }
    }
}
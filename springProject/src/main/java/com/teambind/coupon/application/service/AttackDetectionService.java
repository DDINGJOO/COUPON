package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 공격 탐지 및 차단 서비스
 * 무작위 대입 공격, 스캐닝, DDoS 등을 탐지하고 차단
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttackDetectionService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimiterService rateLimiterService;

    @Value("${coupon.security.max-failures:10}")
    private int maxFailures;

    @Value("${coupon.security.block-duration:24h}")
    private String blockDuration;

    // 공격 패턴 임계값
    private static final int SCAN_THRESHOLD = 5;  // 연속 실패 임계값
    private static final int BRUTE_FORCE_THRESHOLD = 10;  // 무작위 대입 임계값
    private static final int SUSPICIOUS_RATE = 100;  // 의심스러운 요청 비율

    /**
     * 실패 기록
     *
     * @param identifier 식별자 (IP, userId 등)
     * @param action     액션 (login, coupon_download 등)
     */
    public void recordFailure(String identifier, String action) {
        String failureKey = generateFailureKey(identifier, action);

        try {
            Long failures = redisTemplate.opsForValue().increment(failureKey);
            redisTemplate.expire(failureKey, Duration.parse("PT" + blockDuration));

            if (failures != null) {
                log.info("실패 기록 - identifier: {}, action: {}, count: {}", identifier, action, failures);

                // 임계값 확인
                checkThresholds(identifier, action, failures);
            }
        } catch (Exception e) {
            log.error("실패 기록 오류 - identifier: {}, action: {}", identifier, action, e);
        }
    }

    /**
     * 성공 기록 (실패 카운터 리셋)
     *
     * @param identifier 식별자
     * @param action     액션
     */
    public void recordSuccess(String identifier, String action) {
        String failureKey = generateFailureKey(identifier, action);

        try {
            redisTemplate.delete(failureKey);
            log.debug("성공 기록 - 실패 카운터 리셋 - identifier: {}, action: {}", identifier, action);
        } catch (Exception e) {
            log.error("성공 기록 오류 - identifier: {}, action: {}", identifier, action, e);
        }
    }

    /**
     * 차단 여부 확인
     *
     * @param identifier 식별자
     * @return 차단 여부
     */
    public boolean isBlocked(String identifier) {
        String blockKey = generateBlockKey(identifier);

        try {
            Boolean blocked = redisTemplate.hasKey(blockKey);
            return blocked != null && blocked;
        } catch (Exception e) {
            log.error("차단 확인 오류 - identifier: {}", identifier, e);
            return false;
        }
    }

    /**
     * 사용자 차단
     *
     * @param identifier 식별자
     * @param reason     차단 사유
     * @param duration   차단 기간
     */
    public void blockUser(String identifier, String reason, Duration duration) {
        String blockKey = generateBlockKey(identifier);

        try {
            redisTemplate.opsForValue().set(
                    blockKey,
                    String.format("%s|%s", reason, LocalDateTime.now()),
                    duration
            );

            // 차단 이력 기록
            recordBlockHistory(identifier, reason);

            log.warn("사용자 차단 - identifier: {}, reason: {}, duration: {}",
                    identifier, reason, duration);
        } catch (Exception e) {
            log.error("사용자 차단 오류 - identifier: {}", identifier, e);
        }
    }

    /**
     * 차단 해제
     *
     * @param identifier 식별자
     */
    public void unblock(String identifier) {
        String blockKey = generateBlockKey(identifier);

        try {
            redisTemplate.delete(blockKey);
            log.info("차단 해제 - identifier: {}", identifier);
        } catch (Exception e) {
            log.error("차단 해제 오류 - identifier: {}", identifier, e);
        }
    }

    /**
     * 무작위 대입 공격 탐지
     *
     * @param identifier  식별자
     * @param couponCode  시도한 쿠폰 코드
     * @return 공격 탐지 여부
     */
    public boolean detectBruteForce(String identifier, String couponCode) {
        String bruteForceKey = "brute_force:" + identifier;

        try {
            // 최근 시도한 코드 기록
            redisTemplate.opsForSet().add(bruteForceKey, couponCode);
            redisTemplate.expire(bruteForceKey, 1, TimeUnit.HOURS);

            // 시도 횟수 확인
            Long attempts = redisTemplate.opsForSet().size(bruteForceKey);
            if (attempts != null && attempts > BRUTE_FORCE_THRESHOLD) {
                log.warn("무작위 대입 공격 탐지 - identifier: {}, attempts: {}", identifier, attempts);
                blockUser(identifier, "BRUTE_FORCE", Duration.parse("PT" + blockDuration));
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("무작위 대입 탐지 오류 - identifier: {}", identifier, e);
            return false;
        }
    }

    /**
     * 스캔 공격 탐지
     *
     * @param identifier 식별자
     * @param pattern    요청 패턴
     * @return 공격 탐지 여부
     */
    public boolean detectScanning(String identifier, String pattern) {
        String scanKey = "scan_pattern:" + identifier;

        try {
            // 패턴 기록
            redisTemplate.opsForList().rightPush(scanKey, pattern);
            redisTemplate.expire(scanKey, 10, TimeUnit.MINUTES);

            // 최근 패턴 확인
            Long patternCount = redisTemplate.opsForList().size(scanKey);
            if (patternCount != null && patternCount > SCAN_THRESHOLD) {
                // 패턴 분석
                if (analyzePatterns(identifier)) {
                    log.warn("스캔 공격 탐지 - identifier: {}", identifier);
                    blockUser(identifier, "SCANNING", Duration.ofHours(6));
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            log.error("스캔 탐지 오류 - identifier: {}", identifier, e);
            return false;
        }
    }

    /**
     * 의심스러운 활동 점수 계산
     *
     * @param identifier 식별자
     * @return 위험 점수 (0-100)
     */
    public int calculateRiskScore(String identifier) {
        int score = 0;

        try {
            // 실패 횟수
            String failureKey = "failure:*:" + identifier;
            Set<String> failureKeys = redisTemplate.keys(failureKey);
            if (failureKeys != null) {
                score += Math.min(failureKeys.size() * 10, 30);
            }

            // Rate limit 초과 횟수
            if (!rateLimiterService.allowRequest("risk_check:" + identifier, 1000, 3600)) {
                score += 20;
            }

            // 차단 이력
            if (hasBlockHistory(identifier)) {
                score += 30;
            }

            // 무작위 대입 시도
            String bruteForceKey = "brute_force:" + identifier;
            Long bruteForceAttempts = redisTemplate.opsForSet().size(bruteForceKey);
            if (bruteForceAttempts != null && bruteForceAttempts > 0) {
                score += Math.min(bruteForceAttempts * 5, 20);
            }

            log.debug("위험 점수 계산 - identifier: {}, score: {}", identifier, score);
            return Math.min(score, 100);

        } catch (Exception e) {
            log.error("위험 점수 계산 오류 - identifier: {}", identifier, e);
            return 0;
        }
    }

    /**
     * 임계값 확인 및 자동 차단
     */
    private void checkThresholds(String identifier, String action, long failures) {
        if (failures >= maxFailures) {
            log.warn("최대 실패 횟수 초과 - identifier: {}, action: {}, failures: {}",
                    identifier, action, failures);
            blockUser(identifier, "MAX_FAILURES", Duration.parse("PT" + blockDuration));
        }
    }

    /**
     * 패턴 분석
     */
    private boolean analyzePatterns(String identifier) {
        String scanKey = "scan_pattern:" + identifier;

        try {
            // 최근 패턴 조회
            var patterns = redisTemplate.opsForList().range(scanKey, -10, -1);
            if (patterns == null || patterns.isEmpty()) {
                return false;
            }

            // 연속적인 패턴 확인 (예: 순차적 ID 시도)
            return isSequentialPattern(patterns) || isRandomPattern(patterns);

        } catch (Exception e) {
            log.error("패턴 분석 오류 - identifier: {}", identifier, e);
            return false;
        }
    }

    /**
     * 순차적 패턴 확인
     */
    private boolean isSequentialPattern(java.util.List<String> patterns) {
        // 간단한 순차 패턴 확인 로직
        int sequential = 0;
        for (int i = 1; i < patterns.size(); i++) {
            if (isSequential(patterns.get(i - 1), patterns.get(i))) {
                sequential++;
            }
        }
        return sequential > patterns.size() / 2;
    }

    /**
     * 랜덤 패턴 확인
     */
    private boolean isRandomPattern(java.util.List<String> patterns) {
        // 모든 패턴이 다른 경우
        return patterns.stream().distinct().count() == patterns.size();
    }

    /**
     * 두 문자열이 순차적인지 확인
     */
    private boolean isSequential(String s1, String s2) {
        try {
            // 숫자로 끝나는 경우 순차 확인
            String num1 = s1.replaceAll("[^0-9]+$", "");
            String num2 = s2.replaceAll("[^0-9]+$", "");

            if (!num1.isEmpty() && !num2.isEmpty()) {
                return Integer.parseInt(num2) - Integer.parseInt(num1) == 1;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 차단 이력 기록
     */
    private void recordBlockHistory(String identifier, String reason) {
        String historyKey = "block_history:" + identifier;

        try {
            String record = String.format("%s|%s", reason, LocalDateTime.now());
            redisTemplate.opsForList().rightPush(historyKey, record);
            redisTemplate.expire(historyKey, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("차단 이력 기록 오류 - identifier: {}", identifier, e);
        }
    }

    /**
     * 차단 이력 확인
     */
    private boolean hasBlockHistory(String identifier) {
        String historyKey = "block_history:" + identifier;

        try {
            Long size = redisTemplate.opsForList().size(historyKey);
            return size != null && size > 0;
        } catch (Exception e) {
            log.error("차단 이력 확인 오류 - identifier: {}", identifier, e);
            return false;
        }
    }

    private String generateFailureKey(String identifier, String action) {
        return String.format("failure:%s:%s", action, identifier);
    }

    private String generateBlockKey(String identifier) {
        return "blocked:" + identifier;
    }
}
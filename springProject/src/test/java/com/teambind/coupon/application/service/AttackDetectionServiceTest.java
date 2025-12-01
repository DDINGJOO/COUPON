package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.out.SaveAttackLogPort;
import com.teambind.coupon.common.exception.SecurityException;
import com.teambind.coupon.domain.model.AttackLog;
import com.teambind.coupon.domain.model.AttackType;
import com.teambind.coupon.domain.model.ThreatLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 공격 탐지 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("공격 탐지 서비스 테스트")
class AttackDetectionServiceTest {

    @InjectMocks
    private AttackDetectionService attackDetectionService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SaveAttackLogPort saveAttackLogPort;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("Rate Limiting 테스트")
    class RateLimitingTest {

        @Test
        @DisplayName("정상 요청 - 제한 이내")
        void normalRequest() {
            // given
            Long userId = 100L;
            String endpoint = "/api/coupons/download";
            when(valueOperations.get(anyString())).thenReturn(5);

            // when
            boolean result = attackDetectionService.checkRateLimit(userId, endpoint);

            // then
            assertThat(result).isTrue();
            verify(valueOperations, never()).setIfAbsent(anyString(), anyInt(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("제한 초과 요청")
        void rateLimitExceeded() {
            // given
            Long userId = 100L;
            String endpoint = "/api/coupons/download";
            when(valueOperations.get(anyString())).thenReturn(100); // 제한 초과

            // when
            boolean result = attackDetectionService.checkRateLimit(userId, endpoint);

            // then
            assertThat(result).isFalse();
            verify(saveAttackLogPort).save(argThat(log ->
                log.getAttackType() == AttackType.RATE_LIMIT_VIOLATION
            ));
        }

        @Test
        @DisplayName("첫 요청 - 카운터 초기화")
        void firstRequest() {
            // given
            Long userId = 100L;
            String endpoint = "/api/coupons/download";
            when(valueOperations.get(anyString())).thenReturn(null);
            when(valueOperations.setIfAbsent(anyString(), anyInt(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);

            // when
            boolean result = attackDetectionService.checkRateLimit(userId, endpoint);

            // then
            assertThat(result).isTrue();
            verify(valueOperations).setIfAbsent(anyString(), eq(1), eq(60L), eq(TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("SQL Injection 탐지 테스트")
    class SQLInjectionDetectionTest {

        @Test
        @DisplayName("정상 쿠폰 코드")
        void normalCouponCode() {
            // given
            String normalCode = "SUMMER2024";

            // when
            boolean detected = attackDetectionService.detectSQLInjection(normalCode);

            // then
            assertThat(detected).isFalse();
        }

        @Test
        @DisplayName("SQL Injection 패턴 탐지 - OR 1=1")
        void detectSQLInjectionOR() {
            // given
            String maliciousCode = "' OR '1'='1";

            // when
            boolean detected = attackDetectionService.detectSQLInjection(maliciousCode);

            // then
            assertThat(detected).isTrue();
            verify(saveAttackLogPort).save(argThat(log ->
                log.getAttackType() == AttackType.SQL_INJECTION
            ));
        }

        @Test
        @DisplayName("SQL Injection 패턴 탐지 - DROP TABLE")
        void detectSQLInjectionDrop() {
            // given
            String maliciousCode = "'; DROP TABLE coupons; --";

            // when
            boolean detected = attackDetectionService.detectSQLInjection(maliciousCode);

            // then
            assertThat(detected).isTrue();
        }

        @Test
        @DisplayName("SQL Injection 패턴 탐지 - UNION SELECT")
        void detectSQLInjectionUnion() {
            // given
            String maliciousCode = "' UNION SELECT * FROM users --";

            // when
            boolean detected = attackDetectionService.detectSQLInjection(maliciousCode);

            // then
            assertThat(detected).isTrue();
        }
    }

    @Nested
    @DisplayName("XSS 공격 탐지 테스트")
    class XSSDetectionTest {

        @Test
        @DisplayName("정상 텍스트")
        void normalText() {
            // given
            String normalText = "This is a normal coupon description";

            // when
            boolean detected = attackDetectionService.detectXSS(normalText);

            // then
            assertThat(detected).isFalse();
        }

        @Test
        @DisplayName("XSS 패턴 탐지 - Script 태그")
        void detectXSSScript() {
            // given
            String maliciousText = "<script>alert('XSS')</script>";

            // when
            boolean detected = attackDetectionService.detectXSS(maliciousText);

            // then
            assertThat(detected).isTrue();
            verify(saveAttackLogPort).save(argThat(log ->
                log.getAttackType() == AttackType.XSS
            ));
        }

        @Test
        @DisplayName("XSS 패턴 탐지 - JavaScript URL")
        void detectXSSJavascript() {
            // given
            String maliciousText = "javascript:alert('XSS')";

            // when
            boolean detected = attackDetectionService.detectXSS(maliciousText);

            // then
            assertThat(detected).isTrue();
        }

        @Test
        @DisplayName("XSS 패턴 탐지 - Event Handler")
        void detectXSSEventHandler() {
            // given
            String maliciousText = "<img src=x onerror=alert('XSS')>";

            // when
            boolean detected = attackDetectionService.detectXSS(maliciousText);

            // then
            assertThat(detected).isTrue();
        }
    }

    @Nested
    @DisplayName("Brute Force 공격 탐지 테스트")
    class BruteForceDetectionTest {

        @Test
        @DisplayName("정상 로그인 시도")
        void normalLoginAttempt() {
            // given
            Long userId = 100L;
            when(valueOperations.get("login:attempts:" + userId)).thenReturn(2);

            // when
            boolean detected = attackDetectionService.detectBruteForce(userId, "login");

            // then
            assertThat(detected).isFalse();
        }

        @Test
        @DisplayName("Brute Force 탐지 - 과도한 시도")
        void detectBruteForce() {
            // given
            Long userId = 100L;
            when(valueOperations.get("login:attempts:" + userId)).thenReturn(10);

            // when
            boolean detected = attackDetectionService.detectBruteForce(userId, "login");

            // then
            assertThat(detected).isTrue();
            verify(saveAttackLogPort).save(argThat(log ->
                log.getAttackType() == AttackType.BRUTE_FORCE &&
                log.getThreatLevel() == ThreatLevel.HIGH
            ));
        }
    }

    @Nested
    @DisplayName("IP 기반 차단 테스트")
    class IPBlockingTest {

        @Test
        @DisplayName("정상 IP")
        void normalIP() {
            // given
            String ip = "192.168.1.100";
            when(valueOperations.get("blocked:ip:" + ip)).thenReturn(null);

            // when
            boolean blocked = attackDetectionService.isBlocked(ip);

            // then
            assertThat(blocked).isFalse();
        }

        @Test
        @DisplayName("차단된 IP")
        void blockedIP() {
            // given
            String ip = "192.168.1.100";
            when(valueOperations.get("blocked:ip:" + ip)).thenReturn(true);

            // when
            boolean blocked = attackDetectionService.isBlocked(ip);

            // then
            assertThat(blocked).isTrue();
        }

        @Test
        @DisplayName("IP 차단 추가")
        void blockIP() {
            // given
            String ip = "192.168.1.100";
            Long duration = 3600L; // 1시간

            // when
            attackDetectionService.blockIP(ip, duration);

            // then
            verify(valueOperations).set("blocked:ip:" + ip, true, duration, TimeUnit.SECONDS);
            verify(saveAttackLogPort).save(argThat(log ->
                log.getSourceIp().equals(ip) &&
                log.getDescription().contains("IP blocked")
            ));
        }
    }

    @Nested
    @DisplayName("위협 레벨 평가 테스트")
    class ThreatLevelAssessmentTest {

        @Test
        @DisplayName("낮은 위협 레벨")
        void lowThreatLevel() {
            // given
            AttackType attackType = AttackType.RATE_LIMIT_VIOLATION;
            int frequency = 1;

            // when
            ThreatLevel level = attackDetectionService.assessThreatLevel(attackType, frequency);

            // then
            assertThat(level).isEqualTo(ThreatLevel.LOW);
        }

        @Test
        @DisplayName("중간 위협 레벨")
        void mediumThreatLevel() {
            // given
            AttackType attackType = AttackType.XSS;
            int frequency = 3;

            // when
            ThreatLevel level = attackDetectionService.assessThreatLevel(attackType, frequency);

            // then
            assertThat(level).isEqualTo(ThreatLevel.MEDIUM);
        }

        @Test
        @DisplayName("높은 위협 레벨")
        void highThreatLevel() {
            // given
            AttackType attackType = AttackType.SQL_INJECTION;
            int frequency = 5;

            // when
            ThreatLevel level = attackDetectionService.assessThreatLevel(attackType, frequency);

            // then
            assertThat(level).isEqualTo(ThreatLevel.HIGH);
        }

        @Test
        @DisplayName("심각한 위협 레벨")
        void criticalThreatLevel() {
            // given
            AttackType attackType = AttackType.DATA_BREACH_ATTEMPT;
            int frequency = 10;

            // when
            ThreatLevel level = attackDetectionService.assessThreatLevel(attackType, frequency);

            // then
            assertThat(level).isEqualTo(ThreatLevel.CRITICAL);
        }
    }

    @Nested
    @DisplayName("종합 보안 검증 테스트")
    class ComprehensiveSecurityTest {

        @Test
        @DisplayName("다중 공격 패턴 동시 탐지")
        void multipleAttackPatterns() {
            // given
            String maliciousInput = "'; DROP TABLE coupons; <script>alert('XSS')</script>";

            // when
            boolean sqlInjection = attackDetectionService.detectSQLInjection(maliciousInput);
            boolean xss = attackDetectionService.detectXSS(maliciousInput);

            // then
            assertThat(sqlInjection).isTrue();
            assertThat(xss).isTrue();
            verify(saveAttackLogPort, times(2)).save(any(AttackLog.class));
        }

        @Test
        @DisplayName("보안 예외 발생")
        void securityException() {
            // given
            String criticalAttack = "'; DELETE FROM users WHERE 1=1; --";

            // when & then
            assertThatThrownBy(() ->
                attackDetectionService.validateAndThrow(criticalAttack)
            )
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Security threat detected");
        }
    }
}
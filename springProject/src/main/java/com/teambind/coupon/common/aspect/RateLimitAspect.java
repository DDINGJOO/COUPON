package com.teambind.coupon.common.aspect;

import com.teambind.coupon.adapter.out.redis.RateLimiterService;
import com.teambind.coupon.application.service.AttackDetectionService;
import com.teambind.coupon.common.annotation.RateLimit;
import com.teambind.coupon.common.exceptions.CustomException;
import com.teambind.coupon.common.exceptions.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * RateLimit 어노테이션 처리를 위한 AOP Aspect
 * 메서드 레벨에서 Rate Limiting 적용
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final AttackDetectionService attackDetectionService;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(rateLimit)")
    public Object handleRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // Rate limit 키 생성
        String rateLimitKey = generateRateLimitKey(joinPoint, rateLimit);

        log.debug("Rate limit 처리 시작 - method: {}, key: {}",
                joinPoint.getSignature().toShortString(), rateLimitKey);

        // 차단 확인
        if (attackDetectionService.isBlocked(rateLimitKey)) {
            log.warn("차단된 키 접근 시도 - key: {}", rateLimitKey);
            throw new CustomException(ErrorCode.FORBIDDEN, "접근이 차단되었습니다");
        }

        // Rate limit 확인
        boolean allowed = rateLimiterService.allowRequestMultiLevel(
                rateLimitKey,
                rateLimit.perMinute(),
                rateLimit.perHour(),
                rateLimit.perDay()
        );

        if (!allowed) {
            log.warn("Rate limit 초과 - method: {}, key: {}",
                    joinPoint.getSignature().toShortString(), rateLimitKey);

            // 실패 기록
            attackDetectionService.recordFailure(rateLimitKey, "method_rate_limit");

            // 반복적인 위반 시 차단
            int riskScore = attackDetectionService.calculateRiskScore(rateLimitKey);
            if (riskScore > 80) {
                attackDetectionService.blockUser(
                        rateLimitKey,
                        "RATE_LIMIT_ABUSE",
                        Duration.ofMinutes(rateLimit.blockDurationMinutes())
                );
            }

            throw new CustomException(ErrorCode.LOCK_TIMEOUT, rateLimit.message());
        }

        try {
            // 메서드 실행
            Object result = joinPoint.proceed();

            // 성공 시 실패 카운터 리셋
            attackDetectionService.recordSuccess(rateLimitKey, "method_rate_limit");

            return result;

        } catch (Exception e) {
            // 비즈니스 로직 실패는 rate limit 실패로 간주하지 않음
            throw e;
        }
    }

    /**
     * Rate limit 키 생성
     * SpEL 표현식 평가 또는 기본 키 생성
     */
    private String generateRateLimitKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (rateLimit.key().isEmpty()) {
            // 기본 키: 메서드명 + IP/사용자
            return generateDefaultKey(joinPoint);
        }

        // SpEL 표현식 평가
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 컨텍스트에 추가
        String[] paramNames = signature.getParameterNames();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // HttpServletRequest 추가
        HttpServletRequest request = getRequest();
        if (request != null) {
            context.setVariable("request", request);
            context.setVariable("ip", getClientIp(request));
        }

        try {
            Expression expression = parser.parseExpression(rateLimit.key());
            Object keyValue = expression.getValue(context);
            return "rate:" + keyValue;
        } catch (Exception e) {
            log.warn("Rate limit 키 생성 실패, 기본 키 사용 - error: {}", e.getMessage());
            return generateDefaultKey(joinPoint);
        }
    }

    /**
     * 기본 Rate limit 키 생성
     */
    private String generateDefaultKey(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        String identifier = getIdentifier();
        return String.format("rate:%s:%s", methodName, identifier);
    }

    /**
     * 식별자 추출
     */
    private String getIdentifier() {
        HttpServletRequest request = getRequest();
        if (request != null) {
            // 사용자 ID 확인
            String userId = request.getHeader("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return "user:" + userId;
            }

            // IP 주소 사용
            return "ip:" + getClientIp(request);
        }

        return "unknown";
    }

    /**
     * HttpServletRequest 가져오기
     */
    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest();
            }
        } catch (Exception e) {
            log.debug("Request 정보를 가져올 수 없습니다", e);
        }
        return null;
    }

    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }
}
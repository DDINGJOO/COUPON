package com.teambind.coupon.adapter.in.web.interceptor;

import com.teambind.coupon.adapter.out.redis.RateLimiterService;
import com.teambind.coupon.application.service.AttackDetectionService;
import com.teambind.coupon.common.exceptions.CustomException;
import com.teambind.coupon.common.exceptions.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Rate Limiting 인터셉터
 * API 요청에 대한 속도 제한 및 공격 탐지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final AttackDetectionService attackDetectionService;

    @Value("${coupon.rate-limit.per-minute:60}")
    private int limitPerMinute;

    @Value("${coupon.rate-limit.per-hour:600}")
    private int limitPerHour;

    @Value("${coupon.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!rateLimitEnabled) {
            return true;
        }

        String identifier = getIdentifier(request);
        String endpoint = request.getRequestURI();

        // 차단된 사용자 확인
        if (attackDetectionService.isBlocked(identifier)) {
            log.warn("차단된 사용자 접근 시도 - IP: {}, endpoint: {}", identifier, endpoint);
            sendErrorResponse(response, HttpStatus.FORBIDDEN, "ACCESS_BLOCKED", "접근이 차단되었습니다");
            return false;
        }

        // Rate limiting 확인
        boolean allowed = checkRateLimit(identifier, endpoint);

        if (!allowed) {
            log.warn("Rate limit 초과 - IP: {}, endpoint: {}", identifier, endpoint);

            // Rate limit 초과 기록
            attackDetectionService.recordFailure(identifier, "rate_limit");

            // Rate limit 정보 헤더에 추가
            addRateLimitHeaders(response, identifier);

            sendErrorResponse(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                    "요청 속도 제한을 초과했습니다. 잠시 후 다시 시도해주세요.");
            return false;
        }

        // 의심스러운 활동 점수 확인
        int riskScore = attackDetectionService.calculateRiskScore(identifier);
        if (riskScore > 70) {
            log.warn("높은 위험 점수 탐지 - IP: {}, score: {}", identifier, riskScore);

            // 추가 검증 또는 CAPTCHA 요구 가능
            response.addHeader("X-Risk-Score", String.valueOf(riskScore));
        }

        return true;
    }

    /**
     * Rate limit 확인
     */
    private boolean checkRateLimit(String identifier, String endpoint) {
        // 엔드포인트별 다른 제한 적용 가능
        if (endpoint.contains("/coupon/download")) {
            // 쿠폰 다운로드는 더 엄격한 제한
            return rateLimiterService.allowRequestMultiLevel(
                    identifier + ":download",
                    10,  // 분당 10회
                    50,  // 시간당 50회
                    200  // 일당 200회
            );
        } else if (endpoint.contains("/coupon/reserve")) {
            // 쿠폰 예약
            return rateLimiterService.allowRequestMultiLevel(
                    identifier + ":reserve",
                    20,  // 분당 20회
                    100, // 시간당 100회
                    500  // 일당 500회
            );
        } else {
            // 기본 제한
            return rateLimiterService.allowRequestMultiLevel(
                    identifier,
                    limitPerMinute,
                    limitPerHour,
                    0  // 일당 제한 없음
            );
        }
    }

    /**
     * 식별자 추출 (IP 또는 사용자 ID)
     */
    private String getIdentifier(HttpServletRequest request) {
        // 1. 인증된 사용자인 경우 사용자 ID 사용
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }

        // 2. IP 주소 사용
        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // 프록시 서버를 통한 요청 처리
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "X-Real-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 첫 번째 IP 주소 반환 (콤마로 구분된 경우)
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Rate limit 헤더 추가
     */
    private void addRateLimitHeaders(HttpServletResponse response, String identifier) {
        try {
            var info = rateLimiterService.getRateLimitInfo(identifier, limitPerMinute, 60);

            response.addHeader("X-RateLimit-Limit", String.valueOf(info.limit()));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(info.remaining()));
            response.addHeader("X-RateLimit-Reset", String.valueOf(info.resetTime() / 1000));
            response.addHeader("Retry-After", String.valueOf(info.getTimeUntilReset().getSeconds()));
        } catch (Exception e) {
            log.error("Rate limit 헤더 추가 오류", e);
        }
    }

    /**
     * 에러 응답 전송
     */
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status,
                                    String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");

        String json = String.format("""
                {
                    "timestamp": "%s",
                    "status": %d,
                    "code": "%s",
                    "message": "%s"
                }
                """,
                java.time.LocalDateTime.now(),
                status.value(),
                code,
                message
        );

        response.getWriter().write(json);
    }
}
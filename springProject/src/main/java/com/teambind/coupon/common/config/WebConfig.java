package com.teambind.coupon.common.config;

import com.teambind.coupon.adapter.in.web.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정
 * 인터셉터, CORS, 리소스 핸들러 등 설정
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate Limiting 인터셉터 등록
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**", "/coupon/**")  // API 경로에 적용
                .excludePathPatterns(
                        "/actuator/**",     // 모니터링 엔드포인트 제외
                        "/health/**",       // 헬스체크 제외
                        "/error/**",        // 에러 페이지 제외
                        "/swagger-ui/**",   // Swagger UI 제외
                        "/v3/api-docs/**"   // API 문서 제외
                );
    }
}
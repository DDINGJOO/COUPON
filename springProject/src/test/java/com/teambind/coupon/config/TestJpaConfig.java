package com.teambind.coupon.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 테스트용 JPA 설정
 */
@TestConfiguration
@EnableJpaAuditing
public class TestJpaConfig {
}
package com.teambind.coupon.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Configuration
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.teambind.coupon.adapter.out.persistence.repository")
public class JpaConfig {
}
package com.teambind.coupon.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * TestContainer 설정
 * 통합 테스트를 위한 컨테이너 환경 구성
 * Docker Compose를 사용하므로 비활성화
 */
// @TestConfiguration
// @Testcontainers
public class TestContainerConfig {

    // @Bean
    // @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("coupon_test_db")
                .withUsername("testuser")
                .withPassword("testpass")
                .withInitScript("db/init-test.sql")
                .withReuse(true);
    }

    // @Bean
    // @ServiceConnection
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--appendonly", "yes")
                .withReuse(true);
    }

    // @Bean
    // @ServiceConnection
    public KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                .withKraft()
                .withReuse(true);
    }
}
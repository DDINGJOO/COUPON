package com.teambind.coupon.e2e;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;

/**
 * E2E 테스트 베이스 클래스
 * Docker Compose를 사용한 실제 운영 환경과 동일한 테스트 환경 구성
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    protected RequestSpecification requestSpec;

    // Docker Compose 컨테이너 정의
    @Container
    private static final DockerComposeContainer<?> dockerCompose =
            new DockerComposeContainer<>(new File("../docker-compose.yml"))
                    .withExposedService("postgres", 5432,
                            Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(2)))
                    .withExposedService("redis", 6379,
                            Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(2)))
                    .withExposedService("kafka", 9093,
                            Wait.forHealthcheck().withStartupTimeout(Duration.ofMinutes(3)))
                    .withLocalCompose(true);  // 로컬 docker-compose 사용

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // PostgreSQL 설정
        registry.add("spring.datasource.url", () ->
                String.format("jdbc:postgresql://localhost:%d/coupon_db",
                        dockerCompose.getServicePort("postgres", 5432)));
        registry.add("spring.datasource.username", () -> "coupon_user");
        registry.add("spring.datasource.password", () -> "coupon_pass_2024");

        // Redis 설정
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () ->
                dockerCompose.getServicePort("redis", 6379));

        // Kafka 설정
        registry.add("spring.kafka.bootstrap-servers", () ->
                String.format("localhost:%d",
                        dockerCompose.getServicePort("kafka", 9093)));
    }

    @BeforeAll
    void setupRestAssured() {
        RestAssured.port = port;
        requestSpec = new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setContentType(MediaType.APPLICATION_JSON_VALUE)
                .setAccept(MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @BeforeEach
    void setUp() {
        RestAssured.reset();
        RestAssured.port = port;
    }

    /**
     * 테스트 데이터 초기화 (하위 클래스에서 구현)
     */
    protected abstract void initializeTestData();

    /**
     * 테스트 데이터 정리 (하위 클래스에서 구현)
     */
    protected abstract void cleanupTestData();

    /**
     * 응답 시간 검증
     */
    protected void assertResponseTime(long actualTime, long maxTime) {
        if (actualTime > maxTime) {
            throw new AssertionError(
                    String.format("Response time %dms exceeds maximum %dms", actualTime, maxTime)
            );
        }
    }

    /**
     * 테스트용 JWT 토큰 생성 (필요시 사용)
     */
    protected String generateTestToken(Long userId) {
        // 테스트용 토큰 생성 로직
        return "Bearer test-token-" + userId;
    }
}
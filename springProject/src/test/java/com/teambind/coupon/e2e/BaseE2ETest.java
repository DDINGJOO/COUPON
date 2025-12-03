package com.teambind.coupon.e2e;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * E2E 테스트 베이스 클래스
 * Docker Compose를 사용한 실제 운영 환경과 동일한 테스트 환경 구성
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    // 이미 실행 중인 Docker 컨테이너 사용
    "spring.datasource.url=jdbc:postgresql://localhost:25432/coupon_db",
    "spring.datasource.username=coupon_user",
    "spring.datasource.password=coupon_pass_2024",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=26379",
    "spring.kafka.bootstrap-servers=localhost:39092"
})
public abstract class BaseE2ETest {

    @LocalServerPort
    protected int port;

    protected RequestSpecification requestSpec;

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
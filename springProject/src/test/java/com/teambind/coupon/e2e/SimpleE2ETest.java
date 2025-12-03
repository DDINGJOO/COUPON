package com.teambind.coupon.e2e;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 간단한 E2E 테스트 - Docker Compose 환경 사용
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SimpleE2ETest {

    @LocalServerPort
    private int port;

    @BeforeAll
    void setup() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/coupons";
    }

    @Test
    @DisplayName("쿠폰 조회 API 호출 테스트")
    void testGetUserCoupons() {
        Response response = given()
                .queryParam("limit", 10)
            .when()
                .get("/users/100")
            .then()
                .statusCode(200)
                .body("data", notNullValue())
                .body("hasNext", notNullValue())
                .extract().response();

        System.out.println("Response: " + response.asString());
    }

    @Test
    @DisplayName("필터링된 쿠폰 조회 테스트")
    void testGetFilteredCoupons() {
        given()
                .queryParam("status", "ISSUED")
                .queryParam("limit", 5)
            .when()
                .get("/users/100")
            .then()
                .statusCode(200)
                .body("data", notNullValue());
    }
}
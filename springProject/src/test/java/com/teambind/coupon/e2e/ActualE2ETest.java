package com.teambind.coupon.e2e;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 실제 실행 가능한 E2E 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:25432/coupon_db",
    "spring.datasource.username=coupon_user",
    "spring.datasource.password=coupon_pass_2024",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=26379",
    "spring.kafka.bootstrap-servers=localhost:39092",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class ActualE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private CouponPolicyRepository policyRepository;

    @Autowired
    private CouponIssueRepository issueRepository;

    private Long testUserId = 100L;
    private CouponPolicyEntity testPolicy;

    @BeforeAll
    void setup() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/coupons";
    }

    @BeforeEach
    void prepareTestData() {
        // 테스트 데이터 초기화
        issueRepository.deleteAll();
        policyRepository.deleteAll();

        // 테스트 정책 생성
        testPolicy = createTestPolicy();

        // 테스트 쿠폰 발급
        createTestCoupons();
    }

    @AfterEach
    void cleanup() {
        issueRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자 쿠폰 조회 - 정상 동작")
    void getUserCoupons_Success() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("limit", 10)
        .when()
            .get("/users/{userId}", testUserId)
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data", notNullValue())
            .body("data.size()", greaterThan(0))
            .body("hasNext", notNullValue());
    }

    @Test
    @DisplayName("쿠폰 조회 - 상태 필터링")
    void getUserCoupons_WithStatusFilter() {
        given()
            .contentType(ContentType.JSON)
            .queryParam("status", "ISSUED")
            .queryParam("limit", 5)
        .when()
            .get("/users/{userId}", testUserId)
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data.size()", lessThanOrEqualTo(5));
    }

    @Test
    @DisplayName("쿠폰 조회 - 페이지네이션")
    void getUserCoupons_Pagination() {
        // 첫 페이지 조회
        var firstPage = given()
            .contentType(ContentType.JSON)
            .queryParam("limit", 2)
        .when()
            .get("/users/{userId}", testUserId)
        .then()
            .statusCode(200)
            .extract().response();

        // 다음 페이지 존재 확인
        boolean hasNext = firstPage.jsonPath().getBoolean("hasNext");
        if (hasNext) {
            Long lastId = firstPage.jsonPath().getLong("data[-1].couponIssueId");

            // 두 번째 페이지 조회
            given()
                .contentType(ContentType.JSON)
                .queryParam("cursor", lastId)
                .queryParam("limit", 2)
            .when()
                .get("/users/{userId}", testUserId)
            .then()
                .statusCode(200)
                .body("data", notNullValue());
        }
    }

    @Test
    @DisplayName("만료 임박 쿠폰 조회")
    void getExpiringCoupons() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/users/{userId}/expiring", testUserId)
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("data", notNullValue());
    }

    @Test
    @DisplayName("쿠폰 조회 - 잘못된 사용자 ID")
    void getUserCoupons_InvalidUserId() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/users/{userId}", -1)
        .then()
            .statusCode(400);
    }

    // 테스트 데이터 생성 메서드
    private CouponPolicyEntity createTestPolicy() {
        long snowflakeId = System.currentTimeMillis() * 1000;

        CouponPolicyEntity.ItemApplicableRuleJson rule =
            CouponPolicyEntity.ItemApplicableRuleJson.builder()
                .allItemsApplicable(false)
                .applicableItemIds(List.of(1L, 2L, 3L))
                .build();

        CouponPolicyEntity policy = CouponPolicyEntity.builder()
            .id(snowflakeId)
            .couponName("E2E 테스트 쿠폰")
            .couponCode("E2E_TEST_001")
            .description("E2E 테스트용")
            .discountType(DiscountType.PERCENTAGE)
            .discountValue(BigDecimal.valueOf(10))
            .minimumOrderAmount(BigDecimal.valueOf(10000))
            .maxDiscountAmount(BigDecimal.valueOf(5000))
            .applicableRule(rule)
            .distributionType(DistributionType.CODE)
            .validFrom(LocalDateTime.now().minusDays(1))
            .validUntil(LocalDateTime.now().plusDays(30))
            .maxIssueCount(100)
            .maxUsagePerUser(3)
            .isActive(true)
            .createdBy(1L)
            .build();

        return policyRepository.save(policy);
    }

    private void createTestCoupons() {
        // ISSUED 상태 쿠폰 3개
        for (int i = 0; i < 3; i++) {
            CouponIssueEntity issue = CouponIssueEntity.builder()
                .userId(testUserId)
                .policyId(testPolicy.getId())
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(i))
                .expiresAt(LocalDateTime.now().plusDays(30 - i))
                .build();
            issueRepository.save(issue);
        }

        // USED 상태 쿠폰 1개
        CouponIssueEntity usedCoupon = CouponIssueEntity.builder()
            .userId(testUserId)
            .policyId(testPolicy.getId())
            .status(CouponStatus.USED)
            .issuedAt(LocalDateTime.now().minusDays(10))
            .expiresAt(LocalDateTime.now().plusDays(20))
            .usedAt(LocalDateTime.now().minusDays(5))
            .actualDiscountAmount(BigDecimal.valueOf(1000))
            .build();
        issueRepository.save(usedCoupon);

        // EXPIRED 상태 쿠폰 1개
        CouponIssueEntity expiredCoupon = CouponIssueEntity.builder()
            .userId(testUserId)
            .policyId(testPolicy.getId())
            .status(CouponStatus.EXPIRED)
            .issuedAt(LocalDateTime.now().minusDays(40))
            .expiresAt(LocalDateTime.now().minusDays(1))
            .build();
        issueRepository.save(expiredCoupon);
    }
}
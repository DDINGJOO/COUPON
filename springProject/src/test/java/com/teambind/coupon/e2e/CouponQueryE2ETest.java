package com.teambind.coupon.e2e;

import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.domain.model.CouponStatus;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 쿠폰 조회 API E2E 테스트
 * Docker Compose 환경에서 실제 인프라를 사용한 통합 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("쿠폰 조회 API E2E 테스트")
public class CouponQueryE2ETest extends BaseE2ETest {

    @Autowired
    private TestDataInitializer testDataInitializer;

    private static final Long TEST_USER_ID = 100L;
    private static final List<Long> TEST_USER_IDS = Arrays.asList(100L, 101L, 102L, 103L, 104L);

    private List<CouponPolicyEntity> testPolicies;

    @Override
    protected void initializeTestData() {
        // 테스트 데이터 초기화
        testPolicies = testDataInitializer.createCouponPolicies(5);

        // 각 유저별로 다양한 상태의 쿠폰 생성
        testDataInitializer.createCouponIssues(TEST_USER_IDS, testPolicies, 20);

        // 특정 상태 쿠폰 추가 생성
        testDataInitializer.createCouponIssuesWithStatus(
                TEST_USER_ID, testPolicies.get(0), CouponStatus.ISSUED, 10
        );
        testDataInitializer.createCouponIssuesWithStatus(
                TEST_USER_ID, testPolicies.get(1), CouponStatus.USED, 5
        );
        testDataInitializer.createExpiringCoupons(
                TEST_USER_ID, testPolicies.get(2), 3, 5
        );
    }

    @Override
    protected void cleanupTestData() {
        testDataInitializer.cleanupAllTestData();
    }

    @BeforeEach
    void setUpTestData() {
        initializeTestData();
    }

    @AfterEach
    void cleanUp() {
        cleanupTestData();
    }

    @Nested
    @DisplayName("커서 기반 페이지네이션 테스트")
    class CursorPaginationTest {

        @Test
        @Order(1)
        @DisplayName("첫 페이지 조회")
        void testFirstPage() {
            long startTime = System.currentTimeMillis();

            Response response = given()
                    .spec(requestSpec)
                    .queryParam("limit", 10)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("data", notNullValue())
                    .body("data.size()", lessThanOrEqualTo(10))
                    .body("hasNext", notNullValue())
                    .body("nextCursor", notNullValue())
                    .extract().response();

            long responseTime = System.currentTimeMillis() - startTime;
            assertResponseTime(responseTime, 500);

            // 첫 페이지 데이터 검증
            List<Integer> couponIds = response.jsonPath().getList("data.couponIssueId");
            assertFalse(couponIds.isEmpty(), "첫 페이지에 데이터가 있어야 합니다");

            System.out.println("First page response time: " + responseTime + "ms");
            System.out.println("First page coupon count: " + couponIds.size());
        }

        @Test
        @Order(2)
        @DisplayName("커서를 사용한 다음 페이지 조회")
        void testNextPageWithCursor() {
            // 첫 페이지 조회
            Response firstPage = given()
                    .spec(requestSpec)
                    .queryParam("limit", 5)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .extract().response();

            Long cursor = firstPage.jsonPath().getLong("nextCursor");
            assertNotNull(cursor, "첫 페이지에 nextCursor가 있어야 합니다");

            // 다음 페이지 조회
            long startTime = System.currentTimeMillis();

            Response secondPage = given()
                    .spec(requestSpec)
                    .queryParam("cursor", cursor)
                    .queryParam("limit", 5)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("data", notNullValue())
                    .extract().response();

            long responseTime = System.currentTimeMillis() - startTime;
            assertResponseTime(responseTime, 500);

            // 페이지 간 데이터 중복 확인
            List<Long> firstPageIds = firstPage.jsonPath().getList("data.couponIssueId");
            List<Long> secondPageIds = secondPage.jsonPath().getList("data.couponIssueId");

            assertTrue(firstPageIds.stream().noneMatch(secondPageIds::contains),
                    "페이지 간 중복 데이터가 없어야 합니다");
        }

        @Test
        @Order(3)
        @DisplayName("마지막 페이지까지 순회")
        void testTraverseAllPages() {
            Long cursor = null;
            int pageCount = 0;
            int totalItems = 0;

            while (pageCount < 100) { // 무한 루프 방지
                Response response = given()
                        .spec(requestSpec)
                        .queryParam("limit", 10)
                        .queryParam("cursor", cursor != null ? cursor : "")
                    .when()
                        .get("/api/coupons/users/{userId}", TEST_USER_ID)
                    .then()
                        .statusCode(200)
                        .extract().response();

                List<?> data = response.jsonPath().getList("data");
                totalItems += data.size();
                pageCount++;

                Boolean hasNext = response.jsonPath().getBoolean("hasNext");
                if (!hasNext) {
                    break;
                }

                cursor = response.jsonPath().getLong("nextCursor");
            }

            System.out.println("Total pages: " + pageCount);
            System.out.println("Total items: " + totalItems);

            assertTrue(pageCount > 0, "최소 한 페이지는 있어야 합니다");
            assertTrue(totalItems > 0, "최소 한 개의 쿠폰은 있어야 합니다");
        }
    }

    @Nested
    @DisplayName("필터링 테스트")
    class FilteringTest {

        @Test
        @Order(4)
        @DisplayName("상태별 필터링 - AVAILABLE")
        void testFilterByStatusAvailable() {
            Response response = given()
                    .spec(requestSpec)
                    .queryParam("status", "AVAILABLE")
                    .queryParam("limit", 20)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("data", notNullValue())
                    .extract().response();

            List<String> statuses = response.jsonPath().getList("data.status");
            List<Boolean> availabilities = response.jsonPath().getList("data.isAvailable");

            // AVAILABLE 상태는 ISSUED이면서 만료되지 않은 쿠폰
            assertTrue(statuses.stream().allMatch("ISSUED"::equals),
                    "AVAILABLE 필터는 ISSUED 상태 쿠폰만 반환해야 합니다");
            assertTrue(availabilities.stream().allMatch(Boolean::booleanValue),
                    "AVAILABLE 필터는 사용 가능한 쿠폰만 반환해야 합니다");
        }

        @Test
        @Order(5)
        @DisplayName("상태별 필터링 - USED")
        void testFilterByStatusUsed() {
            Response response = given()
                    .spec(requestSpec)
                    .queryParam("status", "USED")
                    .queryParam("limit", 20)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("data", notNullValue())
                    .extract().response();

            List<String> statuses = response.jsonPath().getList("data.status");
            if (!statuses.isEmpty()) {
                assertTrue(statuses.stream().allMatch("USED"::equals),
                        "USED 필터는 사용 완료 쿠폰만 반환해야 합니다");
            }
        }

        @Test
        @Order(6)
        @DisplayName("복수 상품ID 필터링")
        void testFilterByProductIds() {
            // 테스트 정책의 상품 ID 확인
            // 테스트 정책의 적용 가능 상품 ID
            Long[] productIds = new Long[]{1L, 2L, 3L};

            Response response = given()
                    .spec(requestSpec)
                    .queryParam("productIds", productIds[0])
                    .queryParam("limit", 20)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("data", notNullValue())
                    .extract().response();

            // 응답 데이터가 있으면 상품 ID 확인
            List<?> data = response.jsonPath().getList("data");
            if (!data.isEmpty()) {
                System.out.println("Filtered by product ID: " + productIds[0]);
                System.out.println("Found coupons: " + data.size());
            }
        }

        @Test
        @Order(7)
        @DisplayName("복합 필터링 - 상태 + 상품ID")
        void testComplexFiltering() {
            // 테스트 정책의 적용 가능 상품 ID
            Long[] productIds = new Long[]{1L, 2L, 3L};

            Response response = given()
                    .spec(requestSpec)
                    .queryParam("status", "AVAILABLE")
                    .queryParam("productIds", productIds[0])
                    .queryParam("limit", 10)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .extract().response();

            List<String> statuses = response.jsonPath().getList("data.status");
            List<Boolean> availabilities = response.jsonPath().getList("data.isAvailable");

            if (!statuses.isEmpty()) {
                assertTrue(statuses.stream().allMatch("ISSUED"::equals));
                assertTrue(availabilities.stream().allMatch(Boolean::booleanValue));
            }
        }
    }

    @Nested
    @DisplayName("만료 임박 쿠폰 조회 테스트")
    class ExpiringCouponsTest {

        @Test
        @Order(8)
        @DisplayName("7일 이내 만료 쿠폰 조회")
        void testExpiringCoupons() {
            Response response = given()
                    .spec(requestSpec)
                    .queryParam("days", 7)
                    .queryParam("limit", 10)
                .when()
                    .get("/api/coupons/users/{userId}/expiring", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("data", notNullValue())
                    .extract().response();

            List<Integer> remainingDays = response.jsonPath().getList("data.remainingDays");
            if (!remainingDays.isEmpty()) {
                assertTrue(remainingDays.stream().allMatch(days -> days != null && days <= 7),
                        "7일 이내 만료 쿠폰만 반환해야 합니다");
            }
        }
    }

    @Nested
    @DisplayName("통계 조회 테스트")
    class StatisticsTest {

        @Test
        @Order(9)
        @DisplayName("유저 쿠폰 통계 조회")
        void testUserCouponStatistics() {
            Response response = given()
                    .spec(requestSpec)
                .when()
                    .get("/api/coupons/users/{userId}/statistics", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("totalCoupons", notNullValue())
                    .body("availableCoupons", notNullValue())
                    .body("usedCoupons", notNullValue())
                    .body("expiredCoupons", notNullValue())
                    .body("expiringCoupons", notNullValue())
                    .extract().response();

            Long total = response.jsonPath().getLong("totalCoupons");
            Long available = response.jsonPath().getLong("availableCoupons");
            Long used = response.jsonPath().getLong("usedCoupons");
            Long expired = response.jsonPath().getLong("expiredCoupons");

            assertTrue(total >= 0, "전체 쿠폰 수는 0 이상이어야 합니다");
            assertTrue(available >= 0, "사용 가능 쿠폰 수는 0 이상이어야 합니다");
            assertTrue(used >= 0, "사용 완료 쿠폰 수는 0 이상이어야 합니다");
            assertTrue(expired >= 0, "만료 쿠폰 수는 0 이상이어야 합니다");

            System.out.println(String.format(
                    "Statistics - Total: %d, Available: %d, Used: %d, Expired: %d",
                    total, available, used, expired
            ));
        }
    }

    @Nested
    @DisplayName("동시성 및 성능 테스트")
    class ConcurrencyAndPerformanceTest {

        @Test
        @Order(10)
        @DisplayName("다중 사용자 동시 조회")
        void testConcurrentRequests() throws Exception {
            int concurrentUsers = 10;

            List<CompletableFuture<Response>> futures = IntStream.range(0, concurrentUsers)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() ->
                            given()
                                    .spec(requestSpec)
                                    .queryParam("limit", 5)
                                .when()
                                    .get("/api/coupons/users/{userId}", TEST_USER_IDS.get(i % TEST_USER_IDS.size()))
                                .then()
                                    .extract().response()
                    ))
                    .toList();

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            allFutures.get(10, TimeUnit.SECONDS);

            // 모든 응답 검증
            for (CompletableFuture<Response> future : futures) {
                Response response = future.get();
                assertEquals(200, response.getStatusCode(),
                        "모든 동시 요청이 성공해야 합니다");
            }
        }

        @Test
        @Order(11)
        @DisplayName("대용량 데이터 조회 성능")
        void testLargeDatasetPerformance() {
            // 대량 데이터 생성
            testDataInitializer.createCouponIssues(
                    List.of(999L), testPolicies, 100
            );

            long startTime = System.currentTimeMillis();

            Response response = given()
                    .spec(requestSpec)
                    .queryParam("limit", 50)
                .when()
                    .get("/api/coupons/users/{userId}", 999L)
                .then()
                    .statusCode(200)
                    .extract().response();

            long responseTime = System.currentTimeMillis() - startTime;

            assertResponseTime(responseTime, 1000); // 대용량 데이터는 1초 이내

            System.out.println("Large dataset response time: " + responseTime + "ms");
            System.out.println("Items returned: " + response.jsonPath().getList("data").size());
        }

        @Test
        @Order(12)
        @DisplayName("복잡한 필터링 성능")
        void testComplexFilteringPerformance() {
            long startTime = System.currentTimeMillis();

            Response response = given()
                    .spec(requestSpec)
                    .queryParam("status", "AVAILABLE")
                    .queryParam("productIds", "1,2,3,4,5")
                    .queryParam("limit", 20)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .extract().response();

            long responseTime = System.currentTimeMillis() - startTime;

            assertResponseTime(responseTime, 700); // 복잡한 필터링도 700ms 이내

            System.out.println("Complex filtering response time: " + responseTime + "ms");
        }
    }

    @Nested
    @DisplayName("엣지 케이스 테스트")
    class EdgeCaseTest {

        @Test
        @Order(13)
        @DisplayName("쿠폰이 없는 사용자 조회")
        void testUserWithNoCoupons() {
            Response response = given()
                    .spec(requestSpec)
                .when()
                    .get("/api/coupons/users/{userId}", 99999L)
                .then()
                    .statusCode(200)
                    .body("data", empty())
                    .body("hasNext", equalTo(false))
                    .body("nextCursor", nullValue())
                    .extract().response();

            assertEquals(0, response.jsonPath().getInt("count"));
        }

        @Test
        @Order(14)
        @DisplayName("잘못된 파라미터 처리")
        void testInvalidParameters() {
            // 음수 limit
            given()
                    .spec(requestSpec)
                    .queryParam("limit", -1)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(400);

            // 너무 큰 limit
            given()
                    .spec(requestSpec)
                    .queryParam("limit", 1000)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .body("data.size()", lessThanOrEqualTo(100)); // 최대 100개로 제한
        }

        @Test
        @Order(15)
        @DisplayName("존재하지 않는 커서 처리")
        void testInvalidCursor() {
            Response response = given()
                    .spec(requestSpec)
                    .queryParam("cursor", 999999999L)
                    .queryParam("limit", 10)
                .when()
                    .get("/api/coupons/users/{userId}", TEST_USER_ID)
                .then()
                    .statusCode(200)
                    .extract().response();

            // 존재하지 않는 커서는 빈 결과 반환
            assertTrue(response.jsonPath().getList("data").isEmpty());
        }
    }
}
package com.teambind.coupon.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CouponPolicy 도메인 모델 단위 테스트
 */
@DisplayName("CouponPolicy 도메인 모델 테스트")
class CouponPolicyTest {

    private CouponPolicy policy;
    private DiscountPolicy discountPolicy;

    @BeforeEach
    void setUp() {
        discountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .minOrderAmount(new BigDecimal("10000"))
                .maxDiscountAmount(new BigDecimal("5000"))
                .build();

        policy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .description("테스트용 쿠폰")
                .discountPolicy(discountPolicy)
                .applicableRule(ItemApplicableRule.ALL)
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(100)
                .maxUsagePerUser(2)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy(1L)
                .build();

        policy.setCurrentIssueCount(new AtomicInteger(0));
    }

    @Nested
    @DisplayName("쿠폰 발급 가능 여부 확인")
    class IssuableCheck {

        @Test
        @DisplayName("정상적인 쿠폰은 발급 가능")
        void validCouponIsIssuable() {
            assertThat(policy.isIssuable()).isTrue();
        }

        @Test
        @DisplayName("비활성화된 쿠폰은 발급 불가")
        void inactiveCouponNotIssuable() {
            // given
            policy.deactivate();

            // then
            assertThat(policy.isIssuable()).isFalse();
            assertThat(policy.isActive()).isFalse();
        }

        @Test
        @DisplayName("유효기간 이전 쿠폰은 발급 불가")
        void notStartedCouponNotIssuable() {
            // given
            CouponPolicy futurePolicy = CouponPolicy.builder()
                    .validFrom(LocalDateTime.now().plusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .isActive(true)
                    .build();
            futurePolicy.setCurrentIssueCount(new AtomicInteger(0));

            // then
            assertThat(futurePolicy.isIssuable()).isFalse();
            assertThat(futurePolicy.isNotStarted()).isTrue();
        }

        @Test
        @DisplayName("만료된 쿠폰은 발급 불가")
        void expiredCouponNotIssuable() {
            // given
            CouponPolicy expiredPolicy = CouponPolicy.builder()
                    .validFrom(LocalDateTime.now().minusDays(30))
                    .validUntil(LocalDateTime.now().minusDays(1))
                    .isActive(true)
                    .build();
            expiredPolicy.setCurrentIssueCount(new AtomicInteger(0));

            // then
            assertThat(expiredPolicy.isIssuable()).isFalse();
            assertThat(expiredPolicy.isExpired()).isTrue();
        }

        @Test
        @DisplayName("재고가 소진된 쿠폰은 발급 불가")
        void soldOutCouponNotIssuable() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(100)); // maxIssueCount와 동일

            // then
            assertThat(policy.isIssuable()).isFalse();
        }

        @ParameterizedTest
        @MethodSource("provideIssuableTestCases")
        @DisplayName("다양한 조건의 발급 가능 여부 확인")
        void variousIssuableConditions(LocalDateTime validFrom, LocalDateTime validUntil,
                                      boolean isActive, Integer maxIssueCount,
                                      int currentIssueCount, boolean expectedIssuable) {
            // given
            CouponPolicy testPolicy = CouponPolicy.builder()
                    .validFrom(validFrom)
                    .validUntil(validUntil)
                    .isActive(isActive)
                    .maxIssueCount(maxIssueCount)
                    .build();
            testPolicy.setCurrentIssueCount(new AtomicInteger(currentIssueCount));

            // then
            assertThat(testPolicy.isIssuable()).isEqualTo(expectedIssuable);
        }

        static Stream<Arguments> provideIssuableTestCases() {
            LocalDateTime now = LocalDateTime.now();
            return Stream.of(
                    // validFrom, validUntil, isActive, maxIssueCount, currentIssueCount, expectedIssuable
                    Arguments.of(now.minusDays(1), now.plusDays(1), true, null, 0, true), // 무제한 재고
                    Arguments.of(now.minusDays(1), now.plusDays(1), true, 100, 50, true), // 재고 있음
                    Arguments.of(now.minusDays(1), now.plusDays(1), true, 100, 100, false), // 재고 소진
                    Arguments.of(now.plusDays(1), now.plusDays(2), true, 100, 0, false), // 미시작
                    Arguments.of(now.minusDays(2), now.minusDays(1), true, 100, 0, false), // 만료
                    Arguments.of(now.minusDays(1), now.plusDays(1), false, 100, 0, false) // 비활성
            );
        }
    }

    @Nested
    @DisplayName("쿠폰 발급 처리")
    class IssueProcess {

        @Test
        @DisplayName("정상 발급 처리")
        void successfulIssue() {
            // when
            boolean result = policy.tryIssue();

            // then
            assertThat(result).isTrue();
            assertThat(policy.getCurrentIssueCount().get()).isEqualTo(1);
        }

        @Test
        @DisplayName("재고 소진시 발급 실패")
        void failIssueWhenSoldOut() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(100));

            // when
            boolean result = policy.tryIssue();

            // then
            assertThat(result).isFalse();
            assertThat(policy.getCurrentIssueCount().get()).isEqualTo(100);
        }

        @Test
        @DisplayName("무제한 재고 쿠폰 발급")
        void issueUnlimitedCoupon() {
            // given
            CouponPolicy unlimitedPolicy = CouponPolicy.builder()
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(30))
                    .maxIssueCount(null) // 무제한
                    .isActive(true)
                    .build();
            unlimitedPolicy.setCurrentIssueCount(new AtomicInteger(0));

            // when & then
            for (int i = 0; i < 1000; i++) {
                assertThat(unlimitedPolicy.tryIssue()).isTrue();
            }
            // 무제한이므로 발급 수량 추적하지 않음
        }

        @Test
        @DisplayName("동시 발급 시 원자성 보장")
        void atomicIssue() throws InterruptedException {
            // given
            int threadCount = 100;
            policy.setCurrentIssueCount(new AtomicInteger(50)); // 50개 이미 발급

            // when
            Thread[] threads = new Thread[threadCount];
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    if (policy.tryIssue()) {
                        successCount.incrementAndGet();
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // then
            assertThat(successCount.get()).isEqualTo(50); // 100개 한도에서 50개만 추가 발급
            assertThat(policy.getCurrentIssueCount().get()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("쿠폰 정책 상태 관리")
    class StateManagement {

        @Test
        @DisplayName("쿠폰 활성화")
        void activateCoupon() {
            // given
            policy.deactivate();
            assertThat(policy.isActive()).isFalse();

            // when
            policy.activate();

            // then
            assertThat(policy.isActive()).isTrue();
            assertThat(policy.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("쿠폰 비활성화")
        void deactivateCoupon() {
            // given
            assertThat(policy.isActive()).isTrue();

            // when
            policy.deactivate();

            // then
            assertThat(policy.isActive()).isFalse();
            assertThat(policy.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("사용자 제한 확인")
        void checkUserLimit() {
            // given
            CouponPolicy noLimitPolicy = CouponPolicy.builder()
                    .maxUsagePerUser(null)
                    .build();

            CouponPolicy limitedPolicy = CouponPolicy.builder()
                    .maxUsagePerUser(3)
                    .build();

            // then
            assertThat(noLimitPolicy.hasUserLimit()).isFalse();
            assertThat(limitedPolicy.hasUserLimit()).isTrue();
        }
    }

    @Nested
    @DisplayName("유효기간 검증")
    class ValidityPeriod {

        @Test
        @DisplayName("특정 시점 기준 발급 가능 여부")
        void issuableAtSpecificTime() {
            // given
            LocalDateTime pastTime = LocalDateTime.now().minusDays(2);
            LocalDateTime futureTime = LocalDateTime.now().plusDays(31);
            LocalDateTime validTime = LocalDateTime.now();

            // then
            assertThat(policy.isIssuable(pastTime)).isFalse(); // 정책 생성 이전
            assertThat(policy.isIssuable(futureTime)).isFalse(); // 만료 이후
            assertThat(policy.isIssuable(validTime)).isTrue(); // 유효 기간 내
        }

        @Test
        @DisplayName("유효기간 상태 확인")
        void checkValidityStatus() {
            // given
            CouponPolicy notStarted = CouponPolicy.builder()
                    .validFrom(LocalDateTime.now().plusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(10))
                    .build();

            CouponPolicy expired = CouponPolicy.builder()
                    .validFrom(LocalDateTime.now().minusDays(10))
                    .validUntil(LocalDateTime.now().minusDays(1))
                    .build();

            CouponPolicy valid = CouponPolicy.builder()
                    .validFrom(LocalDateTime.now().minusDays(1))
                    .validUntil(LocalDateTime.now().plusDays(1))
                    .build();

            // then
            assertThat(notStarted.isNotStarted()).isTrue();
            assertThat(notStarted.isExpired()).isFalse();

            assertThat(expired.isNotStarted()).isFalse();
            assertThat(expired.isExpired()).isTrue();

            assertThat(valid.isNotStarted()).isFalse();
            assertThat(valid.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("분산 타입별 특성")
    class DistributionTypeSpecific {

        @Test
        @DisplayName("CODE 타입은 쿠폰 코드 필수")
        void codeTypeRequiresCouponCode() {
            // given
            CouponPolicy codePolicy = CouponPolicy.builder()
                    .distributionType(DistributionType.CODE)
                    .couponCode("TESTCODE")
                    .build();

            // then
            assertThat(codePolicy.getCouponCode()).isNotNull();
            assertThat(codePolicy.getDistributionType()).isEqualTo(DistributionType.CODE);
        }

        @Test
        @DisplayName("DIRECT 타입은 관리자 발급용")
        void directTypeForAdmin() {
            // given
            CouponPolicy directPolicy = CouponPolicy.builder()
                    .distributionType(DistributionType.DIRECT)
                    .couponCode(null) // 코드 불필요
                    .build();

            // then
            assertThat(directPolicy.getCouponCode()).isNull();
            assertThat(directPolicy.getDistributionType()).isEqualTo(DistributionType.DIRECT);
        }

        @Test
        @DisplayName("EVENT 타입 특성")
        void eventTypeCharacteristics() {
            // given
            CouponPolicy eventPolicy = CouponPolicy.builder()
                    .distributionType(DistributionType.EVENT)
                    .build();

            // then
            assertThat(eventPolicy.getDistributionType()).isEqualTo(DistributionType.EVENT);
        }
    }

    @Nested
    @DisplayName("쿠폰 정책 남은 발급 수량 수정")
    class UpdateRemainingQuantity {

        @Test
        @DisplayName("정상적인 남은 발급 수량 수정")
        void updateRemainingQuantitySuccess() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(30)); // 30개 이미 발급
            LocalDateTime beforeUpdate = policy.getUpdatedAt();

            // when
            policy.updateRemainingQuantity(150);

            // then
            assertThat(policy.getMaxIssueCount()).isEqualTo(150);
            assertThat(policy.getUpdatedAt()).isAfter(beforeUpdate);
        }

        @Test
        @DisplayName("무제한으로 변경")
        void updateToUnlimited() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(30));

            // when
            policy.updateRemainingQuantity(null);

            // then
            assertThat(policy.getMaxIssueCount()).isNull();
            assertThat(policy.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("현재 발급 수량보다 적게 설정 시 예외")
        void failWhenNewQuantityLessThanCurrentIssued() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(50));

            // when & then
            assertThatThrownBy(() -> policy.updateRemainingQuantity(30))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("새로운 발급 수량(30)은 현재 발급된 수량(50)보다 적을 수 없습니다");
        }

        @Test
        @DisplayName("음수 설정 시 예외")
        void failWhenNegativeQuantity() {
            // when & then
            assertThatThrownBy(() -> policy.updateRemainingQuantity(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("발급 수량은 0 이상이어야 합니다");
        }

        @Test
        @DisplayName("만료된 쿠폰 수정 시 예외")
        void failWhenCouponExpired() {
            // given
            CouponPolicy expiredPolicy = CouponPolicy.builder()
                    .validFrom(LocalDateTime.now().minusDays(10))
                    .validUntil(LocalDateTime.now().minusDays(1))
                    .isActive(true)
                    .build();
            expiredPolicy.setCurrentIssueCount(new AtomicInteger(0));

            // when & then
            assertThatThrownBy(() -> expiredPolicy.updateRemainingQuantity(100))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("만료된 쿠폰 정책은 수정할 수 없습니다");
        }

        @Test
        @DisplayName("0으로 설정 (발급 중단)")
        void updateToZero() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(0));

            // when
            policy.updateRemainingQuantity(0);

            // then
            assertThat(policy.getMaxIssueCount()).isEqualTo(0);
            assertThat(policy.isIssuable()).isFalse(); // 더 이상 발급 불가
        }

        @Test
        @DisplayName("현재 발급 수와 동일하게 설정")
        void updateToCurrentIssuedCount() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(50));

            // when
            policy.updateRemainingQuantity(50);

            // then
            assertThat(policy.getMaxIssueCount()).isEqualTo(50);
            assertThat(policy.isIssuable()).isFalse(); // 추가 발급 불가
        }
    }
}
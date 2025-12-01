package com.teambind.coupon.common.util;

import com.teambind.coupon.common.util.CouponCodeGenerator.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 쿠폰 코드 생성기 테스트
 */
@DisplayName("쿠폰 코드 생성기 테스트")
class CouponCodeGeneratorTest {

    private final CouponCodeGenerator generator = new CouponCodeGenerator();

    @Nested
    @DisplayName("알파뉴메릭 전략")
    class AlphanumericStrategyTest {

        private final AlphanumericStrategy strategy = new AlphanumericStrategy();

        @Test
        @DisplayName("기본 길이 코드 생성")
        void generateDefaultLengthCode() {
            // when
            String code = strategy.generate(8);

            // then
            assertThat(code).hasSize(8);
            assertThat(code).matches("^[A-Z0-9]{8}$");
        }

        @ParameterizedTest
        @ValueSource(ints = {6, 10, 12, 16})
        @DisplayName("다양한 길이의 코드 생성")
        void generateVariousLengthCodes(int length) {
            // when
            String code = strategy.generate(length);

            // then
            assertThat(code).hasSize(length);
            assertThat(code).matches("^[A-Z0-9]{" + length + "}$");
        }

        @Test
        @DisplayName("배치 생성 - 고유성 보장")
        void generateBatchUniqueness() {
            // when
            Set<String> codes = strategy.generateBatch(8, 100);

            // then
            assertThat(codes).hasSize(100); // 모두 고유해야 함
            codes.forEach(code -> {
                assertThat(code).hasSize(8);
                assertThat(code).matches("^[A-Z0-9]{8}$");
            });
        }

        @Test
        @DisplayName("음수 길이 예외 처리")
        void handleNegativeLength() {
            // when & then
            assertThatThrownBy(() -> strategy.generate(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Length must be positive");
        }

        @Test
        @DisplayName("대량 생성시 충돌 방지")
        void preventCollisionInLargeScale() {
            // given
            Set<String> codes = new HashSet<>();

            // when
            for (int i = 0; i < 1000; i++) {
                codes.add(strategy.generate(10));
            }

            // then
            assertThat(codes).hasSize(1000); // 충돌 없이 모두 생성
        }
    }

    @Nested
    @DisplayName("프리픽스 기반 전략")
    class PrefixBasedStrategyTest {

        private final PrefixBasedStrategy strategy = new PrefixBasedStrategy();

        @Test
        @DisplayName("프리픽스 포함 코드 생성")
        void generateWithPrefix() {
            // when
            String code = strategy.generate("SUMMER", 6);

            // then
            assertThat(code).startsWith("SUMMER");
            assertThat(code).hasSize(12); // SUMMER(6) + 6자리
            assertThat(code).matches("^SUMMER[A-Z0-9]{6}$");
        }

        @Test
        @DisplayName("빈 프리픽스 예외 처리")
        void handleEmptyPrefix() {
            // when & then
            assertThatThrownBy(() -> strategy.generate("", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Prefix cannot be null or empty");
        }

        @Test
        @DisplayName("null 프리픽스 예외 처리")
        void handleNullPrefix() {
            // when & then
            assertThatThrownBy(() -> strategy.generate(null, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Prefix cannot be null or empty");
        }

        @Test
        @DisplayName("배치 생성")
        void generateBatch() {
            // when
            Set<String> codes = strategy.generateBatch("EVENT", 4, 50);

            // then
            assertThat(codes).hasSize(50);
            codes.forEach(code -> {
                assertThat(code).startsWith("EVENT");
                assertThat(code).hasSize(9); // EVENT(5) + 4자리
            });
        }
    }

    @Nested
    @DisplayName("날짜 기반 전략")
    class DateBasedStrategyTest {

        private final DateBasedStrategy strategy = new DateBasedStrategy();

        @Test
        @DisplayName("현재 날짜 포함 코드 생성")
        void generateWithCurrentDate() {
            // given
            LocalDateTime now = LocalDateTime.now();
            String expectedDatePart = String.format("%04d%02d%02d",
                    now.getYear(), now.getMonth().getValue(), now.getDayOfMonth());

            // when
            String code = strategy.generate(now, 6);

            // then
            assertThat(code).startsWith(expectedDatePart);
            assertThat(code).hasSize(14); // 8자리 날짜 + 6자리 랜덤
        }

        @Test
        @DisplayName("카테고리 포함 생성")
        void generateWithCategory() {
            // given
            LocalDateTime date = LocalDateTime.of(2024, 1, 15, 0, 0);

            // when
            String code = strategy.generateWithCategory(date, "NEWYEAR", 4);

            // then
            assertThat(code).startsWith("NEWYEAR_20240115");
            assertThat(code).matches("^NEWYEAR_20240115[A-Z0-9]{4}$");
        }

        @Test
        @DisplayName("null 날짜는 현재 시간 사용")
        void useCurrentDateForNull() {
            // given
            LocalDateTime now = LocalDateTime.now();
            String expectedDatePart = String.format("%04d%02d%02d",
                    now.getYear(), now.getMonth().getValue(), now.getDayOfMonth());

            // when
            String code = strategy.generate(null, 4);

            // then
            assertThat(code).startsWith(expectedDatePart);
        }

        @Test
        @DisplayName("동일 날짜 내 고유성")
        void uniquenessWithinSameDate() {
            // given
            LocalDateTime fixedDate = LocalDateTime.of(2024, 1, 1, 0, 0);
            Set<String> codes = new HashSet<>();

            // when
            for (int i = 0; i < 100; i++) {
                codes.add(strategy.generate(fixedDate, 6));
            }

            // then
            assertThat(codes).hasSize(100); // 동일 날짜에도 모두 고유
        }
    }

    @Nested
    @DisplayName("커스텀 패턴 전략")
    class CustomPatternStrategyTest {

        private final CustomPatternStrategy strategy = new CustomPatternStrategy();

        @Test
        @DisplayName("패턴 기반 코드 생성")
        void generateWithPattern() {
            // given
            LocalDateTime date = LocalDateTime.of(2024, 1, 15, 0, 0);
            String pattern = "COUPON-{YYYY}-{MM}-{RAND:4}";

            // when
            String code = strategy.generate(pattern, date);

            // then
            assertThat(code).matches("^COUPON-2024-01-[A-Z0-9]{4}$");
        }

        @Test
        @DisplayName("복잡한 패턴 처리")
        void handleComplexPattern() {
            // given
            LocalDateTime date = LocalDateTime.of(2024, 3, 15, 0, 0);
            String pattern = "SP{YY}{MM}{DD}-{RAND:6}";

            // when
            String code = strategy.generate(pattern, date);

            // then
            assertThat(code).matches("^SP240315-[A-Z0-9]{6}$");
        }

        @Test
        @DisplayName("다중 랜덤 블록 패턴")
        void handleMultipleRandomBlocks() {
            // given
            String pattern = "{RAND:3}-{YYYY}-{RAND:2}";
            LocalDateTime date = LocalDateTime.of(2024, 1, 1, 0, 0);

            // when
            String code = strategy.generate(pattern, date);

            // then
            assertThat(code).matches("^[A-Z0-9]{3}-2024-[A-Z0-9]{2}$");
        }

        @Test
        @DisplayName("빈 패턴 예외 처리")
        void handleEmptyPattern() {
            // when & then
            assertThatThrownBy(() -> strategy.generate("", LocalDateTime.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Pattern cannot be null or empty");
        }
    }

    @Nested
    @DisplayName("CouponCodeGenerator 통합 테스트")
    class IntegrationTest {

        @Test
        @DisplayName("간단한 알파뉴메릭 코드 생성")
        void generateSimpleCode() {
            // when
            String code = generator.generateSimple(10);

            // then
            assertThat(code).hasSize(10);
            assertThat(code).matches("^[A-Z0-9]{10}$");
        }

        @Test
        @DisplayName("프리픽스 코드 생성")
        void generateWithPrefix() {
            // when
            String code = generator.generateWithPrefix("PROMO", 6);

            // then
            assertThat(code).startsWith("PROMO");
            assertThat(code).hasSize(11); // PROMO(5) + 6
        }

        @Test
        @DisplayName("날짜 기반 코드 생성")
        void generateDateBased() {
            // given
            LocalDateTime now = LocalDateTime.now();
            String expectedDatePart = String.format("%04d%02d%02d",
                    now.getYear(), now.getMonth().getValue(), now.getDayOfMonth());

            // when
            String code = generator.generateDateBased(4);

            // then
            assertThat(code).startsWith(expectedDatePart);
            assertThat(code).hasSize(12); // 8자리 날짜 + 4자리 랜덤
        }

        @Test
        @DisplayName("커스텀 패턴 코드 생성")
        void generateCustom() {
            // given
            String pattern = "SALE-{YYYY}{MM}-{RAND:5}";

            // when
            String code = generator.generateCustom(pattern);

            // then
            assertThat(code).matches("^SALE-\\d{6}-[A-Z0-9]{5}$");
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("멀티스레드 환경에서 코드 생성 고유성")
        void concurrentCodeGeneration() throws InterruptedException {
            // given
            int threadCount = 50;
            int codesPerThread = 100;
            ConcurrentHashMap<String, Boolean> allCodes = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            AlphanumericStrategy strategy = new AlphanumericStrategy();

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < codesPerThread; j++) {
                            String code = strategy.generate(12);
                            allCodes.put(code, true);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();

            // then
            assertThat(allCodes).hasSize(threadCount * codesPerThread); // 모두 고유
            executor.shutdown();
        }

        @Test
        @DisplayName("다양한 전략 동시 사용")
        void concurrentMultipleStrategies() throws InterruptedException {
            // given
            int iterations = 100;
            ConcurrentHashMap<String, String> codeToStrategy = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(3);
            ExecutorService executor = Executors.newFixedThreadPool(3);

            // when
            executor.submit(() -> {
                try {
                    AlphanumericStrategy strategy = new AlphanumericStrategy();
                    for (int i = 0; i < iterations; i++) {
                        String code = strategy.generate(12);
                        codeToStrategy.put(code, "Alphanumeric");
                    }
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    PrefixBasedStrategy strategy = new PrefixBasedStrategy();
                    for (int i = 0; i < iterations; i++) {
                        String code = strategy.generate("PRE", 9);
                        codeToStrategy.put(code, "Prefix");
                    }
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    DateBasedStrategy strategy = new DateBasedStrategy();
                    for (int i = 0; i < iterations; i++) {
                        String code = strategy.generate(LocalDateTime.now(), 4);
                        codeToStrategy.put(code, "Date");
                    }
                } finally {
                    latch.countDown();
                }
            });

            latch.await();

            // then
            assertThat(codeToStrategy).hasSize(3 * iterations); // 전략 간에도 중복 없음
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("성능 테스트")
    class PerformanceTest {

        @Test
        @DisplayName("대량 코드 생성 성능")
        void bulkGenerationPerformance() {
            // given
            AlphanumericStrategy strategy = new AlphanumericStrategy();
            int count = 10000;

            // when
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                strategy.generate(12);
            }
            long endTime = System.currentTimeMillis();

            // then
            long duration = endTime - startTime;
            System.out.println("Generated " + count + " codes in " + duration + "ms");
            assertThat(duration).isLessThan(1000); // 1만개를 1초 이내 생성
        }

        @Test
        @DisplayName("배치 생성 성능")
        void batchGenerationPerformance() {
            // given
            AlphanumericStrategy strategy = new AlphanumericStrategy();

            // when
            long startTime = System.currentTimeMillis();
            Set<String> codes = strategy.generateBatch(12, 1000);
            long endTime = System.currentTimeMillis();

            // then
            long duration = endTime - startTime;
            System.out.println("Batch generated 1000 codes in " + duration + "ms");
            assertThat(codes).hasSize(1000);
            assertThat(duration).isLessThan(200); // 1000개 배치를 200ms 이내
        }
    }
}
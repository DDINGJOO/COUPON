package com.teambind.coupon.common.util;

import com.teambind.coupon.common.util.CouponCodeGenerator.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
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

    @Nested
    @DisplayName("알파벳-숫자 생성 전략")
    class AlphanumericStrategyTest {

        private final AlphanumericStrategy strategy = new AlphanumericStrategy();

        @Test
        @DisplayName("기본 8자리 코드 생성")
        void generateDefault8CharCode() {
            // when
            String code = strategy.generate("TEST", 0);

            // then
            assertThat(code).hasSize(8);
            assertThat(code).matches("^[A-Z0-9]{8}$");
        }

        @ParameterizedTest
        @ValueSource(ints = {6, 10, 12, 16})
        @DisplayName("다양한 길이의 코드 생성")
        void generateVariousLengthCodes(int length) {
            // when
            String code = strategy.generate("TEST", length);

            // then
            assertThat(code).hasSize(length);
            assertThat(code).matches("^[A-Z0-9]{" + length + "}$");
        }

        @Test
        @DisplayName("코드 중복성 테스트")
        void checkUniqueness() {
            // given
            Set<String> codes = new HashSet<>();
            int count = 10000;

            // when
            for (int i = 0; i < count; i++) {
                codes.add(strategy.generate("TEST", 8));
            }

            // then
            assertThat(codes).hasSize(count); // 모두 고유해야 함
        }

        @RepeatedTest(100)
        @DisplayName("생성된 코드 패턴 검증")
        void validateGeneratedPattern() {
            // when
            String code = strategy.generate("TEST", 8);

            // then
            assertThat(code).doesNotContain("O", "0", "I", "l"); // 혼동 가능한 문자 제외
            assertThat(code).matches("^[A-Z2-9]{8}$");
        }
    }

    @Nested
    @DisplayName("접두사 기반 생성 전략")
    class PrefixBasedStrategyTest {

        private final PrefixBasedStrategy strategy = new PrefixBasedStrategy();

        @Test
        @DisplayName("접두사 포함 코드 생성")
        void generateWithPrefix() {
            // when
            String code = strategy.generate("SUMMER", 12);

            // then
            assertThat(code).startsWith("SUMMER");
            assertThat(code).hasSize(12);
            assertThat(code).matches("^SUMMER[A-Z0-9]{6}$");
        }

        @Test
        @DisplayName("빈 접두사 처리")
        void handleEmptyPrefix() {
            // when
            String code = strategy.generate("", 10);

            // then
            assertThat(code).hasSize(10);
            assertThat(code).matches("^[A-Z0-9]{10}$");
        }

        @Test
        @DisplayName("접두사가 길이보다 긴 경우")
        void handleLongPrefix() {
            // when
            String code = strategy.generate("VERYLONGPREFIX", 10);

            // then
            assertThat(code).isEqualTo("VERYLONGPR"); // 잘림
            assertThat(code).hasSize(10);
        }

        @Test
        @DisplayName("null 접두사 처리")
        void handleNullPrefix() {
            // when
            String code = strategy.generate(null, 10);

            // then
            assertThat(code).hasSize(10);
            assertThat(code).matches("^[A-Z0-9]{10}$");
        }
    }

    @Nested
    @DisplayName("날짜 기반 생성 전략")
    class DateBasedStrategyTest {

        private final DateBasedStrategy strategy = new DateBasedStrategy();

        @Test
        @DisplayName("날짜 포함 코드 생성")
        void generateWithDate() {
            // when
            String code = strategy.generate("EVENT", 16);
            LocalDate today = LocalDate.now();
            String expectedDatePart = String.format("%04d%02d%02d",
                    today.getYear(), today.getMonthValue(), today.getDayOfMonth());

            // then
            assertThat(code).contains(expectedDatePart);
            assertThat(code).hasSize(16);
        }

        @Test
        @DisplayName("날짜 형식 검증")
        void validateDateFormat() {
            // when
            String code = strategy.generate("TEST", 16);

            // then
            // YYYYMMDD 형식이 포함되어 있는지 확인
            Pattern datePattern = Pattern.compile("\\d{8}");
            assertThat(datePattern.matcher(code).find()).isTrue();
        }

        @Test
        @DisplayName("동일 날짜 내 고유성")
        void uniquenessWithinSameDate() {
            // given
            Set<String> codes = new HashSet<>();

            // when
            for (int i = 0; i < 1000; i++) {
                codes.add(strategy.generate("TEST", 16));
            }

            // then
            assertThat(codes).hasSize(1000); // 동일 날짜에도 모두 고유
        }
    }

    @Nested
    @DisplayName("UUID 기반 생성 전략")
    class UuidBasedStrategyTest {

        private final UuidBasedStrategy strategy = new UuidBasedStrategy();

        @Test
        @DisplayName("UUID 기반 코드 생성")
        void generateUuidBased() {
            // when
            String code = strategy.generate("TEST", 12);

            // then
            assertThat(code).hasSize(12);
            assertThat(code).matches("^[A-Z0-9]{12}$");
        }

        @Test
        @DisplayName("UUID 고유성 보장")
        void guaranteeUuidUniqueness() {
            // given
            Set<String> codes = new HashSet<>();
            int count = 100000;

            // when
            for (int i = 0; i < count; i++) {
                codes.add(strategy.generate("TEST", 16));
            }

            // then
            assertThat(codes).hasSize(count); // 10만개 모두 고유
        }

        @Test
        @DisplayName("다양한 길이 지원")
        void supportVariousLengths() {
            // when & then
            for (int length = 8; length <= 32; length += 4) {
                String code = strategy.generate("TEST", length);
                assertThat(code).hasSize(length);
            }
        }
    }

    @Nested
    @DisplayName("체크섬 포함 생성 전략")
    class ChecksumStrategyTest {

        private final ChecksumStrategy strategy = new ChecksumStrategy();

        @Test
        @DisplayName("체크섬 포함 코드 생성")
        void generateWithChecksum() {
            // when
            String code = strategy.generate("TEST", 10);

            // then
            assertThat(code).hasSize(10);
            assertThat(strategy.validateChecksum(code)).isTrue();
        }

        @Test
        @DisplayName("체크섬 검증 - 유효한 코드")
        void validateValidChecksum() {
            // given
            String validCode = strategy.generate("TEST", 12);

            // then
            assertThat(strategy.validateChecksum(validCode)).isTrue();
        }

        @Test
        @DisplayName("체크섬 검증 - 변조된 코드")
        void validateInvalidChecksum() {
            // given
            String validCode = strategy.generate("TEST", 12);
            // 코드 변조
            char[] chars = validCode.toCharArray();
            chars[5] = chars[5] == 'A' ? 'B' : 'A';
            String tamperedCode = new String(chars);

            // then
            assertThat(strategy.validateChecksum(tamperedCode)).isFalse();
        }

        @Test
        @DisplayName("다양한 길이에서 체크섬 동작")
        void checksumWithVariousLengths() {
            // when & then
            for (int length = 8; length <= 20; length++) {
                String code = strategy.generate("TEST", length);
                assertThat(strategy.validateChecksum(code)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("멀티스레드 환경에서 코드 생성 고유성")
        void concurrentCodeGeneration() throws InterruptedException {
            // given
            int threadCount = 100;
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
                            String code = strategy.generate("TEST", 12);
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
            GenerationStrategy[] strategies = {
                    new AlphanumericStrategy(),
                    new PrefixBasedStrategy(),
                    new DateBasedStrategy(),
                    new UuidBasedStrategy(),
                    new ChecksumStrategy()
            };

            int iterations = 1000;
            ConcurrentHashMap<String, String> codeToStrategy = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(strategies.length);
            ExecutorService executor = Executors.newFixedThreadPool(strategies.length);

            // when
            for (GenerationStrategy strategy : strategies) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterations; i++) {
                            String code = strategy.generate("TEST", 12);
                            codeToStrategy.put(code, strategy.getClass().getSimpleName());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();

            // then
            assertThat(codeToStrategy).hasSize(strategies.length * iterations); // 전략 간에도 중복 없음
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCaseTest {

        @Test
        @DisplayName("최소 길이 코드 생성")
        void generateMinimumLengthCode() {
            // given
            AlphanumericStrategy strategy = new AlphanumericStrategy();

            // when
            String code = strategy.generate("TEST", 1);

            // then
            assertThat(code).hasSize(1);
            assertThat(code).matches("^[A-Z0-9]$");
        }

        @Test
        @DisplayName("최대 길이 코드 생성")
        void generateMaximumLengthCode() {
            // given
            AlphanumericStrategy strategy = new AlphanumericStrategy();
            int maxLength = 100;

            // when
            String code = strategy.generate("TEST", maxLength);

            // then
            assertThat(code).hasSize(maxLength);
            assertThat(code).matches("^[A-Z0-9]{" + maxLength + "}$");
        }

        @Test
        @DisplayName("음수 길이 처리")
        void handleNegativeLength() {
            // given
            AlphanumericStrategy strategy = new AlphanumericStrategy();

            // when & then
            assertThatThrownBy(() -> strategy.generate("TEST", -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("0 길이 처리")
        void handleZeroLength() {
            // given
            AlphanumericStrategy strategy = new AlphanumericStrategy();

            // when
            String code = strategy.generate("TEST", 0);

            // then
            assertThat(code).hasSize(8); // 기본값으로 fallback
        }

        @Test
        @DisplayName("특수문자 포함 접두사")
        void handleSpecialCharPrefix() {
            // given
            PrefixBasedStrategy strategy = new PrefixBasedStrategy();

            // when
            String code = strategy.generate("TEST@#$", 12);

            // then
            assertThat(code).startsWith("TEST"); // 특수문자 제거
            assertThat(code).hasSize(12);
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
            int count = 100000;

            // when
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                strategy.generate("TEST", 12);
            }
            long endTime = System.currentTimeMillis();

            // then
            long duration = endTime - startTime;
            System.out.println("Generated " + count + " codes in " + duration + "ms");
            assertThat(duration).isLessThan(5000); // 10만개를 5초 이내 생성
        }

        @Test
        @DisplayName("체크섬 검증 성능")
        void checksumValidationPerformance() {
            // given
            ChecksumStrategy strategy = new ChecksumStrategy();
            String[] codes = new String[10000];
            for (int i = 0; i < codes.length; i++) {
                codes[i] = strategy.generate("TEST", 12);
            }

            // when
            long startTime = System.currentTimeMillis();
            for (String code : codes) {
                strategy.validateChecksum(code);
            }
            long endTime = System.currentTimeMillis();

            // then
            long duration = endTime - startTime;
            System.out.println("Validated " + codes.length + " checksums in " + duration + "ms");
            assertThat(duration).isLessThan(100); // 1만개 검증을 100ms 이내
        }
    }
}
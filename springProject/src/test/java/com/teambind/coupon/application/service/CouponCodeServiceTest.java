package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.common.util.CouponCodeGenerator;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.DistributionType;
import com.teambind.coupon.fixture.CouponPolicyFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 코드 서비스 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 코드 서비스 테스트")
class CouponCodeServiceTest {

    @InjectMocks
    private CouponCodeService couponCodeService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponPolicyPort saveCouponPolicyPort;

    @Mock
    private CouponCodeGenerator couponCodeGenerator;

    private CouponPolicy codePolicy;

    @BeforeEach
    void setUp() {
        codePolicy = CouponPolicyFixture.createCodePolicy();
    }

    @Nested
    @DisplayName("쿠폰 코드 생성 테스트")
    class CouponCodeGenerationTest {

        @Test
        @DisplayName("단일 쿠폰 코드 생성")
        void generateSingleCode() {
            // given
            Long policyId = codePolicy.getId();
            String expectedCode = "SUMMER2024";

            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(codePolicy));
            when(couponCodeGenerator.generate())
                    .thenReturn(expectedCode);
            when(loadCouponPolicyPort.existsByCode(expectedCode))
                    .thenReturn(false);

            // when
            String code = couponCodeService.generateCode(policyId);

            // then
            assertThat(code).isEqualTo(expectedCode);
            verify(saveCouponPolicyPort).save(argThat(policy ->
                policy.getCouponCode().equals(expectedCode)
            ));
        }

        @Test
        @DisplayName("중복 코드 재생성")
        void regenerateOnDuplicateCode() {
            // given
            Long policyId = codePolicy.getId();
            String duplicateCode = "DUP2024";
            String uniqueCode = "UNIQUE2024";

            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(codePolicy));
            when(couponCodeGenerator.generate())
                    .thenReturn(duplicateCode)
                    .thenReturn(uniqueCode);
            when(loadCouponPolicyPort.existsByCode(duplicateCode))
                    .thenReturn(true);
            when(loadCouponPolicyPort.existsByCode(uniqueCode))
                    .thenReturn(false);

            // when
            String code = couponCodeService.generateCode(policyId);

            // then
            assertThat(code).isEqualTo(uniqueCode);
            verify(couponCodeGenerator, times(2)).generate();
        }

        @Test
        @DisplayName("배치 쿠폰 코드 생성")
        void generateBatchCodes() {
            // given
            Long policyId = codePolicy.getId();
            int count = 100;
            Set<String> expectedCodes = new HashSet<>();
            for (int i = 0; i < count; i++) {
                expectedCodes.add("CODE" + i);
            }

            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(codePolicy));
            when(couponCodeGenerator.generateBatch(count))
                    .thenReturn(expectedCodes);
            when(loadCouponPolicyPort.existsByCodeIn(any()))
                    .thenReturn(false);

            // when
            Set<String> codes = couponCodeService.generateBatchCodes(policyId, count);

            // then
            assertThat(codes).hasSize(count);
            assertThat(codes).isEqualTo(expectedCodes);
        }

        @Test
        @DisplayName("최대 재시도 초과")
        void maxRetryExceeded() {
            // given
            Long policyId = codePolicy.getId();

            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(codePolicy));
            when(couponCodeGenerator.generate())
                    .thenReturn("ALWAYS_DUP");
            when(loadCouponPolicyPort.existsByCode("ALWAYS_DUP"))
                    .thenReturn(true);

            // when & then
            assertThatThrownBy(() ->
                couponCodeService.generateCode(policyId)
            )
            .isInstanceOf(CouponDomainException.class)
            .hasMessageContaining("Failed to generate unique code");
        }
    }

    @Nested
    @DisplayName("쿠폰 코드 검증 테스트")
    class CouponCodeValidationTest {

        @Test
        @DisplayName("유효한 쿠폰 코드")
        void validCouponCode() {
            // given
            String code = "VALID2024";
            when(loadCouponPolicyPort.loadByCodeAndActive(code))
                    .thenReturn(Optional.of(codePolicy));

            // when
            boolean valid = couponCodeService.validateCode(code);

            // then
            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 코드")
        void nonExistentCode() {
            // given
            String code = "INVALID2024";
            when(loadCouponPolicyPort.loadByCodeAndActive(code))
                    .thenReturn(Optional.empty());

            // when
            boolean valid = couponCodeService.validateCode(code);

            // then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("만료된 쿠폰 코드")
        void expiredCode() {
            // given
            String code = "EXPIRED2024";
            CouponPolicy expiredPolicy = CouponPolicyFixture.createExpiredPolicy();
            when(loadCouponPolicyPort.loadByCodeAndActive(code))
                    .thenReturn(Optional.of(expiredPolicy));

            // when
            boolean valid = couponCodeService.validateCode(code);

            // then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("비활성화된 쿠폰 코드")
        void inactiveCode() {
            // given
            String code = "INACTIVE2024";
            when(loadCouponPolicyPort.loadByCodeAndActive(code))
                    .thenReturn(Optional.empty());

            // when
            boolean valid = couponCodeService.validateCode(code);

            // then
            assertThat(valid).isFalse();
        }
    }

    @Nested
    @DisplayName("쿠폰 코드 포맷 테스트")
    class CouponCodeFormatTest {

        @Test
        @DisplayName("코드 정규화 - 소문자를 대문자로")
        void normalizeCodeToUpperCase() {
            // given
            String inputCode = "summer2024";
            String expectedCode = "SUMMER2024";

            // when
            String normalized = couponCodeService.normalizeCode(inputCode);

            // then
            assertThat(normalized).isEqualTo(expectedCode);
        }

        @Test
        @DisplayName("코드 정규화 - 공백 제거")
        void removeSpacesFromCode() {
            // given
            String inputCode = "SUMMER 2024";
            String expectedCode = "SUMMER2024";

            // when
            String normalized = couponCodeService.normalizeCode(inputCode);

            // then
            assertThat(normalized).isEqualTo(expectedCode);
        }

        @Test
        @DisplayName("코드 정규화 - 특수문자 처리")
        void handleSpecialCharacters() {
            // given
            String inputCode = "SUMMER-2024-SALE";
            String expectedCode = "SUMMER2024SALE";

            // when
            String normalized = couponCodeService.normalizeCode(inputCode);

            // then
            assertThat(normalized).isEqualTo(expectedCode);
        }

        @Test
        @DisplayName("코드 길이 검증")
        void validateCodeLength() {
            // given
            String shortCode = "ABC";
            String longCode = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456";

            // when & then
            assertThatThrownBy(() ->
                couponCodeService.validateCodeFormat(shortCode)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Code length must be between");

            assertThatThrownBy(() ->
                couponCodeService.validateCodeFormat(longCode)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Code length must be between");
        }
    }

    @Nested
    @DisplayName("쿠폰 코드 전략별 테스트")
    class CouponCodeStrategyTest {

        @Test
        @DisplayName("알파뉴메릭 전략")
        void alphanumericStrategy() {
            // given
            String strategy = "ALPHANUMERIC";
            String expectedPattern = "[A-Z0-9]+";

            // when
            couponCodeService.setGeneratorStrategy(strategy);

            // then
            verify(couponCodeGenerator).setStrategy(argThat(s ->
                s.equals(strategy)
            ));
        }

        @Test
        @DisplayName("날짜 기반 전략")
        void dateBasedStrategy() {
            // given
            String strategy = "DATE_BASED";

            // when
            couponCodeService.setGeneratorStrategy(strategy);

            // then
            verify(couponCodeGenerator).setStrategy(argThat(s ->
                s.equals(strategy)
            ));
        }

        @Test
        @DisplayName("커스텀 패턴 전략")
        void customPatternStrategy() {
            // given
            String strategy = "CUSTOM";
            String pattern = "SALE-{YYYY}-{RANDOM}";

            // when
            couponCodeService.setGeneratorStrategy(strategy, pattern);

            // then
            verify(couponCodeGenerator).setStrategy(eq(strategy), eq(pattern));
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시 다발적 코드 생성")
        void concurrentCodeGeneration() throws InterruptedException {
            // given
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            Set<String> generatedCodes = Collections.synchronizedSet(new HashSet<>());
            AtomicInteger successCount = new AtomicInteger(0);

            when(loadCouponPolicyPort.loadById(anyLong()))
                    .thenReturn(Optional.of(codePolicy));
            when(couponCodeGenerator.generate())
                    .thenAnswer(inv -> "CODE" + System.nanoTime());
            when(loadCouponPolicyPort.existsByCode(anyString()))
                    .thenReturn(false);
            when(saveCouponPolicyPort.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            // when
            for (int i = 0; i < threadCount; i++) {
                final Long policyId = (long) i;
                executor.submit(() -> {
                    try {
                        String code = couponCodeService.generateCode(policyId);
                        generatedCodes.add(code);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);

            // then
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(generatedCodes).hasSize(threadCount); // 모든 코드가 유니크해야 함
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("코드 히스토리 관리")
    class CodeHistoryManagementTest {

        @Test
        @DisplayName("코드 변경 이력 저장")
        void saveCodeChangeHistory() {
            // given
            Long policyId = codePolicy.getId();
            String oldCode = "OLD2024";
            String newCode = "NEW2024";

            codePolicy.updateCouponCode(oldCode);
            when(loadCouponPolicyPort.loadById(policyId))
                    .thenReturn(Optional.of(codePolicy));
            when(couponCodeGenerator.generate())
                    .thenReturn(newCode);
            when(loadCouponPolicyPort.existsByCode(newCode))
                    .thenReturn(false);

            // when
            couponCodeService.changeCode(policyId, newCode);

            // then
            verify(saveCouponPolicyPort).save(argThat(policy ->
                policy.getCouponCode().equals(newCode)
            ));
            verify(saveCouponPolicyPort).saveHistory(eq(policyId), eq(oldCode), eq(newCode));
        }

        @Test
        @DisplayName("코드 사용 통계 조회")
        void getCodeUsageStatistics() {
            // given
            String code = "STATS2024";
            Map<String, Object> expectedStats = new HashMap<>();
            expectedStats.put("totalIssued", 100);
            expectedStats.put("totalUsed", 50);
            expectedStats.put("usageRate", 0.5);

            when(loadCouponPolicyPort.getCodeStatistics(code))
                    .thenReturn(expectedStats);

            // when
            Map<String, Object> stats = couponCodeService.getCodeStatistics(code);

            // then
            assertThat(stats).containsEntry("totalIssued", 100);
            assertThat(stats).containsEntry("totalUsed", 50);
            assertThat(stats).containsEntry("usageRate", 0.5);
        }
    }
}
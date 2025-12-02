package com.teambind.coupon.domain.service;

import com.teambind.coupon.domain.service.CouponCodeGenerator.GeneratorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AlphanumericCodeGenerator 단위 테스트
 */
@DisplayName("AlphanumericCodeGenerator 테스트")
class AlphanumericCodeGeneratorTest {

    private AlphanumericCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AlphanumericCodeGenerator();
    }

    @Test
    @DisplayName("기본 코드 생성")
    void generate_Default() {
        // when
        String code = generator.generate();

        // then
        assertThat(code).isNotEmpty();
        assertThat(code).containsPattern("[A-Z0-9-]+");
    }

    @Test
    @DisplayName("프리픽스 포함 코드 생성")
    void generate_WithPrefix() {
        // when
        String code = generator.generate("TEST");

        // then
        assertThat(code).isNotEmpty();
        assertThat(code).startsWith("TEST-");
    }

    @Test
    @DisplayName("null 프리픽스로 코드 생성")
    void generate_NullPrefix() {
        // when
        String code = generator.generate(null);

        // then
        assertThat(code).isNotEmpty();
    }

    @Test
    @DisplayName("빈 프리픽스로 코드 생성")
    void generate_EmptyPrefix() {
        // when
        String code = generator.generate("");

        // then
        assertThat(code).isNotEmpty();
    }

    @Test
    @DisplayName("특수문자 포함 프리픽스 정규화")
    void generate_PrefixNormalization() {
        // when
        String code = generator.generate("test@123!");

        // then
        assertThat(code).startsWith("TEST123-");
    }

    @Test
    @DisplayName("배치 코드 생성")
    void generateBatch() {
        // when
        String[] batch = generator.generateBatch(10);

        // then
        assertThat(batch).hasSize(10);
        // 중복 없음 확인
        Set<String> uniqueCodes = new HashSet<>();
        for (String code : batch) {
            assertThat(code).isNotEmpty();
            uniqueCodes.add(code);
        }
        assertThat(uniqueCodes).hasSize(10);
    }

    @Test
    @DisplayName("배치 생성 - 최대 개수")
    void generateBatch_MaxCount() {
        // when
        String[] batch = generator.generateBatch(10000);

        // then
        assertThat(batch).hasSize(10000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10001})
    @DisplayName("배치 생성 - 잘못된 개수로 예외 발생")
    void generateBatch_InvalidCount(int count) {
        // when & then
        assertThatThrownBy(() -> generator.generateBatch(count))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("생성 개수는 1~10000 사이여야 합니다");
    }

    @Test
    @DisplayName("타입 확인")
    void getType() {
        // when
        GeneratorType type = generator.getType();

        // then
        assertThat(type).isEqualTo(GeneratorType.ALPHANUMERIC);
    }

    @Test
    @DisplayName("생성된 코드 포맷 확인")
    void generateCodeFormat() {
        // given
        String prefix = "COUPON";

        // when
        String code = generator.generate(prefix);

        // then
        // 프리픽스-코드 형식
        assertThat(code).matches("^COUPON-[A-Z0-9-]+$");
    }

    @Test
    @DisplayName("코드 유일성 검증")
    void generate_Uniqueness() {
        // given
        Set<String> codes = new HashSet<>();
        int count = 100;

        // when
        for (int i = 0; i < count; i++) {
            codes.add(generator.generate());
        }

        // then
        assertThat(codes).hasSize(count); // 모두 유일해야 함
    }
}
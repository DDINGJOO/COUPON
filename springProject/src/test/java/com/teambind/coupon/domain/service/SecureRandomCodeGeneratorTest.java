package com.teambind.coupon.domain.service;

import com.teambind.coupon.domain.service.CouponCodeGenerator.GeneratorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecureRandomCodeGenerator 단위 테스트
 */
@DisplayName("SecureRandomCodeGenerator 테스트")
class SecureRandomCodeGeneratorTest {

    private SecureRandomCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SecureRandomCodeGenerator();
    }

    @Test
    @DisplayName("기본 보안 코드 생성")
    void generate_Default() {
        // when
        String code = generator.generate();

        // then
        assertThat(code).isNotEmpty();
        assertThat(code).hasSize(19); // 16자리 + 3개의 대시(-)
        assertThat(code).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
    }

    @Test
    @DisplayName("프리픽스 포함 코드 생성")
    void generate_WithPrefix() {
        // when
        String code = generator.generate("SECURE");

        // then
        assertThat(code).isNotEmpty();
        assertThat(code).startsWith("SECURE:");
        assertThat(code).contains(":");

        // 프리픽스 이후 부분 검증
        String[] parts = code.split(":");
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isEqualTo("SECURE");
        assertThat(parts[1]).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
    }

    @Test
    @DisplayName("null 프리픽스로 코드 생성")
    void generate_NullPrefix() {
        // when
        String code = generator.generate(null);

        // then
        assertThat(code).isNotEmpty();
        assertThat(code).doesNotContain(":");
        assertThat(code).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
    }

    @Test
    @DisplayName("빈 프리픽스로 코드 생성")
    void generate_EmptyPrefix() {
        // when
        String code = generator.generate("");

        // then
        assertThat(code).isNotEmpty();
        assertThat(code).doesNotContain(":");
        assertThat(code).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
    }

    @Test
    @DisplayName("특수문자 포함 프리픽스 정규화")
    void generate_PrefixNormalization() {
        // when
        String code1 = generator.generate("test@123!");
        String code2 = generator.generate("TEST-123");
        String code3 = generator.generate("Te$st#123");

        // then
        assertThat(code1).startsWith("TEST123:");
        assertThat(code2).startsWith("TEST123:");
        assertThat(code3).startsWith("TEST123:");
    }

    @Test
    @DisplayName("배치 코드 생성")
    void generateBatch() {
        // when
        String[] batch = generator.generateBatch(10);

        // then
        assertThat(batch).hasSize(10);

        // 중복 검증
        Set<String> uniqueCodes = new HashSet<>();
        for (String code : batch) {
            assertThat(code).isNotEmpty();
            assertThat(code).matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
            uniqueCodes.add(code);
        }
        assertThat(uniqueCodes).hasSize(10);
    }

    @Test
    @DisplayName("배치 생성 - 최대 개수")
    void generateBatch_MaxCount() {
        // when
        String[] batch = generator.generateBatch(1000);

        // then
        assertThat(batch).hasSize(1000);

        // 샘플링하여 중복 확인
        Set<String> sampleCodes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            sampleCodes.add(batch[i]);
        }
        assertThat(sampleCodes).hasSize(100);
    }

    @Test
    @DisplayName("배치 생성 - 잘못된 개수로 예외")
    void generateBatch_InvalidCount() {
        // when & then
        assertThatThrownBy(() -> generator.generateBatch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("보안 코드 생성은 1~1000개까지 가능합니다");

        assertThatThrownBy(() -> generator.generateBatch(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("보안 코드 생성은 1~1000개까지 가능합니다");

        assertThatThrownBy(() -> generator.generateBatch(1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("보안 코드 생성은 1~1000개까지 가능합니다");
    }

    @Test
    @DisplayName("타입 확인")
    void getType() {
        // when
        GeneratorType type = generator.getType();

        // then
        assertThat(type).isEqualTo(GeneratorType.SECURE_RANDOM);
    }

    @Test
    @DisplayName("코드 유일성 검증 - 100개 생성")
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

    @Test
    @DisplayName("연속 생성시 다른 코드 생성")
    void generate_Sequential() {
        // when
        String code1 = generator.generate();
        String code2 = generator.generate();
        String code3 = generator.generate();

        // then
        assertThat(code1).isNotEqualTo(code2);
        assertThat(code2).isNotEqualTo(code3);
        assertThat(code1).isNotEqualTo(code3);
    }

    @Test
    @DisplayName("코드 형식 검증")
    void generate_Format() {
        // given
        Pattern pattern = Pattern.compile("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");

        // when
        for (int i = 0; i < 10; i++) {
            String code = generator.generate();

            // then
            assertThat(pattern.matcher(code).matches()).isTrue();
            assertThat(code.length()).isEqualTo(19);
        }
    }
}
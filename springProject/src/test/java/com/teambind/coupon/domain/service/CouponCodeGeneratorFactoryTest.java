package com.teambind.coupon.domain.service;

import com.teambind.coupon.domain.service.CouponCodeGenerator.GeneratorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CouponCodeGeneratorFactory 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponCodeGeneratorFactory 테스트")
class CouponCodeGeneratorFactoryTest {

    private CouponCodeGeneratorFactory factory;

    @Mock
    private AlphanumericCodeGenerator alphanumericGenerator;

    @Mock
    private SecureRandomCodeGenerator secureRandomGenerator;

    @BeforeEach
    void setUp() {
        when(alphanumericGenerator.getType()).thenReturn(GeneratorType.ALPHANUMERIC);
        when(secureRandomGenerator.getType()).thenReturn(GeneratorType.SECURE_RANDOM);

        List<CouponCodeGenerator> generators = List.of(
                alphanumericGenerator,
                secureRandomGenerator
        );

        factory = new CouponCodeGeneratorFactory(generators);

        // PostConstruct 메서드를 수동으로 호출
        factory.init();
    }

    @Test
    @DisplayName("ALPHANUMERIC 타입 생성기 조회")
    void getGenerator_Alphanumeric() {
        // when
        CouponCodeGenerator generator = factory.getGenerator(GeneratorType.ALPHANUMERIC);

        // then
        assertThat(generator).isEqualTo(alphanumericGenerator);
    }

    @Test
    @DisplayName("SECURE_RANDOM 타입 생성기 조회")
    void getGenerator_SecureRandom() {
        // when
        CouponCodeGenerator generator = factory.getGenerator(GeneratorType.SECURE_RANDOM);

        // then
        assertThat(generator).isEqualTo(secureRandomGenerator);
    }

    @Test
    @DisplayName("기본 생성기 조회")
    void getDefaultGenerator() {
        // when
        CouponCodeGenerator generator = factory.getDefaultGenerator();

        // then
        assertThat(generator).isEqualTo(alphanumericGenerator);
    }

    @Test
    @DisplayName("보안 생성기 조회")
    void getSecureGenerator() {
        // when
        CouponCodeGenerator generator = factory.getSecureGenerator();

        // then
        assertThat(generator).isEqualTo(secureRandomGenerator);
    }

    @Test
    @DisplayName("커스텀 생성기 조회 - 없으면 기본 생성기 반환")
    void getCustomGenerator() {
        // when
        CouponCodeGenerator generator = factory.getCustomGenerator();

        // then
        // CUSTOM 타입 생성기가 없으므로 기본 생성기 반환
        assertThat(generator).isEqualTo(alphanumericGenerator);
    }

    @Test
    @DisplayName("존재하지 않는 타입 조회시 기본 생성기 반환")
    void getGenerator_UnknownType_ReturnsDefault() {
        // when
        CouponCodeGenerator generator = factory.getGenerator(GeneratorType.UUID);

        // then
        assertThat(generator).isEqualTo(alphanumericGenerator);
    }

    @Test
    @DisplayName("null 타입 조회시 기본 생성기 반환")
    void getGenerator_NullType_ReturnsDefault() {
        // when
        CouponCodeGenerator generator = factory.getGenerator(null);

        // then
        assertThat(generator).isEqualTo(alphanumericGenerator);
    }

    @Test
    @DisplayName("사용 가능한 생성기 타입 목록 조회")
    void getAvailableTypes() {
        // when
        GeneratorType[] types = factory.getAvailableTypes();

        // then
        assertThat(types).hasSize(2);
        assertThat(types).containsExactlyInAnyOrder(
                GeneratorType.ALPHANUMERIC,
                GeneratorType.SECURE_RANDOM
        );
    }

    @Test
    @DisplayName("빈 생성기 목록으로 팩토리 생성시에도 작동")
    void emptyGeneratorList() {
        // given
        factory = new CouponCodeGeneratorFactory(List.of());
        factory.init();

        // when
        GeneratorType[] types = factory.getAvailableTypes();

        // then
        assertThat(types).isEmpty();
    }
}
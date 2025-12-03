package com.teambind.coupon.adapter.out.persistence;

import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.mapper.CouponPolicyMapper;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.DiscountPolicy;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CouponPolicyPersistenceAdapter 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponPolicyPersistenceAdapter 테스트")
class CouponPolicyPersistenceAdapterTest {

    @Mock
    private CouponPolicyRepository repository;

    @Mock
    private CouponPolicyMapper mapper;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @InjectMocks
    private CouponPolicyPersistenceAdapter adapter;

    private CouponPolicy testPolicy;
    private CouponPolicyEntity testEntity;

    @BeforeEach
    void setUp() {
        // 테스트용 도메인 객체 생성
        testPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .description("테스트용 쿠폰")
                .discountPolicy(new DiscountPolicy(
                        DiscountType.FIXED_AMOUNT,
                        10000L,
                        null,
                        50000L
                ))
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(100)
                .maxUsagePerUser(1)
                .isActive(true)
                .createdBy("SYSTEM")
                .build();

        // 테스트용 엔티티 객체 생성
        testEntity = new CouponPolicyEntity();
        testEntity.setId(1L);
        testEntity.setCouponName("테스트 쿠폰");
        testEntity.setCouponCode("TEST2024");
        testEntity.setDescription("테스트용 쿠폰");
        testEntity.setDiscountType(DiscountType.FIXED_AMOUNT);
        testEntity.setDiscountAmount(10000L);
        testEntity.setMinOrderAmount(50000L);
        testEntity.setDistributionType(DistributionType.CODE);
        testEntity.setValidFrom(LocalDateTime.now());
        testEntity.setValidUntil(LocalDateTime.now().plusDays(30));
        testEntity.setMaxIssueCount(100);
        testEntity.setMaxUsagePerUser(1);
        testEntity.setIsActive(true);
        testEntity.setCreatedBy("SYSTEM");
    }

    @Test
    @DisplayName("ID로 쿠폰 정책 조회 - 성공")
    void loadById_Success() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testPolicy);

        // when
        Optional<CouponPolicy> result = adapter.loadById(1L);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testPolicy);
        verify(repository).findById(1L);
        verify(mapper).toDomain(testEntity);
    }

    @Test
    @DisplayName("ID로 쿠폰 정책 조회 - 존재하지 않음")
    void loadById_NotFound() {
        // given
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // when
        Optional<CouponPolicy> result = adapter.loadById(999L);

        // then
        assertThat(result).isEmpty();
        verify(repository).findById(999L);
        verify(mapper, never()).toDomain(any());
    }

    @Test
    @DisplayName("활성화된 쿠폰 코드로 정책 조회 - 성공")
    void loadByCodeAndActive_Success() {
        // given
        when(repository.findByCouponCodeAndIsActiveTrue("TEST2024"))
                .thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testPolicy);

        // when
        Optional<CouponPolicy> result = adapter.loadByCodeAndActive("TEST2024");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getCouponCode()).isEqualTo("TEST2024");
        verify(repository).findByCouponCodeAndIsActiveTrue("TEST2024");
    }

    @Test
    @DisplayName("락과 함께 쿠폰 코드로 정책 조회")
    void loadByCodeWithLock() {
        // given
        when(repository.findByCouponCodeWithLock("TEST2024"))
                .thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testPolicy);

        // when
        Optional<CouponPolicy> result = adapter.loadByCodeWithLock("TEST2024");

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testPolicy);
        verify(repository).findByCouponCodeWithLock("TEST2024");
    }

    @Test
    @DisplayName("쿠폰 코드 존재 여부 확인 - 존재함")
    void existsByCode_True() {
        // given
        when(repository.existsByCouponCode("TEST2024")).thenReturn(true);

        // when
        boolean result = adapter.existsByCode("TEST2024");

        // then
        assertThat(result).isTrue();
        verify(repository).existsByCouponCode("TEST2024");
    }

    @Test
    @DisplayName("쿠폰 코드 존재 여부 확인 - 존재하지 않음")
    void existsByCode_False() {
        // given
        when(repository.existsByCouponCode("INVALID")).thenReturn(false);

        // when
        boolean result = adapter.existsByCode("INVALID");

        // then
        assertThat(result).isFalse();
        verify(repository).existsByCouponCode("INVALID");
    }

    @Test
    @DisplayName("새로운 쿠폰 정책 저장 - ID 없음")
    void save_NewPolicy() {
        // given
        CouponPolicy newPolicy = CouponPolicy.builder()
                .couponName("신규 쿠폰")
                .couponCode("NEW2024")
                .description("신규 쿠폰")
                .discountPolicy(new DiscountPolicy(
                        DiscountType.PERCENTAGE,
                        10L,
                        5000L,
                        null
                ))
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(1000)
                .maxUsagePerUser(3)
                .isActive(true)
                .createdBy("ADMIN")
                .build();

        Long generatedId = 123456789L;
        when(idGenerator.nextId()).thenReturn(generatedId);
        when(mapper.toEntity(any(CouponPolicy.class))).thenReturn(testEntity);
        when(repository.save(any(CouponPolicyEntity.class))).thenReturn(testEntity);
        when(mapper.toDomain(testEntity)).thenReturn(testPolicy);

        // when
        CouponPolicy result = adapter.save(newPolicy);

        // then
        assertThat(result).isEqualTo(testPolicy);
        verify(idGenerator).nextId();

        ArgumentCaptor<CouponPolicy> policyCaptor = ArgumentCaptor.forClass(CouponPolicy.class);
        verify(mapper).toEntity(policyCaptor.capture());
        assertThat(policyCaptor.getValue().getId()).isEqualTo(generatedId);

        verify(repository).save(testEntity);
    }

    @Test
    @DisplayName("기존 쿠폰 정책 저장 - ID 있음")
    void save_ExistingPolicy() {
        // given
        when(mapper.toEntity(testPolicy)).thenReturn(testEntity);
        when(repository.save(testEntity)).thenReturn(testEntity);
        when(mapper.toDomain(testEntity)).thenReturn(testPolicy);

        // when
        CouponPolicy result = adapter.save(testPolicy);

        // then
        assertThat(result).isEqualTo(testPolicy);
        verify(idGenerator, never()).nextId();
        verify(mapper).toEntity(testPolicy);
        verify(repository).save(testEntity);
    }

    @Test
    @DisplayName("쿠폰 정책 업데이트 - 성공")
    void update_Success() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(testEntity));

        // when
        adapter.update(testPolicy);

        // then
        verify(repository).findById(1L);
        verify(mapper).updateEntity(testEntity, testPolicy);
        verify(repository).save(testEntity);
    }

    @Test
    @DisplayName("쿠폰 정책 업데이트 - 정책 없음으로 실패")
    void update_PolicyNotFound() {
        // given
        CouponPolicy nonExistentPolicy = CouponPolicy.builder()
                .id(999L)
                .build();
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adapter.update(nonExistentPolicy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰 정책을 찾을 수 없습니다");

        verify(repository).findById(999L);
        verify(mapper, never()).updateEntity(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("재고 감소 - 성공")
    void decrementStock_Success() {
        // given
        when(repository.decrementStock(1L)).thenReturn(1);

        // when
        boolean result = adapter.decrementStock(1L);

        // then
        assertThat(result).isTrue();
        verify(repository).decrementStock(1L);
    }

    @Test
    @DisplayName("재고 감소 - 실패")
    void decrementStock_Failed() {
        // given
        when(repository.decrementStock(1L)).thenReturn(0);

        // when
        boolean result = adapter.decrementStock(1L);

        // then
        assertThat(result).isFalse();
        verify(repository).decrementStock(1L);
    }

    @Test
    @DisplayName("ID 목록으로 정책 일괄 조회 - 정상")
    void loadByIds_Success() {
        // given
        List<Long> ids = Arrays.asList(1L, 2L, 3L);

        CouponPolicyEntity entity1 = new CouponPolicyEntity();
        entity1.setId(1L);
        CouponPolicyEntity entity2 = new CouponPolicyEntity();
        entity2.setId(2L);

        CouponPolicy policy1 = CouponPolicy.builder().id(1L).build();
        CouponPolicy policy2 = CouponPolicy.builder().id(2L).build();

        when(repository.findAllById(ids)).thenReturn(Arrays.asList(entity1, entity2));
        when(mapper.toDomain(entity1)).thenReturn(policy1);
        when(mapper.toDomain(entity2)).thenReturn(policy2);

        // when
        Map<Long, CouponPolicy> result = adapter.loadByIds(ids);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(1L)).isEqualTo(policy1);
        assertThat(result.get(2L)).isEqualTo(policy2);
        assertThat(result.get(3L)).isNull();
        verify(repository).findAllById(ids);
    }

    @Test
    @DisplayName("ID 목록으로 정책 일괄 조회 - 중복 ID 제거")
    void loadByIds_WithDuplicates() {
        // given
        List<Long> ids = Arrays.asList(1L, 1L, 2L, 2L, 3L);
        List<Long> uniqueIds = Arrays.asList(1L, 2L, 3L);

        CouponPolicyEntity entity1 = new CouponPolicyEntity();
        entity1.setId(1L);

        CouponPolicy policy1 = CouponPolicy.builder().id(1L).build();

        when(repository.findAllById(uniqueIds)).thenReturn(Arrays.asList(entity1));
        when(mapper.toDomain(entity1)).thenReturn(policy1);

        // when
        Map<Long, CouponPolicy> result = adapter.loadByIds(ids);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(1L)).isEqualTo(policy1);
        verify(repository).findAllById(uniqueIds);
    }

    @Test
    @DisplayName("ID 목록으로 정책 일괄 조회 - null 목록")
    void loadByIds_NullList() {
        // when
        Map<Long, CouponPolicy> result = adapter.loadByIds(null);

        // then
        assertThat(result).isEmpty();
        verify(repository, never()).findAllById(any());
    }

    @Test
    @DisplayName("ID 목록으로 정책 일괄 조회 - 빈 목록")
    void loadByIds_EmptyList() {
        // given
        List<Long> ids = Arrays.asList();

        // when
        Map<Long, CouponPolicy> result = adapter.loadByIds(ids);

        // then
        assertThat(result).isEmpty();
        verify(repository, never()).findAllById(any());
    }
}
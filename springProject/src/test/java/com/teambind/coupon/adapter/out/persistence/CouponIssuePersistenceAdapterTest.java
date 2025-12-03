package com.teambind.coupon.adapter.out.persistence;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.mapper.CouponIssueMapper;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountPolicy;
import com.teambind.coupon.domain.model.DiscountType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CouponIssuePersistenceAdapter 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponIssuePersistenceAdapter 테스트")
class CouponIssuePersistenceAdapterTest {

    @Mock
    private CouponIssueRepository repository;

    @Mock
    private CouponIssueMapper mapper;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @InjectMocks
    private CouponIssuePersistenceAdapter adapter;

    private CouponIssue testIssue;
    private CouponIssueEntity testEntity;

    @BeforeEach
    void setUp() {
        // 테스트용 도메인 객체 생성
        testIssue = CouponIssue.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("테스트 쿠폰")
                .discountPolicy(new DiscountPolicy(
                        DiscountType.FIXED_AMOUNT,
                        5000L,
                        null,
                        30000L
                ))
                .build();

        // 테스트용 엔티티 객체 생성
        testEntity = new CouponIssueEntity();
        testEntity.setId(1L);
        testEntity.setPolicyId(100L);
        testEntity.setUserId(1000L);
        testEntity.setStatus(CouponStatus.ISSUED);
        testEntity.setIssuedAt(LocalDateTime.now());
        testEntity.setCouponName("테스트 쿠폰");
    }

    @Test
    @DisplayName("ID로 쿠폰 발급 조회 - 성공")
    void loadById_Success() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        Optional<CouponIssue> result = adapter.loadById(1L);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testIssue);
        verify(repository).findById(1L);
    }

    @Test
    @DisplayName("ID와 사용자 ID로 쿠폰 발급 조회")
    void loadByIdAndUserId() {
        // given
        when(repository.findByIdAndUserId(1L, 1000L)).thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        Optional<CouponIssue> result = adapter.loadByIdAndUserId(1L, 1000L);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(1000L);
        verify(repository).findByIdAndUserId(1L, 1000L);
    }

    @Test
    @DisplayName("락과 함께 ID와 사용자 ID로 쿠폰 발급 조회")
    void loadByIdAndUserIdWithLock() {
        // given
        when(repository.findByIdAndUserIdWithLock(1L, 1000L)).thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        Optional<CouponIssue> result = adapter.loadByIdAndUserIdWithLock(1L, 1000L);

        // then
        assertThat(result).isPresent();
        verify(repository).findByIdAndUserIdWithLock(1L, 1000L);
    }

    @Test
    @DisplayName("예약 ID로 쿠폰 발급 조회 (락)")
    void loadByReservationIdWithLock() {
        // given
        String reservationId = "RES123";
        testIssue = CouponIssue.builder()
                .id(1L)
                .reservationId(reservationId)
                .build();
        testEntity.setReservationId(reservationId);

        when(repository.findByReservationIdWithLock(reservationId)).thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        Optional<CouponIssue> result = adapter.loadByReservationIdWithLock(reservationId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getReservationId()).isEqualTo(reservationId);
    }

    @Test
    @DisplayName("사용자별 쿠폰 발급 횟수 조회")
    void countUserIssuance() {
        // given
        when(repository.countByUserIdAndPolicyId(1000L, 100L)).thenReturn(3);

        // when
        int count = adapter.countUserIssuance(1000L, 100L);

        // then
        assertThat(count).isEqualTo(3);
        verify(repository).countByUserIdAndPolicyId(1000L, 100L);
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 목록 조회")
    void loadUsableCoupons() {
        // given
        LocalDateTime now = LocalDateTime.now();
        List<CouponIssueEntity> entities = Arrays.asList(testEntity);

        when(repository.findUsableCoupons(eq(1000L), any(LocalDateTime.class)))
                .thenReturn(entities);
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        List<CouponIssue> result = adapter.loadUsableCoupons(1000L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(testIssue);
        verify(repository).findUsableCoupons(eq(1000L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("타임아웃된 예약 목록 조회")
    void loadTimeoutReservations() {
        // given
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(10);
        List<CouponIssueEntity> entities = Arrays.asList(testEntity);

        when(repository.findTimeoutReservations(timeoutTime)).thenReturn(entities);
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        List<CouponIssue> result = adapter.loadTimeoutReservations(timeoutTime);

        // then
        assertThat(result).hasSize(1);
        verify(repository).findTimeoutReservations(timeoutTime);
    }

    @Test
    @DisplayName("만료된 쿠폰 목록 조회")
    void loadExpiredCoupons() {
        // given
        List<CouponIssueEntity> entities = Arrays.asList(testEntity);

        when(repository.findExpiredCoupons(any(LocalDateTime.class))).thenReturn(entities);
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        List<CouponIssue> result = adapter.loadExpiredCoupons();

        // then
        assertThat(result).hasSize(1);
        verify(repository).findExpiredCoupons(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("사용자별 상태별 쿠폰 개수 조회")
    void countByUserIdAndStatus() {
        // given
        when(repository.countByUserIdAndStatus(1000L, CouponStatus.ISSUED)).thenReturn(5);

        // when
        int count = adapter.countByUserIdAndStatus(1000L, CouponStatus.ISSUED);

        // then
        assertThat(count).isEqualTo(5);
        verify(repository).countByUserIdAndStatus(1000L, CouponStatus.ISSUED);
    }

    @Test
    @DisplayName("새로운 쿠폰 발급 저장 - ID 없음")
    void save_NewIssue() {
        // given
        CouponIssue newIssue = CouponIssue.builder()
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("신규 쿠폰")
                .build();

        Long generatedId = 987654321L;
        when(idGenerator.nextId()).thenReturn(generatedId);
        when(mapper.toEntity(any(CouponIssue.class))).thenReturn(testEntity);
        when(repository.save(any(CouponIssueEntity.class))).thenReturn(testEntity);
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        CouponIssue result = adapter.save(newIssue);

        // then
        assertThat(result).isEqualTo(testIssue);
        verify(idGenerator).nextId();

        ArgumentCaptor<CouponIssue> issueCaptor = ArgumentCaptor.forClass(CouponIssue.class);
        verify(mapper).toEntity(issueCaptor.capture());
        assertThat(issueCaptor.getValue().getId()).isEqualTo(generatedId);
    }

    @Test
    @DisplayName("기존 쿠폰 발급 저장 - ID 있음")
    void save_ExistingIssue() {
        // given
        when(mapper.toEntity(testIssue)).thenReturn(testEntity);
        when(repository.save(testEntity)).thenReturn(testEntity);
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        CouponIssue result = adapter.save(testIssue);

        // then
        assertThat(result).isEqualTo(testIssue);
        verify(idGenerator, never()).nextId();
    }

    @Test
    @DisplayName("쿠폰 발급 업데이트 - 성공")
    void update_Success() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(testEntity));

        // when
        adapter.update(testIssue);

        // then
        verify(repository).findById(1L);
        verify(mapper).updateEntity(testEntity, testIssue);
        verify(repository).save(testEntity);
    }

    @Test
    @DisplayName("쿠폰 발급 업데이트 - 발급 내역 없음")
    void update_NotFound() {
        // given
        CouponIssue nonExistentIssue = CouponIssue.builder().id(999L).build();
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adapter.update(nonExistentIssue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("발급된 쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("쿠폰 상태 일괄 업데이트")
    void updateStatusBatch() {
        // given
        List<Long> issueIds = Arrays.asList(1L, 2L, 3L);
        CouponStatus newStatus = CouponStatus.EXPIRED;
        LocalDateTime expiredAt = LocalDateTime.now();

        when(repository.updateStatusBatch(issueIds, newStatus, expiredAt)).thenReturn(3);

        // when
        int updated = adapter.updateStatusBatch(issueIds, newStatus, expiredAt);

        // then
        assertThat(updated).isEqualTo(3);
        verify(repository).updateStatusBatch(issueIds, newStatus, expiredAt);
    }

    @Test
    @DisplayName("쿠폰 일괄 저장 - 신규 발급")
    void saveAll_NewIssues() {
        // given
        CouponIssue issue1 = CouponIssue.builder()
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("쿠폰1")
                .build();

        CouponIssue issue2 = CouponIssue.builder()
                .policyId(101L)
                .userId(1001L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("쿠폰2")
                .build();

        List<CouponIssue> issues = Arrays.asList(issue1, issue2);

        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(mapper.toEntity(any(CouponIssue.class))).thenReturn(testEntity);
        when(repository.saveAll(anyList())).thenReturn(Arrays.asList(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        List<CouponIssue> result = adapter.saveAll(issues);

        // then
        assertThat(result).hasSize(1);
        verify(idGenerator, times(2)).nextId();
        verify(repository).saveAll(anyList());
    }

    @Test
    @DisplayName("쿠폰 일괄 저장 - 기존 발급")
    void saveAll_ExistingIssues() {
        // given
        CouponIssue issue1 = CouponIssue.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .build();

        List<CouponIssue> issues = Arrays.asList(issue1);

        when(mapper.toEntity(issue1)).thenReturn(testEntity);
        when(repository.saveAll(anyList())).thenReturn(Arrays.asList(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        List<CouponIssue> result = adapter.saveAll(issues);

        // then
        assertThat(result).hasSize(1);
        verify(idGenerator, never()).nextId();
    }

    @Test
    @DisplayName("사용자별 사용 가능한 쿠폰 조회")
    void findAvailableCouponsByUserId() {
        // given
        CouponIssue availableIssue = CouponIssue.builder()
                .id(1L)
                .status(CouponStatus.ISSUED)
                .expiredAt(LocalDateTime.now().plusDays(10))
                .build();

        CouponIssue expiredIssue = CouponIssue.builder()
                .id(2L)
                .status(CouponStatus.ISSUED)
                .expiredAt(LocalDateTime.now().minusDays(1))
                .build();

        CouponIssueEntity entity1 = new CouponIssueEntity();
        CouponIssueEntity entity2 = new CouponIssueEntity();

        when(repository.findByUserIdAndStatus(1000L, CouponStatus.ISSUED))
                .thenReturn(Arrays.asList(entity1, entity2));
        when(mapper.toDomain(entity1)).thenReturn(availableIssue);
        when(mapper.toDomain(entity2)).thenReturn(expiredIssue);

        // when
        List<CouponIssue> result = adapter.findAvailableCouponsByUserId(1000L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(availableIssue);
    }

    @Test
    @DisplayName("ID로 쿠폰 발급 찾기")
    void findById() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(testEntity));
        when(mapper.toDomain(testEntity)).thenReturn(testIssue);

        // when
        Optional<CouponIssue> result = adapter.findById(1L);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testIssue);
    }
}
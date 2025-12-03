package com.teambind.coupon.adapter.out.persistence;

import com.teambind.coupon.adapter.out.persistence.entity.CouponReservationJpaEntity;
import com.teambind.coupon.adapter.out.persistence.mapper.CouponReservationMapper;
import com.teambind.coupon.adapter.out.persistence.repository.CouponReservationRepository;
import com.teambind.coupon.domain.model.CouponReservation;
import com.teambind.coupon.domain.model.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CouponReservationPersistenceAdapter 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponReservationPersistenceAdapter 테스트")
class CouponReservationPersistenceAdapterTest {

    @Mock
    private CouponReservationRepository reservationRepository;

    @Mock
    private CouponReservationMapper reservationMapper;

    @InjectMocks
    private CouponReservationPersistenceAdapter adapter;

    private CouponReservation testReservation;
    private CouponReservationJpaEntity testEntity;

    @BeforeEach
    void setUp() {
        // 테스트용 도메인 객체 생성
        testReservation = CouponReservation.builder()
                .reservationId("RES123456")
                .policyId(100L)
                .userId(1000L)
                .orderId("ORD789")
                .orderAmount(50000L)
                .paymentAmount(45000L)
                .status(ReservationStatus.PENDING)
                .reservedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        // 테스트용 엔티티 객체 생성
        testEntity = new CouponReservationJpaEntity();
        testEntity.setReservationId("RES123456");
        testEntity.setPolicyId(100L);
        testEntity.setUserId(1000L);
        testEntity.setOrderId("ORD789");
        testEntity.setOrderAmount(50000L);
        testEntity.setPaymentAmount(45000L);
        testEntity.setStatus(ReservationStatus.PENDING);
        testEntity.setReservedAt(LocalDateTime.now());
        testEntity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    @DisplayName("쿠폰 예약 저장 - 성공")
    void save_Success() {
        // given
        when(reservationMapper.toJpaEntity(testReservation)).thenReturn(testEntity);
        when(reservationRepository.save(testEntity)).thenReturn(testEntity);
        when(reservationMapper.toDomain(testEntity)).thenReturn(testReservation);

        // when
        CouponReservation result = adapter.save(testReservation);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo("RES123456");
        assertThat(result.getPolicyId()).isEqualTo(100L);
        assertThat(result.getUserId()).isEqualTo(1000L);
        assertThat(result.getOrderId()).isEqualTo("ORD789");
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);

        verify(reservationMapper).toJpaEntity(testReservation);
        verify(reservationRepository).save(testEntity);
        verify(reservationMapper).toDomain(testEntity);
    }

    @Test
    @DisplayName("예약 ID로 쿠폰 예약 조회 - 성공")
    void findById_Success() {
        // given
        String reservationId = "RES123456";
        when(reservationRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(testEntity));
        when(reservationMapper.toDomain(testEntity)).thenReturn(testReservation);

        // when
        Optional<CouponReservation> result = adapter.findById(reservationId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getReservationId()).isEqualTo(reservationId);
        assertThat(result.get().getUserId()).isEqualTo(1000L);

        verify(reservationRepository).findByReservationId(reservationId);
        verify(reservationMapper).toDomain(testEntity);
    }

    @Test
    @DisplayName("예약 ID로 쿠폰 예약 조회 - 존재하지 않음")
    void findById_NotFound() {
        // given
        String reservationId = "INVALID_ID";
        when(reservationRepository.findByReservationId(reservationId))
                .thenReturn(Optional.empty());

        // when
        Optional<CouponReservation> result = adapter.findById(reservationId);

        // then
        assertThat(result).isEmpty();
        verify(reservationRepository).findByReservationId(reservationId);
        verify(reservationMapper, never()).toDomain(any());
    }

    @Test
    @DisplayName("만료된 예약 목록 조회 - 성공")
    void findExpiredReservations_Success() {
        // given
        LocalDateTime now = LocalDateTime.now();

        CouponReservationJpaEntity expiredEntity1 = new CouponReservationJpaEntity();
        expiredEntity1.setReservationId("EXP001");
        expiredEntity1.setPolicyId(101L);
        expiredEntity1.setUserId(1001L);
        expiredEntity1.setStatus(ReservationStatus.PENDING);
        expiredEntity1.setExpiresAt(now.minusMinutes(5));

        CouponReservationJpaEntity expiredEntity2 = new CouponReservationJpaEntity();
        expiredEntity2.setReservationId("EXP002");
        expiredEntity2.setPolicyId(102L);
        expiredEntity2.setUserId(1002L);
        expiredEntity2.setStatus(ReservationStatus.PENDING);
        expiredEntity2.setExpiresAt(now.minusMinutes(10));

        List<CouponReservationJpaEntity> expiredEntities = Arrays.asList(expiredEntity1, expiredEntity2);

        CouponReservation expiredReservation1 = CouponReservation.builder()
                .reservationId("EXP001")
                .policyId(101L)
                .userId(1001L)
                .status(ReservationStatus.PENDING)
                .expiresAt(now.minusMinutes(5))
                .build();

        CouponReservation expiredReservation2 = CouponReservation.builder()
                .reservationId("EXP002")
                .policyId(102L)
                .userId(1002L)
                .status(ReservationStatus.PENDING)
                .expiresAt(now.minusMinutes(10))
                .build();

        when(reservationRepository.findExpiredReservations(ReservationStatus.PENDING, now))
                .thenReturn(expiredEntities);
        when(reservationMapper.toDomain(expiredEntity1)).thenReturn(expiredReservation1);
        when(reservationMapper.toDomain(expiredEntity2)).thenReturn(expiredReservation2);

        // when
        List<CouponReservation> result = adapter.findExpiredReservations(now);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReservationId()).isEqualTo("EXP001");
        assertThat(result.get(1).getReservationId()).isEqualTo("EXP002");

        verify(reservationRepository).findExpiredReservations(ReservationStatus.PENDING, now);
        verify(reservationMapper, times(2)).toDomain(any(CouponReservationJpaEntity.class));
    }

    @Test
    @DisplayName("만료된 예약 목록 조회 - 빈 목록")
    void findExpiredReservations_EmptyList() {
        // given
        LocalDateTime now = LocalDateTime.now();
        when(reservationRepository.findExpiredReservations(ReservationStatus.PENDING, now))
                .thenReturn(Arrays.asList());

        // when
        List<CouponReservation> result = adapter.findExpiredReservations(now);

        // then
        assertThat(result).isEmpty();
        verify(reservationRepository).findExpiredReservations(ReservationStatus.PENDING, now);
        verify(reservationMapper, never()).toDomain(any());
    }

    @Test
    @DisplayName("다양한 상태의 쿠폰 예약 저장")
    void save_VariousStatus() {
        // given - CONFIRMED 상태
        CouponReservation confirmedReservation = CouponReservation.builder()
                .reservationId("RES_CONFIRMED")
                .policyId(200L)
                .userId(2000L)
                .status(ReservationStatus.CONFIRMED)
                .confirmedAt(LocalDateTime.now())
                .build();

        CouponReservationJpaEntity confirmedEntity = new CouponReservationJpaEntity();
        confirmedEntity.setReservationId("RES_CONFIRMED");
        confirmedEntity.setStatus(ReservationStatus.CONFIRMED);

        when(reservationMapper.toJpaEntity(confirmedReservation)).thenReturn(confirmedEntity);
        when(reservationRepository.save(confirmedEntity)).thenReturn(confirmedEntity);
        when(reservationMapper.toDomain(confirmedEntity)).thenReturn(confirmedReservation);

        // when
        CouponReservation result = adapter.save(confirmedReservation);

        // then
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(confirmedEntity);
    }

    @Test
    @DisplayName("금액 정보가 있는 예약 저장")
    void save_WithAmountInfo() {
        // given
        CouponReservation reservationWithAmount = CouponReservation.builder()
                .reservationId("RES_AMOUNT")
                .policyId(300L)
                .userId(3000L)
                .orderId("ORD_AMOUNT")
                .orderAmount(100000L)
                .paymentAmount(90000L)
                .discountAmount(10000L)
                .status(ReservationStatus.PENDING)
                .build();

        CouponReservationJpaEntity entityWithAmount = new CouponReservationJpaEntity();
        entityWithAmount.setReservationId("RES_AMOUNT");
        entityWithAmount.setOrderAmount(100000L);
        entityWithAmount.setPaymentAmount(90000L);
        entityWithAmount.setDiscountAmount(10000L);

        when(reservationMapper.toJpaEntity(reservationWithAmount)).thenReturn(entityWithAmount);
        when(reservationRepository.save(entityWithAmount)).thenReturn(entityWithAmount);
        when(reservationMapper.toDomain(entityWithAmount)).thenReturn(reservationWithAmount);

        // when
        CouponReservation result = adapter.save(reservationWithAmount);

        // then
        assertThat(result.getOrderAmount()).isEqualTo(100000L);
        assertThat(result.getPaymentAmount()).isEqualTo(90000L);
        assertThat(result.getDiscountAmount()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("시간 정보가 있는 예약 조회")
    void findById_WithTimeInfo() {
        // given
        LocalDateTime reservedAt = LocalDateTime.of(2024, 12, 1, 10, 0, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2024, 12, 1, 10, 10, 0);

        CouponReservationJpaEntity entityWithTime = new CouponReservationJpaEntity();
        entityWithTime.setReservationId("RES_TIME");
        entityWithTime.setReservedAt(reservedAt);
        entityWithTime.setExpiresAt(expiresAt);

        CouponReservation reservationWithTime = CouponReservation.builder()
                .reservationId("RES_TIME")
                .reservedAt(reservedAt)
                .expiresAt(expiresAt)
                .build();

        when(reservationRepository.findByReservationId("RES_TIME"))
                .thenReturn(Optional.of(entityWithTime));
        when(reservationMapper.toDomain(entityWithTime)).thenReturn(reservationWithTime);

        // when
        Optional<CouponReservation> result = adapter.findById("RES_TIME");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getReservedAt()).isEqualTo(reservedAt);
        assertThat(result.get().getExpiresAt()).isEqualTo(expiresAt);
    }
}
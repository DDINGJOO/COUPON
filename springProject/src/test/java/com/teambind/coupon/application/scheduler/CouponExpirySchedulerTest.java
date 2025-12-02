package com.teambind.coupon.application.scheduler;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.domain.model.CouponStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 쿠폰 만료 스케줄러 테스트
 */
@ExtendWith(MockitoExtension.class)
class CouponExpirySchedulerTest {

    @InjectMocks
    private CouponExpiryScheduler scheduler;

    @Mock
    private CouponIssueRepository couponIssueRepository;

    @BeforeEach
    void setUp() {
        // 배치 크기와 스케줄러 활성화 설정
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
    }

    @Test
    @DisplayName("만료된 쿠폰들이 배치로 처리되어야 한다")
    void processExpiredCoupons_shouldProcessInBatches() {
        // given
        LocalDateTime now = LocalDateTime.now();
        List<CouponIssueEntity> expiredCoupons = createExpiredCoupons(25); // 25개 쿠폰

        // 3개의 페이지로 분할 (10, 10, 5)
        Page<CouponIssueEntity> page1 = new PageImpl<>(
                expiredCoupons.subList(0, 10),
                PageRequest.of(0, 10),
                25
        );
        Page<CouponIssueEntity> page2 = new PageImpl<>(
                expiredCoupons.subList(10, 20),
                PageRequest.of(1, 10),
                25
        );
        Page<CouponIssueEntity> page3 = new PageImpl<>(
                expiredCoupons.subList(20, 25),
                PageRequest.of(2, 10),
                25
        );

        when(couponIssueRepository.findExpiredCouponsWithPaging(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page1, page2, page3);

        when(couponIssueRepository.updateToExpiredBatch(anyList(), any(LocalDateTime.class)))
                .thenReturn(10, 10, 5);

        // when
        scheduler.processExpiredCoupons();

        // then
        verify(couponIssueRepository, times(3))
                .findExpiredCouponsWithPaging(any(LocalDateTime.class), any(Pageable.class));
        verify(couponIssueRepository, times(3))
                .updateToExpiredBatch(anyList(), any(LocalDateTime.class));

        // 배치 업데이트에 전달된 ID 검증
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(couponIssueRepository, times(3))
                .updateToExpiredBatch(idsCaptor.capture(), any(LocalDateTime.class));

        List<List<Long>> capturedIds = idsCaptor.getAllValues();
        assertThat(capturedIds.get(0)).hasSize(10); // 첫 번째 배치
        assertThat(capturedIds.get(1)).hasSize(10); // 두 번째 배치
        assertThat(capturedIds.get(2)).hasSize(5);  // 세 번째 배치
    }

    @Test
    @DisplayName("스케줄러가 비활성화되면 처리하지 않아야 한다")
    void processExpiredCoupons_shouldNotProcessWhenDisabled() {
        // given
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", false);

        // when
        scheduler.processExpiredCoupons();

        // then
        verify(couponIssueRepository, never())
                .findExpiredCouponsWithPaging(any(), any());
        verify(couponIssueRepository, never())
                .updateToExpiredBatch(anyList(), any());
    }

    @Test
    @DisplayName("빈 페이지는 처리하지 않아야 한다")
    void processExpiredCoupons_shouldHandleEmptyPages() {
        // given
        Page<CouponIssueEntity> emptyPage = Page.empty();

        when(couponIssueRepository.findExpiredCouponsWithPaging(any(), any()))
                .thenReturn(emptyPage);

        // when
        scheduler.processExpiredCoupons();

        // then
        verify(couponIssueRepository, times(1))
                .findExpiredCouponsWithPaging(any(), any());
        verify(couponIssueRepository, never())
                .updateToExpiredBatch(anyList(), any());
    }

    @Test
    @DisplayName("수동 실행이 정상 동작해야 한다")
    void processExpiredCouponsManually_shouldWork() {
        // given
        List<CouponIssueEntity> expiredCoupons = createExpiredCoupons(5);
        Page<CouponIssueEntity> page = new PageImpl<>(expiredCoupons);

        when(couponIssueRepository.findExpiredCouponsWithPaging(any(), any()))
                .thenReturn(page);
        when(couponIssueRepository.updateToExpiredBatch(anyList(), any()))
                .thenReturn(5);

        // when
        int processedCount = scheduler.processExpiredCouponsManually();

        // then
        assertThat(processedCount).isEqualTo(5);
        verify(couponIssueRepository).findExpiredCouponsWithPaging(any(), any());
        verify(couponIssueRepository).updateToExpiredBatch(anyList(), any());
    }

    @Test
    @DisplayName("예약 타임아웃 처리가 정상 동작해야 한다")
    void processReservationTimeouts_shouldRollbackTimedOutReservations() {
        // given
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(31);
        List<CouponIssueEntity> timeoutReservations = IntStream.range(1, 6)
                .mapToObj(i -> {
                    CouponIssueEntity entity = CouponIssueEntity.builder()
                            .id((long) i)
                            .status(CouponStatus.RESERVED)
                            .reservationId("RESV-" + i)
                            .reservedAt(thirtyMinutesAgo)
                            .build();
                    return entity;
                })
                .collect(Collectors.toList());

        when(couponIssueRepository.findTimeoutReservations(any(LocalDateTime.class)))
                .thenReturn(timeoutReservations);

        // when
        scheduler.processReservationTimeouts();

        // then
        verify(couponIssueRepository).findTimeoutReservations(any(LocalDateTime.class));
        verify(couponIssueRepository, times(5)).save(any(CouponIssueEntity.class));

        // 롤백 상태 검증
        ArgumentCaptor<CouponIssueEntity> entityCaptor = ArgumentCaptor.forClass(CouponIssueEntity.class);
        verify(couponIssueRepository, times(5)).save(entityCaptor.capture());

        List<CouponIssueEntity> capturedEntities = entityCaptor.getAllValues();
        capturedEntities.forEach(entity -> {
            assertThat(entity.getStatus()).isEqualTo(CouponStatus.ISSUED);
            assertThat(entity.getReservationId()).isNull();
            assertThat(entity.getReservedAt()).isNull();
        });
    }

    @Test
    @DisplayName("대량의 만료 쿠폰도 효율적으로 처리해야 한다")
    void processExpiredCoupons_shouldHandleLargeVolume() {
        // given
        int totalCoupons = 1000;
        int batchSize = 100;
        ReflectionTestUtils.setField(scheduler, "batchSize", batchSize);

        List<CouponIssueEntity> allCoupons = createExpiredCoupons(totalCoupons);

        // Mockito의 Answer를 사용하여 동적으로 페이지 반환
        when(couponIssueRepository.findExpiredCouponsWithPaging(any(LocalDateTime.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    int pageNumber = pageable.getPageNumber();
                    int pageSize = pageable.getPageSize();

                    int start = pageNumber * pageSize;
                    int end = Math.min(start + pageSize, totalCoupons);

                    if (start >= totalCoupons) {
                        return Page.empty();
                    }

                    return new PageImpl<>(
                            allCoupons.subList(start, end),
                            pageable,
                            totalCoupons
                    );
                });

        when(couponIssueRepository.updateToExpiredBatch(anyList(), any(LocalDateTime.class)))
                .thenReturn(batchSize);

        // when
        scheduler.processExpiredCoupons();

        // then
        // 1000개를 100개씩 나누면 10개 페이지가 나오고, 마지막에 빈 페이지를 확인하므로 총 10번 호출
        verify(couponIssueRepository, times(10))
                .findExpiredCouponsWithPaging(any(LocalDateTime.class), any(Pageable.class));
        verify(couponIssueRepository, times(10))
                .updateToExpiredBatch(anyList(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("스케줄러 상태 조회가 정상 동작해야 한다")
    void getStatus_shouldReturnCorrectStatus() {
        // given
        ReflectionTestUtils.setField(scheduler, "schedulerEnabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 50);

        // when
        CouponExpiryScheduler.SchedulerStatus status = scheduler.getStatus();

        // then
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getBatchSize()).isEqualTo(50);
        assertThat(status.getLastProcessedTime()).isNotNull();
    }

    /**
     * 테스트용 만료 쿠폰 생성
     */
    private List<CouponIssueEntity> createExpiredCoupons(int count) {
        LocalDateTime expiredTime = LocalDateTime.now().minusDays(1);

        return IntStream.range(1, count + 1)
                .mapToObj(i -> CouponIssueEntity.builder()
                        .id((long) i)
                        .policyId(1L)
                        .userId((long) (100 + i))
                        .status(CouponStatus.ISSUED)
                        .issuedAt(LocalDateTime.now().minusDays(30))
                        .expiresAt(expiredTime)
                        .build())
                .collect(Collectors.toList());
    }
}
package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.in.web.dto.CouponQueryRequest;
import com.teambind.coupon.adapter.in.web.dto.CouponQueryResponse;
import com.teambind.coupon.adapter.out.persistence.projection.CouponIssueProjection;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueQueryRepository;
import com.teambind.coupon.application.port.in.QueryUserCouponsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponQueryService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponQueryService 테스트")
class CouponQueryServiceTest {

    @Mock
    private CouponIssueQueryRepository couponIssueQueryRepository;

    @InjectMocks
    private CouponQueryService couponQueryService;

    private Long userId;
    private List<CouponIssueProjection> mockProjections;

    @BeforeEach
    void setUp() {
        userId = 100L;
        mockProjections = createMockProjections(5);
    }

    @Nested
    @DisplayName("유저 쿠폰 조회")
    class QueryUserCoupons {

        @Test
        @DisplayName("첫 페이지 조회 성공")
        void queryFirstPage() {
            // given
            CouponQueryRequest request = CouponQueryRequest.builder()
                    .cursor(null)
                    .limit(3)
                    .build();

            when(couponIssueQueryRepository.findUserCouponsWithCursor(
                    eq(userId), isNull(), isNull(), isNull(), eq(4)
            )).thenReturn(mockProjections.subList(0, 4));

            // when
            CouponQueryResponse response = couponQueryService.queryUserCoupons(userId, request);

            // then
            assertThat(response.getData()).hasSize(3);
            assertThat(response.isHasNext()).isTrue();
            assertThat(response.getNextCursor()).isEqualTo(3L);
            verify(couponIssueQueryRepository).findUserCouponsWithCursor(
                    userId, null, null, null, 4
            );
        }

        @Test
        @DisplayName("커서 기반 다음 페이지 조회")
        void queryNextPage() {
            // given
            CouponQueryRequest request = CouponQueryRequest.builder()
                    .cursor(10L)
                    .limit(3)
                    .build();

            when(couponIssueQueryRepository.findUserCouponsWithCursor(
                    eq(userId), isNull(), isNull(), eq(10L), eq(4)
            )).thenReturn(mockProjections.subList(0, 2));

            // when
            CouponQueryResponse response = couponQueryService.queryUserCoupons(userId, request);

            // then
            assertThat(response.getData()).hasSize(2);
            assertThat(response.isHasNext()).isFalse();
            assertThat(response.getNextCursor()).isNull();
        }

        @Test
        @DisplayName("상태 필터링 조회")
        void queryWithStatusFilter() {
            // given
            CouponQueryRequest request = CouponQueryRequest.builder()
                    .status(CouponQueryRequest.CouponStatusFilter.AVAILABLE)
                    .limit(10)
                    .build();

            when(couponIssueQueryRepository.findUserCouponsWithCursor(
                    eq(userId), eq("AVAILABLE"), isNull(), isNull(), eq(11)
            )).thenReturn(mockProjections);

            // when
            CouponQueryResponse response = couponQueryService.queryUserCoupons(userId, request);

            // then
            assertThat(response.getData()).hasSize(5);
            verify(couponIssueQueryRepository).findUserCouponsWithCursor(
                    userId, "AVAILABLE", null, null, 11
            );
        }

        @Test
        @DisplayName("상품ID 필터링 조회")
        void queryWithProductFilter() {
            // given
            List<Long> productIds = Arrays.asList(1L, 2L, 3L);
            CouponQueryRequest request = CouponQueryRequest.builder()
                    .productIds(productIds)
                    .limit(10)
                    .build();

            when(couponIssueQueryRepository.findUserCouponsWithCursor(
                    eq(userId), isNull(), eq("{1,2,3}"), isNull(), eq(11)
            )).thenReturn(mockProjections);

            // when
            CouponQueryResponse response = couponQueryService.queryUserCoupons(userId, request);

            // then
            assertThat(response.getData()).hasSize(5);
            verify(couponIssueQueryRepository).findUserCouponsWithCursor(
                    userId, null, "{1,2,3}", null, 11
            );
        }

        @Test
        @DisplayName("빈 결과 처리")
        void queryEmptyResult() {
            // given
            CouponQueryRequest request = CouponQueryRequest.builder()
                    .limit(10)
                    .build();

            when(couponIssueQueryRepository.findUserCouponsWithCursor(
                    anyLong(), any(), any(), any(), anyInt()
            )).thenReturn(new ArrayList<>());

            // when
            CouponQueryResponse response = couponQueryService.queryUserCoupons(userId, request);

            // then
            assertThat(response.getData()).isEmpty();
            assertThat(response.isHasNext()).isFalse();
            assertThat(response.getNextCursor()).isNull();
            assertThat(response.getCount()).isZero();
        }
    }

    @Nested
    @DisplayName("만료 임박 쿠폰 조회")
    class QueryExpiringCoupons {

        @Test
        @DisplayName("7일 이내 만료 쿠폰 조회")
        void queryExpiringCoupons() {
            // given
            when(couponIssueQueryRepository.findExpiringCoupons(
                    eq(userId), eq(7), eq(10)
            )).thenReturn(mockProjections.subList(0, 3));

            // when
            CouponQueryResponse response = couponQueryService.queryExpiringCoupons(userId, 7, 10);

            // then
            assertThat(response.getData()).hasSize(3);
            assertThat(response.isHasNext()).isFalse();
            verify(couponIssueQueryRepository).findExpiringCoupons(userId, 7, 10);
        }

        @Test
        @DisplayName("만료 임박 쿠폰이 없는 경우")
        void queryNoExpiringCoupons() {
            // given
            when(couponIssueQueryRepository.findExpiringCoupons(
                    eq(userId), anyInt(), anyInt()
            )).thenReturn(new ArrayList<>());

            // when
            CouponQueryResponse response = couponQueryService.queryExpiringCoupons(userId, 3, 10);

            // then
            assertThat(response.getData()).isEmpty();
            assertThat(response.getCount()).isZero();
        }
    }

    @Nested
    @DisplayName("쿠폰 통계 조회")
    class GetCouponStatistics {

        @Test
        @DisplayName("유저 쿠폰 통계 조회 성공")
        void getCouponStatisticsSuccess() {
            // given
            when(couponIssueQueryRepository.countUserCoupons(userId, null, null))
                    .thenReturn(100L);
            when(couponIssueQueryRepository.countUserCoupons(userId, "AVAILABLE", null))
                    .thenReturn(30L);
            when(couponIssueQueryRepository.countUserCoupons(userId, "USED", null))
                    .thenReturn(50L);
            when(couponIssueQueryRepository.countUserCoupons(userId, "EXPIRED", null))
                    .thenReturn(20L);
            when(couponIssueQueryRepository.findExpiringCoupons(userId, 7, Integer.MAX_VALUE))
                    .thenReturn(mockProjections.subList(0, 2));

            // when
            QueryUserCouponsUseCase.CouponStatistics statistics =
                    couponQueryService.getCouponStatistics(userId);

            // then
            assertThat(statistics.getTotalCoupons()).isEqualTo(100L);
            assertThat(statistics.getAvailableCoupons()).isEqualTo(30L);
            assertThat(statistics.getUsedCoupons()).isEqualTo(50L);
            assertThat(statistics.getExpiredCoupons()).isEqualTo(20L);
            assertThat(statistics.getExpiringCoupons()).isEqualTo(2L);

            verify(couponIssueQueryRepository, times(4)).countUserCoupons(any(), any(), any());
            verify(couponIssueQueryRepository).findExpiringCoupons(userId, 7, Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("쿠폰이 없는 유저의 통계")
        void getEmptyStatistics() {
            // given
            when(couponIssueQueryRepository.countUserCoupons(any(), any(), any()))
                    .thenReturn(0L);
            when(couponIssueQueryRepository.findExpiringCoupons(any(), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<>());

            // when
            QueryUserCouponsUseCase.CouponStatistics statistics =
                    couponQueryService.getCouponStatistics(userId);

            // then
            assertThat(statistics.getTotalCoupons()).isZero();
            assertThat(statistics.getAvailableCoupons()).isZero();
            assertThat(statistics.getUsedCoupons()).isZero();
            assertThat(statistics.getExpiredCoupons()).isZero();
            assertThat(statistics.getExpiringCoupons()).isZero();
        }
    }

    /**
     * 테스트용 Mock Projection 생성
     */
    private List<CouponIssueProjection> createMockProjections(int count) {
        List<CouponIssueProjection> projections = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            final int index = i;
            projections.add(new CouponIssueProjection() {
                @Override
                public Long getCouponIssueId() { return (long) index; }
                @Override
                public Long getUserId() { return userId; }
                @Override
                public Long getPolicyId() { return (long) (100 + index); }
                @Override
                public String getStatus() { return "ISSUED"; }
                @Override
                public LocalDateTime getIssuedAt() { return LocalDateTime.now().minusDays(index); }
                @Override
                public LocalDateTime getExpiresAt() { return LocalDateTime.now().plusDays(30 - index); }
                @Override
                public LocalDateTime getUsedAt() { return null; }
                @Override
                public LocalDateTime getReservedAt() { return null; }
                @Override
                public String getReservationId() { return null; }
                @Override
                public BigDecimal getActualDiscountAmount() { return null; }
                @Override
                public String getCouponName() { return "테스트 쿠폰 " + index; }
                @Override
                public String getCouponCode() { return "CODE" + index; }
                @Override
                public String getDescription() { return "테스트 쿠폰 설명 " + index; }
                @Override
                public String getDiscountType() { return "PERCENTAGE"; }
                @Override
                public BigDecimal getDiscountValue() { return BigDecimal.valueOf(10); }
                @Override
                public BigDecimal getMinimumOrderAmount() { return BigDecimal.valueOf(10000); }
                @Override
                public BigDecimal getMaxDiscountAmount() { return BigDecimal.valueOf(5000); }
                @Override
                public String getApplicableRule() { return "{\"applicableItemIds\":[1,2,3]}"; }
                @Override
                public String getDistributionType() { return "CODE"; }
                @Override
                public Boolean getIsAvailable() { return true; }
            });
        }
        return projections;
    }
}
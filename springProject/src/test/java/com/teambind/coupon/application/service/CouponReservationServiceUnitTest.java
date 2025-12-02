package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ReserveCouponCommand;
import com.teambind.coupon.application.port.in.ReserveCouponUseCase.CouponReservationResult;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponReservationService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponReservationService 단위 테스트")
class CouponReservationServiceUnitTest {

    @InjectMocks
    private CouponReservationService reservationService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    private ReserveCouponCommand command;
    private CouponIssue availableCoupon;
    private CouponPolicy couponPolicy;

    @BeforeEach
    void setUp() {
        // @Value 필드 직접 주입
        ReflectionTestUtils.setField(reservationService, "reservationTimeoutMinutes", 10);

        command = new ReserveCouponCommand("RESV-123", 100L, 1L);

        availableCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(29))
                .couponName("테스트 쿠폰")
                .discountPolicy(DiscountPolicy.builder()
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(5000))
                        .build())
                .build();

        couponPolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .currentIssueCount(new AtomicInteger(50))
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("쿠폰 예약 성공")
    void reserveCoupon_Success() {
        // given
        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(availableCoupon));
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(couponPolicy));
        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                .thenReturn(availableCoupon);

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReservationId()).isEqualTo("RESV-123");
        assertThat(result.getCouponId()).isEqualTo(1L);
        // 정책의 할인 정책에서 금액 가져옴
        assertThat(result.getDiscountAmount()).isNotNull();
        assertThat(result.getReservedUntil()).isNotNull();

        verify(saveCouponIssuePort).save(any(CouponIssue.class));
    }

    @Test
    @DisplayName("쿠폰 예약 실패 - 예약 ID가 없음")
    void reserveCoupon_InvalidReservationId() {
        // given
        ReserveCouponCommand invalidCommand = new ReserveCouponCommand(null, 100L, 1L);

        // when
        CouponReservationResult result = reservationService.reserveCoupon(invalidCommand);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("예약 ID가 유효하지 않음");

        verify(loadCouponIssuePort, never()).loadByIdAndUserId(anyLong(), anyLong());
        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 예약 실패 - 빈 예약 ID")
    void reserveCoupon_EmptyReservationId() {
        // given
        ReserveCouponCommand emptyCommand = new ReserveCouponCommand("  ", 100L, 1L);

        // when
        CouponReservationResult result = reservationService.reserveCoupon(emptyCommand);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("예약 ID가 유효하지 않음");

        verify(loadCouponIssuePort, never()).loadByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("쿠폰 예약 실패 - 쿠폰을 찾을 수 없음")
    void reserveCoupon_CouponNotFound() {
        // given
        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.empty());

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("쿠폰을 찾을 수 없음");

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 예약 - 멱등성 (이미 같은 ID로 예약됨)")
    void reserveCoupon_Idempotency() {
        // given
        CouponIssue alreadyReserved = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.RESERVED)
                .reservationId("RESV-123") // 같은 예약 ID
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(29))
                .build();

        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(alreadyReserved));

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("쿠폰이 이미 예약됨");
        assertThat(result.getReservedUntil()).isNotNull();

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 예약 실패 - 이미 사용된 쿠폰")
    void reserveCoupon_AlreadyUsed() {
        // given
        CouponIssue usedCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.USED)
                .usedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(usedCoupon));

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("예약할 수 없는 상태");

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 예약 실패 - 만료된 쿠폰")
    void reserveCoupon_Expired() {
        // given
        CouponIssue expiredCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.EXPIRED)
                .expiredAt(LocalDateTime.now().minusDays(1))
                .build();

        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(expiredCoupon));

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("예약할 수 없는 상태");

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 예약 실패 - 다른 예약 ID로 이미 예약됨")
    void reserveCoupon_AlreadyReservedByAnother() {
        // given
        CouponIssue reservedByAnother = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.RESERVED)
                .reservationId("OTHER-RESV") // 다른 예약 ID
                .reservedAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(reservedByAnother));

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("예약할 수 없는 상태");

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 예약 실패 - 정책을 찾을 수 없음")
    void reserveCoupon_PolicyNotFound_Failure() {
        // given
        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(availableCoupon));
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.empty()); // 정책 없음

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("쿠폰 정책을 찾을 수 없습니다");

        verify(saveCouponIssuePort, never()).save(any(CouponIssue.class));
    }

    @Test
    @DisplayName("쿠폰 예약 - 예약 시간 초과 설정")
    void reserveCoupon_ReservationTimeout() {
        // given
        ReflectionTestUtils.setField(reservationService, "reservationTimeoutMinutes", 30);

        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(availableCoupon));
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(couponPolicy));
        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                .thenReturn(availableCoupon);

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReservedUntil()).isAfter(LocalDateTime.now().plusMinutes(29));
        assertThat(result.getReservedUntil()).isBefore(LocalDateTime.now().plusMinutes(31));
    }

    @Test
    @DisplayName("쿠폰 예약 - 퍼센트 할인 쿠폰")
    void reserveCoupon_PercentageDiscount() {
        // given
        CouponIssue percentCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .discountPolicy(DiscountPolicy.builder()
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(BigDecimal.valueOf(10))
                        .maxDiscountAmount(BigDecimal.valueOf(10000))
                        .build())
                .build();

        CouponPolicy percentPolicy = CouponPolicy.builder()
                .id(10L)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .maxDiscountAmount(BigDecimal.valueOf(10000))
                .isActive(true)
                .build();

        when(loadCouponIssuePort.loadByIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(percentCoupon));
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(percentPolicy));
        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                .thenReturn(percentCoupon);

        // when
        CouponReservationResult result = reservationService.reserveCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        // 퍼센트 할인의 경우 정책의 할인 값이 반환됨
        assertThat(result.getDiscountAmount()).isNotNull();

        verify(saveCouponIssuePort).save(any(CouponIssue.class));
    }
}
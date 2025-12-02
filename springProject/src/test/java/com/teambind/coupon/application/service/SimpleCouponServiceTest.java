package com.teambind.coupon.application.service;

import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 쿠폰 서비스 간단 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 서비스 기본 테스트")
class SimpleCouponServiceTest {

    @InjectMocks
    private ApplyCouponService applyCouponService;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private CouponLockService couponLockService;

    private CouponApplyRequest request;
    private CouponIssue coupon;
    private CouponPolicy policy;

    @BeforeEach
    void setUp() {
        request = CouponApplyRequest.builder()
                .reservationId("RESV-123")
                .userId(100L)
                .couponId(1L)
                .orderAmount(BigDecimal.valueOf(50000))
                .build();

        coupon = CouponIssue.builder()
                .id(1L)
                .policyId(1L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        policy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .build();
    }

    @Test
    @DisplayName("쿠폰 적용 성공")
    void applyCoupon_Success() {
        // given
        when(loadCouponIssuePort.findById(1L))
                .thenReturn(Optional.of(coupon));
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(policy));

        CouponApplyResponse mockResponse = CouponApplyResponse.builder()
                .couponId("1")
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .build();

        when(couponLockService.tryLockAndApplyCoupon(any(), any()))
                .thenReturn(mockResponse);

        // when
        CouponApplyResponse response = applyCouponService.applyCoupon(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getCouponId()).isEqualTo("1");
        assertThat(response.getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getDiscountValue()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("쿠폰을 찾을 수 없을 때 예외 발생")
    void applyCoupon_CouponNotFound() {
        // given
        when(loadCouponIssuePort.findById(1L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(request))
                .isInstanceOf(com.teambind.coupon.domain.exception.CouponNotFoundException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("다른 사용자의 쿠폰 사용 시 예외 발생")
    void applyCoupon_UnauthorizedAccess() {
        // given
        CouponIssue otherUserCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(1L)
                .userId(999L) // 다른 사용자
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        when(loadCouponIssuePort.findById(1L))
                .thenReturn(Optional.of(otherUserCoupon));

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(request))
                .isInstanceOf(com.teambind.coupon.domain.exception.UnauthorizedCouponAccessException.class)
                .hasMessageContaining("해당 쿠폰에 대한 권한이 없습니다");
    }

    @Test
    @DisplayName("최소 주문 금액 미달 시 예외 발생")
    void applyCoupon_MinimumOrderNotMet() {
        // given
        CouponApplyRequest lowAmountRequest = CouponApplyRequest.builder()
                .reservationId("RESV-123")
                .userId(100L)
                .couponId(1L)
                .orderAmount(BigDecimal.valueOf(5000)) // 최소 금액 미달
                .build();

        CouponPolicy highMinimumPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .minimumOrderAmount(BigDecimal.valueOf(10000))
                .build();

        when(loadCouponIssuePort.findById(1L))
                .thenReturn(Optional.of(coupon));
        when(loadCouponPolicyPort.loadById(1L))
                .thenReturn(Optional.of(highMinimumPolicy));

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(lowAmountRequest))
                .isInstanceOf(com.teambind.coupon.domain.exception.MinimumOrderNotMetException.class)
                .hasMessageContaining("최소 주문 금액을 충족하지 않습니다");
    }

    @Test
    @DisplayName("이미 사용된 쿠폰 적용 시 예외 발생")
    void applyCoupon_AlreadyUsed() {
        // given
        CouponIssue usedCoupon = CouponIssue.builder()
                .id(1L)
                .policyId(1L)
                .userId(100L)
                .status(CouponStatus.USED) // 이미 사용됨
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiredAt(LocalDateTime.now().plusDays(30))
                .usedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(loadCouponIssuePort.findById(1L))
                .thenReturn(Optional.of(usedCoupon));

        // when & then
        assertThatThrownBy(() -> applyCouponService.applyCoupon(request))
                .isInstanceOf(com.teambind.coupon.domain.exception.CouponAlreadyUsedException.class)
                .hasMessageContaining("사용 가능한 상태가 아닙니다");
    }
}
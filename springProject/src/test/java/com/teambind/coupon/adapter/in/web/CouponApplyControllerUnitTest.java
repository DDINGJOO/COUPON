package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.in.ApplyCouponUseCase;
import com.teambind.coupon.domain.model.DiscountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CouponApplyController 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponApplyController 단위 테스트")
class CouponApplyControllerUnitTest {

    @InjectMocks
    private CouponApplyController couponApplyController;

    @Mock
    private ApplyCouponUseCase applyCouponUseCase;

    private CouponApplyRequest request;
    private CouponApplyResponse successResponse;
    private CouponApplyResponse emptyResponse;

    @BeforeEach
    void setUp() {
        request = CouponApplyRequest.builder()
                .reservationId("RESV-123")
                .userId(100L)
                .couponId(1L)
                .orderAmount(BigDecimal.valueOf(50000))
                .build();

        successResponse = CouponApplyResponse.builder()
                .couponId("1")
                .couponName("테스트 쿠폰")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .maxDiscountAmount(null)
                .build();

        emptyResponse = CouponApplyResponse.empty();
    }

    @Test
    @DisplayName("쿠폰 적용 성공")
    void applyCoupon_Success() {
        // given
        when(applyCouponUseCase.applyCoupon(any(CouponApplyRequest.class)))
                .thenReturn(successResponse);

        // when
        ResponseEntity<CouponApplyResponse> response = couponApplyController.applyCoupon(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCouponId()).isEqualTo("1");
        assertThat(response.getBody().getCouponName()).isEqualTo("테스트 쿠폰");
        assertThat(response.getBody().getDiscountValue()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("적용 가능한 쿠폰이 없는 경우")
    void applyCoupon_NoContent() {
        // given
        when(applyCouponUseCase.applyCoupon(any(CouponApplyRequest.class)))
                .thenReturn(emptyResponse);

        // when
        ResponseEntity<CouponApplyResponse> response = couponApplyController.applyCoupon(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("퍼센트 할인 쿠폰 적용")
    void applyCoupon_PercentageDiscount() {
        // given
        CouponApplyResponse percentResponse = CouponApplyResponse.builder()
                .couponId("2")
                .couponName("20% 할인 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .maxDiscountAmount(BigDecimal.valueOf(10000))
                .build();

        when(applyCouponUseCase.applyCoupon(any(CouponApplyRequest.class)))
                .thenReturn(percentResponse);

        // when
        ResponseEntity<CouponApplyResponse> response = couponApplyController.applyCoupon(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(response.getBody().getDiscountValue()).isEqualByComparingTo("20");
        assertThat(response.getBody().getMaxDiscountAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("쿠폰 락 해제 성공")
    void releaseCouponLock_Success() {
        // given
        String reservationId = "RESV-123";
        doNothing().when(applyCouponUseCase).releaseCouponLock(reservationId);

        // when
        ResponseEntity<Void> response = couponApplyController.releaseCouponLock(reservationId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applyCouponUseCase).releaseCouponLock(eq(reservationId));
    }

    @Test
    @DisplayName("최대 할인 금액이 적용된 쿠폰")
    void applyCoupon_WithMaxDiscount() {
        // given
        CouponApplyResponse maxDiscountResponse = CouponApplyResponse.builder()
                .couponId("3")
                .couponName("VIP 쿠폰")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(30))
                .maxDiscountAmount(BigDecimal.valueOf(10000))
                .build();

        when(applyCouponUseCase.applyCoupon(any(CouponApplyRequest.class)))
                .thenReturn(maxDiscountResponse);

        // when
        ResponseEntity<CouponApplyResponse> response = couponApplyController.applyCoupon(request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMaxDiscountAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("여러 예약 ID에 대한 락 해제")
    void releaseCouponLock_Multiple() {
        // given
        String[] reservationIds = {"RESV-001", "RESV-002", "RESV-003"};

        for (String id : reservationIds) {
            doNothing().when(applyCouponUseCase).releaseCouponLock(id);
        }

        // when & then
        for (String id : reservationIds) {
            ResponseEntity<Void> response = couponApplyController.releaseCouponLock(id);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(applyCouponUseCase).releaseCouponLock(eq(id));
        }
    }

}
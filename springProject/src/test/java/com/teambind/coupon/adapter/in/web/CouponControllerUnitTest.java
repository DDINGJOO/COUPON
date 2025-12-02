package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.adapter.in.web.dto.CouponIssueResponse;
import com.teambind.coupon.adapter.in.web.dto.DirectIssueRequest;
import com.teambind.coupon.adapter.in.web.dto.DirectIssueResponse;
import com.teambind.coupon.adapter.in.web.dto.DownloadCouponRequest;
import com.teambind.coupon.adapter.in.web.dto.ReserveCouponRequest;
import com.teambind.coupon.adapter.in.web.dto.ReserveCouponResponse;
import com.teambind.coupon.application.port.in.*;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase.DirectIssueResult;
import com.teambind.coupon.application.port.in.ReserveCouponUseCase.CouponReservationResult;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CouponController 단위 테스트
 * Spring Context 없이 순수 단위 테스트로 진행
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponController 단위 테스트")
class CouponControllerUnitTest {

    @InjectMocks
    private CouponController couponController;

    @Mock
    private DownloadCouponUseCase downloadCouponUseCase;

    @Mock
    private ReserveCouponUseCase reserveCouponUseCase;

    @Mock
    private DirectIssueCouponUseCase directIssueCouponUseCase;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    private DownloadCouponRequest downloadRequest;
    private ReserveCouponRequest reserveRequest;
    private DirectIssueRequest directIssueRequest;
    private CouponIssue couponIssue;
    private CouponPolicy couponPolicy;

    @BeforeEach
    void setUp() {
        downloadRequest = new DownloadCouponRequest("TEST2024", 100L);

        reserveRequest = new ReserveCouponRequest("RESV-123", 100L, 1L);

        directIssueRequest = DirectIssueRequest.builder()
                .couponPolicyId(10L)
                .userIds(List.of(100L, 101L, 102L))
                .issuedBy("admin")
                .build();

        couponIssue = CouponIssue.builder()
                .id(1L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        couponPolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("쿠폰 다운로드 성공")
    void downloadCoupon_Success() {
        // given
        when(downloadCouponUseCase.downloadCoupon(any(DownloadCouponCommand.class)))
                .thenReturn(couponIssue);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.of(couponPolicy));

        // when
        ResponseEntity<CouponIssueResponse> response = couponController.downloadCoupon(downloadRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCouponIssueId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("쿠폰 다운로드 - 정책 조회 실패시에도 응답")
    void downloadCoupon_PolicyNotFound() {
        // given
        when(downloadCouponUseCase.downloadCoupon(any(DownloadCouponCommand.class)))
                .thenReturn(couponIssue);
        when(loadCouponPolicyPort.loadById(10L))
                .thenReturn(Optional.empty());

        // when
        ResponseEntity<CouponIssueResponse> response = couponController.downloadCoupon(downloadRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCouponIssueId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("쿠폰 예약 성공")
    void reserveCoupon_Success() {
        // given
        CouponReservationResult successResult = CouponReservationResult.builder()
                .success(true)
                .reservationId("RESV-123")
                .couponId(1L)
                .message("예약 성공")
                .build();

        when(reserveCouponUseCase.reserveCoupon(any(ReserveCouponCommand.class)))
                .thenReturn(successResult);

        // when
        ResponseEntity<ReserveCouponResponse> response = couponController.reserveCoupon(reserveRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getReservationId()).isEqualTo("RESV-123");
    }

    @Test
    @DisplayName("쿠폰 예약 실패")
    void reserveCoupon_Failed() {
        // given
        CouponReservationResult failedResult = CouponReservationResult.builder()
                .success(false)
                .reservationId("RESV-123")
                .message("쿠폰이 이미 사용됨")
                .build();

        when(reserveCouponUseCase.reserveCoupon(any(ReserveCouponCommand.class)))
                .thenReturn(failedResult);

        // when
        ResponseEntity<ReserveCouponResponse> response = couponController.reserveCoupon(reserveRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("쿠폰이 이미 사용됨");
    }

    @Test
    @DisplayName("쿠폰 직접 발급 - 전체 성공")
    void directIssueCoupons_FullSuccess() {
        // given
        DirectIssueResult fullSuccessResult = DirectIssueResult.builder()
                        .requestedCount(3)
                        .successCount(3)
                        .failedCount(0)
                        .failedUserIds(List.of())
                        .issuedCoupons(List.of())
                        .failures(List.of())
                        .success(true)
                        .build();

        when(directIssueCouponUseCase.directIssue(any(DirectIssueCouponCommand.class)))
                .thenReturn(fullSuccessResult);

        // when
        ResponseEntity<DirectIssueResponse> response = couponController.directIssueCoupons(directIssueRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccessCount()).isEqualTo(3);
        assertThat(response.getBody().getFailedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("쿠폰 직접 발급 - 부분 성공")
    void directIssueCoupons_PartialSuccess() {
        // given
        DirectIssueResult partialResult = DirectIssueResult.builder()
                        .requestedCount(3)
                        .successCount(2)
                        .failedCount(1)
                        .failedUserIds(List.of(102L))
                        .issuedCoupons(List.of())
                        .failures(List.of())
                        .success(false)
                        .build();

        when(directIssueCouponUseCase.directIssue(any(DirectIssueCouponCommand.class)))
                .thenReturn(partialResult);

        // when
        ResponseEntity<DirectIssueResponse> response = couponController.directIssueCoupons(directIssueRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccessCount()).isEqualTo(2);
        assertThat(response.getBody().getFailedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 직접 발급 - 전체 실패")
    void directIssueCoupons_AllFailed() {
        // given
        DirectIssueResult failedResult = DirectIssueResult.builder()
                        .requestedCount(3)
                        .successCount(0)
                        .failedCount(3)
                        .failedUserIds(List.of(100L, 101L, 102L))
                        .issuedCoupons(List.of())
                        .failures(List.of())
                        .success(false)
                        .build();

        when(directIssueCouponUseCase.directIssue(any(DirectIssueCouponCommand.class)))
                .thenReturn(failedResult);

        // when
        ResponseEntity<DirectIssueResponse> response = couponController.directIssueCoupons(directIssueRequest);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccessCount()).isEqualTo(0);
        assertThat(response.getBody().getFailedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("쿠폰 코드 유효성 검증 - 유효한 쿠폰")
    void validateCouponCode_Valid() {
        // given
        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(couponPolicy));

        // when
        ResponseEntity<CouponController.CouponValidationResponse> response =
                couponController.validateCouponCode("TEST2024");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isValid()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("사용 가능한 쿠폰입니다");
    }

    @Test
    @DisplayName("쿠폰 코드 유효성 검증 - 유효하지 않은 쿠폰")
    void validateCouponCode_Invalid() {
        // given
        when(loadCouponPolicyPort.loadByCodeAndActive("INVALID"))
                .thenReturn(Optional.empty());

        // when
        ResponseEntity<CouponController.CouponValidationResponse> response =
                couponController.validateCouponCode("INVALID");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isValid()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("사용할 수 없는 쿠폰입니다");
    }
}
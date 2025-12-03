package com.teambind.coupon.common.exceptions;

import com.teambind.coupon.domain.exception.CouponDomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler 테스트")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/coupons");
        when(webRequest.getDescription(false)).thenReturn("uri=/api/coupons");
    }

    @Test
    @DisplayName("쿠폰을 찾을 수 없음 예외 처리")
    void handleCouponNotFound() {
        // given
        CouponDomainException.CouponNotFound exception =
                new CouponDomainException.CouponNotFound("쿠폰을 찾을 수 없습니다: 123");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleCouponDomainException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COUPON_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("쿠폰을 찾을 수 없습니다: 123");
        assertThat(response.getBody().getPath()).isEqualTo("/api/coupons");
    }

    @Test
    @DisplayName("쿠폰 이미 사용됨 예외 처리")
    void handleCouponAlreadyUsed() {
        // given
        CouponDomainException.CouponAlreadyUsed exception =
                new CouponDomainException.CouponAlreadyUsed("이미 사용된 쿠폰입니다");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleCouponDomainException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("COUPON_ALREADY_USED");
        assertThat(response.getBody().getMessage()).isEqualTo("이미 사용된 쿠폰입니다");
    }

    @Test
    @DisplayName("쿠폰 만료 예외 처리")
    void handleCouponExpired() {
        // given
        CouponDomainException.CouponExpired exception =
                new CouponDomainException.CouponExpired("만료된 쿠폰입니다");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleCouponDomainException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().getCode()).isEqualTo("COUPON_EXPIRED");
    }

    @Test
    @DisplayName("쿠폰 재고 소진 예외 처리")
    void handleCouponStockExhausted() {
        // given
        CouponDomainException.CouponStockExhausted exception =
                new CouponDomainException.CouponStockExhausted("쿠폰 재고가 소진되었습니다");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleCouponDomainException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("COUPON_STOCK_EXHAUSTED");
    }

    @Test
    @DisplayName("사용자 쿠폰 한도 초과 예외 처리")
    void handleUserCouponLimitExceeded() {
        // given
        CouponDomainException.UserCouponLimitExceeded exception =
                new CouponDomainException.UserCouponLimitExceeded("쿠폰 발급 한도를 초과했습니다");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleCouponDomainException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo("USER_COUPON_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("일반 쿠폰 도메인 예외 처리")
    void handleGeneralCouponDomainException() {
        // given
        CouponDomainException exception = mock(CouponDomainException.class);
        when(exception.getMessage()).thenReturn("일반 쿠폰 오류");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleCouponDomainException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("COUPON_ERROR");
    }

    @Test
    @DisplayName("CustomException 처리")
    void handleCustomException() {
        // given
        CustomException exception = new CustomException(
                ExceptionType.VALIDATION_ERROR,
                "유효성 검증 실패"
        );

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handlePlaceException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ExceptionType.VALIDATION_ERROR.name());
    }

    @Test
    @DisplayName("MethodArgumentNotValid 예외 처리")
    void handleMethodArgumentNotValid() {
        // given
        BindingResult bindingResult = mock(BindingResult.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        List<FieldError> fieldErrors = Arrays.asList(
                new FieldError("couponDto", "couponCode", "쿠폰 코드는 필수입니다"),
                new FieldError("couponDto", "discountAmount", "할인 금액은 양수여야 합니다")
        );

        when(bindingResult.getFieldErrorCount()).thenReturn(2);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);

        // when
        ResponseEntity<Object> response = exceptionHandler.handleMethodArgumentNotValid(
                exception,
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);

        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertThat(errorResponse.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(errorResponse.getMessage()).isEqualTo("입력값 검증에 실패했습니다.");
        assertThat(errorResponse.getPath()).isEqualTo("/api/coupons");
    }

    @Test
    @DisplayName("일반 예외 처리")
    void handleGeneralException() {
        // given
        Exception exception = new RuntimeException("예상치 못한 오류");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleGeneralException(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getErrCode());
        assertThat(response.getBody().getMessage()).isEqualTo("서버 내부 오류가 발생했습니다.");
        assertThat(response.getBody().getPath()).isEqualTo("/api/coupons");
    }

    @Test
    @DisplayName("다양한 요청 URI 처리")
    void handleVariousRequestURIs() {
        // given
        when(request.getRequestURI()).thenReturn("/api/v1/coupons/download");
        CouponDomainException.CouponNotFound exception =
                new CouponDomainException.CouponNotFound("Not found");

        // when
        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleCouponDomainException(exception, request);

        // then
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/coupons/download");
    }

    @Test
    @DisplayName("WebRequest 경로 추출")
    void extractPathFromWebRequest() {
        // given
        when(webRequest.getDescription(false)).thenReturn("uri=/api/v2/coupons/apply");

        BindingResult bindingResult = mock(BindingResult.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        when(bindingResult.getFieldErrorCount()).thenReturn(0);
        when(bindingResult.getFieldErrors()).thenReturn(Arrays.asList());

        // when
        ResponseEntity<Object> response = exceptionHandler.handleMethodArgumentNotValid(
                exception,
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest
        );

        // then
        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertThat(errorResponse.getPath()).isEqualTo("/api/v2/coupons/apply");
    }
}
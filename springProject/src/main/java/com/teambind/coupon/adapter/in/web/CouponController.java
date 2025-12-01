package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.adapter.in.web.dto.CouponIssueResponse;
import com.teambind.coupon.adapter.in.web.dto.DirectIssueRequest;
import com.teambind.coupon.adapter.in.web.dto.DirectIssueResponse;
import com.teambind.coupon.adapter.in.web.dto.DownloadCouponRequest;
import com.teambind.coupon.adapter.in.web.dto.ReserveCouponRequest;
import com.teambind.coupon.adapter.in.web.dto.ReserveCouponResponse;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase;
import com.teambind.coupon.application.port.in.DownloadCouponUseCase;
import com.teambind.coupon.application.port.in.ReserveCouponUseCase;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 쿠폰 컨트롤러
 * 쿠폰 다운로드, 예약 및 조회 API
 */
@Slf4j
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final DownloadCouponUseCase downloadCouponUseCase;
    private final ReserveCouponUseCase reserveCouponUseCase;
    private final DirectIssueCouponUseCase directIssueCouponUseCase;
    private final LoadCouponPolicyPort loadCouponPolicyPort;

    /**
     * 쿠폰 다운로드 API
     * CODE 타입 쿠폰을 사용자가 다운로드
     *
     * @param request 쿠폰 다운로드 요청
     * @return 발급된 쿠폰 정보
     */
    @PostMapping("/download")
    public ResponseEntity<CouponIssueResponse> downloadCoupon(
            @Valid @RequestBody DownloadCouponRequest request) {

        log.info("쿠폰 다운로드 요청 - userId: {}, couponCode: {}",
                request.getUserId(), request.getCouponCode());

        CouponIssue issued = downloadCouponUseCase.downloadCoupon(request.toCommand());

        // 쿠폰 정책에서 유효기간 조회
        CouponPolicy policy = loadCouponPolicyPort.loadById(issued.getPolicyId())
                .orElse(null);

        CouponIssueResponse response = CouponIssueResponse.from(
                issued,
                policy != null ? policy.getValidUntil() : null
        );

        log.info("쿠폰 다운로드 성공 - issueId: {}, userId: {}",
                issued.getId(), request.getUserId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 쿠폰 예약 API
     * 게이트웨이로부터 전달받은 예약 정보로 쿠폰을 예약 상태로 변경
     *
     * @param request 쿠폰 예약 요청
     * @return 예약 결과
     */
    @PostMapping("/reserve")
    public ResponseEntity<ReserveCouponResponse> reserveCoupon(
            @Valid @RequestBody ReserveCouponRequest request) {

        log.info("쿠폰 예약 요청 - reservationId: {}, userId: {}, couponId: {}",
                request.getReservationId(), request.getUserId(), request.getCouponId());

        ReserveCouponUseCase.CouponReservationResult result =
                reserveCouponUseCase.reserveCoupon(request.toCommand());

        ReserveCouponResponse response = ReserveCouponResponse.from(result);

        if (result.isSuccess()) {
            log.info("쿠폰 예약 성공 - reservationId: {}, couponId: {}",
                    request.getReservationId(), request.getCouponId());
            return ResponseEntity.ok(response);
        } else {
            log.warn("쿠폰 예약 실패 - reservationId: {}, message: {}",
                    request.getReservationId(), result.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    /**
     * 쿠폰 직접 발급 API (관리자용)
     * DIRECT 타입 쿠폰을 특정 사용자들에게 직접 발급
     *
     * @param request 직접 발급 요청
     * @return 발급 결과
     */
    @PostMapping("/direct-issue")
    public ResponseEntity<DirectIssueResponse> directIssueCoupons(
            @Valid @RequestBody DirectIssueRequest request) {

        log.info("쿠폰 직접 발급 요청 - policyId: {}, userCount: {}, issuedBy: {}",
                request.getCouponPolicyId(), request.getUserIds().size(), request.getIssuedBy());

        DirectIssueCouponUseCase.DirectIssueResult result =
                directIssueCouponUseCase.directIssue(request.toCommand());

        DirectIssueResponse response = DirectIssueResponse.from(result);

        if (result.isFullySuccessful()) {
            log.info("쿠폰 직접 발급 완전 성공 - 발급: {}/{}",
                    result.getSuccessCount(), result.getRequestedCount());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else if (result.isPartiallySuccessful()) {
            log.warn("쿠폰 직접 발급 부분 성공 - 성공: {}, 실패: {}",
                    result.getSuccessCount(), result.getFailedCount());
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
        } else {
            log.error("쿠폰 직접 발급 전체 실패 - 실패: {}", result.getFailedCount());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    /**
     * 쿠폰 코드 유효성 확인 API
     *
     * @param couponCode 확인할 쿠폰 코드
     * @return 쿠폰 코드 유효 여부
     */
    @GetMapping("/validate/{couponCode}")
    public ResponseEntity<CouponValidationResponse> validateCouponCode(
            @PathVariable String couponCode) {

        log.info("쿠폰 코드 유효성 확인 - couponCode: {}", couponCode);

        boolean isValid = loadCouponPolicyPort.loadByCodeAndActive(couponCode)
                .map(policy -> policy.isIssuable())
                .orElse(false);

        return ResponseEntity.ok(
                CouponValidationResponse.of(couponCode, isValid)
        );
    }

    /**
     * 쿠폰 코드 유효성 응답 DTO
     */
    @lombok.Value
    @lombok.Builder
    public static class CouponValidationResponse {
        String couponCode;
        boolean valid;
        String message;

        public static CouponValidationResponse of(String couponCode, boolean valid) {
            return CouponValidationResponse.builder()
                    .couponCode(couponCode)
                    .valid(valid)
                    .message(valid ? "사용 가능한 쿠폰입니다" : "사용할 수 없는 쿠폰입니다")
                    .build();
        }
    }
}
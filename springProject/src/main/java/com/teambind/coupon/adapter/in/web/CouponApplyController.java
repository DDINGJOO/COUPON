package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.in.ApplyCouponUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 쿠폰 적용 API 컨트롤러
 * 결제 전 쿠폰을 적용하고 락을 잡는 기능을 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/coupons/apply")
@RequiredArgsConstructor
public class CouponApplyController {

    private final ApplyCouponUseCase applyCouponUseCase;

    /**
     * 쿠폰 적용 및 락 획득
     *
     * @param request 쿠폰 적용 요청 (userId, productIds, orderAmount)
     * @return 적용 가능한 쿠폰 정보
     */
    @PostMapping
    public ResponseEntity<CouponApplyResponse> applyCoupon(
            @Valid @RequestBody CouponApplyRequest request) {

        log.info("쿠폰 적용 API 요청 - userId: {}, couponId: {}, orderAmount: {}",
                request.getUserId(), request.getCouponId(), request.getOrderAmount());

        CouponApplyResponse response = applyCouponUseCase.applyCoupon(request);

        if (response.isEmpty()) {
            log.info("적용 가능한 쿠폰이 없습니다 - userId: {}", request.getUserId());
            return ResponseEntity.noContent().build();
        }

        log.info("쿠폰 적용 성공 - couponId: {}",
                response.getCouponId());

        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 락 해제
     *
     * @param reservationId 예약 ID
     * @return 성공 응답
     */
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> releaseCouponLock(
            @PathVariable String reservationId) {

        log.info("쿠폰 락 해제 API 요청 - reservationId: {}", reservationId);

        applyCouponUseCase.releaseCouponLock(reservationId);

        log.info("쿠폰 락 해제 완료 - reservationId: {}", reservationId);

        return ResponseEntity.ok().build();
    }
}
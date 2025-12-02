package com.teambind.coupon.adapter.in.web;

import com.teambind.coupon.adapter.in.web.dto.UpdateCouponPolicyRemainingQuantityRequest;
import com.teambind.coupon.adapter.in.web.dto.UpdateCouponPolicyRemainingQuantityResponse;
import com.teambind.coupon.application.port.in.UpdateCouponPolicyRemainingQuantityCommand;
import com.teambind.coupon.application.port.in.UpdateCouponPolicyRemainingQuantityUseCase;
import com.teambind.coupon.application.service.CouponPolicyManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 쿠폰 정책 관리 컨트롤러
 * 생성된 쿠폰 정책의 제한적 수정 API
 */
@Slf4j
@RestController
@RequestMapping("/api/coupon-policies")
@RequiredArgsConstructor
public class CouponPolicyManagementController {

    private final UpdateCouponPolicyRemainingQuantityUseCase updateCouponPolicyRemainingQuantityUseCase;

    /**
     * 쿠폰 정책 남은 발급 수량 수정 API
     * 관리자 권한 필요
     *
     * @param policyId 수정할 쿠폰 정책 ID
     * @param request 수정 요청 정보
     * @return 수정 결과
     */
    @PatchMapping("/{policyId}/remaining-quantity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UpdateCouponPolicyRemainingQuantityResponse> updateRemainingQuantity(
            @PathVariable Long policyId,
            @Valid @RequestBody UpdateCouponPolicyRemainingQuantityRequest request) {

        log.info("쿠폰 정책 남은 발급 수량 수정 요청 - policyId: {}, newQuantity: {}, modifiedBy: {}",
                policyId, request.getNewMaxIssueCount(), request.getModifiedBy());

        // Command 객체 생성
        UpdateCouponPolicyRemainingQuantityCommand command = UpdateCouponPolicyRemainingQuantityCommand.builder()
                .couponPolicyId(policyId)
                .newMaxIssueCount(request.getNewMaxIssueCount())
                .modifiedBy(request.getModifiedBy())
                .reason(request.getReason())
                .build();

        // 서비스 호출
        CouponPolicyManagementService.UpdateResult result =
                updateCouponPolicyRemainingQuantityUseCase.updateRemainingQuantity(command);

        // 응답 생성
        UpdateCouponPolicyRemainingQuantityResponse response = UpdateCouponPolicyRemainingQuantityResponse.builder()
                .couponPolicyId(result.getCouponPolicyId())
                .previousMaxIssueCount(result.getPreviousMaxIssueCount())
                .newMaxIssueCount(result.getNewMaxIssueCount())
                .currentIssuedCount(result.getCurrentIssuedCount())
                .remainingCount(calculateRemainingCount(result.getNewMaxIssueCount(), result.getCurrentIssuedCount()))
                .success(result.isSuccess())
                .message(result.getMessage())
                .build();

        if (result.isSuccess()) {
            log.info("쿠폰 정책 남은 발급 수량 수정 성공 - policyId: {}, previous: {}, new: {}",
                    policyId, result.getPreviousMaxIssueCount(), result.getNewMaxIssueCount());
            return ResponseEntity.ok(response);
        } else {
            log.warn("쿠폰 정책 남은 발급 수량 수정 실패 - policyId: {}, message: {}",
                    policyId, result.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * 남은 발급 가능 수량 계산
     */
    private Integer calculateRemainingCount(Integer maxCount, Integer currentCount) {
        if (maxCount == null) {
            return null; // 무제한
        }
        return Math.max(0, maxCount - currentCount);
    }
}
package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.UpdateCouponPolicyRemainingQuantityCommand;
import com.teambind.coupon.application.port.in.UpdateCouponPolicyRemainingQuantityUseCase;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.common.annotation.DistributedLock;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 정책 관리 서비스
 * 생성된 쿠폰 정책의 제한적 수정 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponPolicyManagementService implements UpdateCouponPolicyRemainingQuantityUseCase {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponPolicyPort saveCouponPolicyPort;

    /**
     * 쿠폰 정책의 남은 발급 수량 업데이트
     * 생성된 쿠폰 정책의 유일한 수정 가능 필드
     *
     * @param command 수정 요청 커맨드
     * @return 수정 결과
     */
    @Override
    @Transactional
    @DistributedLock(key = "#command.couponPolicyId", prefix = "policy:update", waitTime = 10, leaseTime = 30)
    public UpdateResult updateRemainingQuantity(UpdateCouponPolicyRemainingQuantityCommand command) {
        log.info("쿠폰 정책 남은 발급 수량 수정 시작 - policyId: {}, newQuantity: {}, modifiedBy: {}",
                command.getCouponPolicyId(), command.getNewMaxIssueCount(), command.getModifiedBy());

        // 1. 쿠폰 정책 조회
        CouponPolicy policy = loadCouponPolicyPort.loadById(command.getCouponPolicyId())
                .orElseThrow(() -> new CouponDomainException.CouponNotFound(command.getCouponPolicyId()));

        // 2. 이전 값 저장 (변경 이력 추적용)
        Integer previousQuantity = policy.getMaxIssueCount();
        int currentIssuedCount = policy.getCurrentIssueCount().get();

        try {
            // 3. 도메인 모델에서 수량 업데이트 (검증 포함)
            policy.updateRemainingQuantity(command.getNewMaxIssueCount());

            // 4. 변경사항 저장
            saveCouponPolicyPort.save(policy);

            log.info("쿠폰 정책 남은 발급 수량 수정 완료 - policyId: {}, previous: {}, new: {}, currentIssued: {}",
                    command.getCouponPolicyId(), previousQuantity, command.getNewMaxIssueCount(), currentIssuedCount);

            return UpdateResult.builder()
                    .couponPolicyId(command.getCouponPolicyId())
                    .previousMaxIssueCount(previousQuantity)
                    .newMaxIssueCount(command.getNewMaxIssueCount())
                    .currentIssuedCount(currentIssuedCount)
                    .success(true)
                    .message("남은 발급 수량이 성공적으로 수정되었습니다.")
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("쿠폰 정책 수정 실패 - 잘못된 입력값: {}", e.getMessage());
            return UpdateResult.builder()
                    .couponPolicyId(command.getCouponPolicyId())
                    .previousMaxIssueCount(previousQuantity)
                    .newMaxIssueCount(command.getNewMaxIssueCount())
                    .currentIssuedCount(currentIssuedCount)
                    .success(false)
                    .message(e.getMessage())
                    .build();

        } catch (IllegalStateException e) {
            log.error("쿠폰 정책 수정 실패 - 상태 오류: {}", e.getMessage());
            return UpdateResult.builder()
                    .couponPolicyId(command.getCouponPolicyId())
                    .previousMaxIssueCount(previousQuantity)
                    .newMaxIssueCount(command.getNewMaxIssueCount())
                    .currentIssuedCount(currentIssuedCount)
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * 쿠폰 정책 수정 결과
     */
    @lombok.Value
    @lombok.Builder
    public static class UpdateResult {
        Long couponPolicyId;
        Integer previousMaxIssueCount;
        Integer newMaxIssueCount;
        Integer currentIssuedCount;
        boolean success;
        String message;
    }
}
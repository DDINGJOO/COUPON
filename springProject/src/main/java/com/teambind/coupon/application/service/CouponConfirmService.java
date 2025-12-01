package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ConfirmCouponUseCommand;
import com.teambind.coupon.application.port.in.ConfirmCouponUseUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.common.annotation.DistributedLock;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 쿠폰 사용 확정 서비스
 * 결제 완료 시 예약된 쿠폰을 사용 완료 상태로 변경
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponConfirmService implements ConfirmCouponUseUseCase {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;

    @Override
    @Transactional
    @DistributedLock(key = "#command.reservationId", prefix = "coupon:confirm", waitTime = 5, leaseTime = 10)
    public void confirmCouponUse(ConfirmCouponUseCommand command) {
        log.info("쿠폰 사용 확정 시작 - reservationId: {}, orderId: {}",
                command.getReservationId(), command.getOrderId());

        try {
            // 1. 예약 ID로 쿠폰 조회 (비관적 락)
            CouponIssue couponIssue = loadCouponIssuePort
                    .loadByReservationIdWithLock(command.getReservationId())
                    .orElseThrow(() -> new CouponDomainException(
                            "예약된 쿠폰을 찾을 수 없습니다: " + command.getReservationId()));

            // 2. 쿠폰 상태 확인 및 처리
            // RESERVED 상태 또는 타임아웃 후 ISSUED 상태 모두 처리 가능
            if (couponIssue.getStatus() == CouponStatus.RESERVED) {
                // 정상적인 예약 상태에서 사용 확정
                log.info("예약된 쿠폰 사용 확정 - couponId: {}, reservationId: {}",
                        couponIssue.getId(), command.getReservationId());
            } else if (couponIssue.getStatus() == CouponStatus.ISSUED &&
                       couponIssue.matchesReservation(command.getReservationId())) {
                // 타임아웃으로 롤백되었지만 같은 reservationId로 결제 완료된 경우
                log.info("타임아웃 후 결제 완료 - 쿠폰 복구 후 사용 처리 - couponId: {}, reservationId: {}",
                        couponIssue.getId(), command.getReservationId());
            } else if (couponIssue.getStatus() == CouponStatus.USED) {
                // 이미 사용된 경우 (중복 이벤트)
                log.warn("이미 사용된 쿠폰입니다 - couponId: {}, reservationId: {}",
                        couponIssue.getId(), command.getReservationId());
                return;
            } else {
                throw new CouponDomainException(
                        String.format("쿠폰을 사용할 수 없는 상태입니다. status: %s, reservationId match: %s",
                                couponIssue.getStatus(),
                                couponIssue.matchesReservation(command.getReservationId())));
            }

            // 3. 실제 할인 금액 계산 (필요시 결제 금액과 비교)
            BigDecimal actualDiscountAmount = calculateActualDiscount(
                    couponIssue, command.getPaymentAmount());

            // 4. 쿠폰 사용 확정
            couponIssue.confirmUsage(command.getOrderId(), actualDiscountAmount);

            // 5. 변경사항 저장
            saveCouponIssuePort.update(couponIssue);

            log.info("쿠폰 사용 확정 완료 - reservationId: {}, couponId: {}, orderId: {}, discountAmount: {}",
                    command.getReservationId(), couponIssue.getId(),
                    command.getOrderId(), actualDiscountAmount);

            // TODO: 쿠폰 사용 완료 이벤트 발행
            // publishCouponUsedEvent(couponIssue);

        } catch (CouponDomainException e) {
            log.warn("쿠폰 사용 확정 실패 - reservationId: {}, error: {}",
                    command.getReservationId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("쿠폰 사용 확정 중 예외 발생 - reservationId: {}, error: {}",
                    command.getReservationId(), e.getMessage(), e);
            throw new CouponDomainException("쿠폰 사용 확정 중 오류가 발생했습니다", e);
        }
    }


    /**
     * 실제 할인 금액 계산
     */
    private BigDecimal calculateActualDiscount(CouponIssue couponIssue, BigDecimal paymentAmount) {
        // 기본적으로 쿠폰의 할인 정책에 따라 계산
        // 실제로는 상품 정보와 함께 계산해야 함
        if (couponIssue.getDiscountPolicy() != null) {
            return couponIssue.getDiscountPolicy().calculateDiscountAmount(paymentAmount);
        }
        return BigDecimal.ZERO;
    }
}
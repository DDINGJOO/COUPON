package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ProcessPaymentUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentUseCaseService implements ProcessPaymentUseCase {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;

    @Override
    @Transactional
    public PaymentResult processPaymentCompleted(PaymentCompletedCommand command) {
        log.info("Payment completed processing - orderId: {}, reservationId: {}, userId: {}",
                command.getOrderId(), command.getReservationId(), command.getUserId());

        try {
            // Find coupon by reservation ID
            Optional<CouponIssue> couponOpt = loadCouponIssuePort.loadByReservationIdWithLock(command.getReservationId());

            if (couponOpt.isEmpty()) {
                log.warn("No coupon found for reservation: {}", command.getReservationId());
                return PaymentResult.failure(command.getReservationId(), "Coupon not found");
            }

            CouponIssue coupon = couponOpt.get();

            // Update coupon status to USED
            coupon.use(command.getOrderId(), command.getDiscountAmount());
            saveCouponIssuePort.save(coupon);

            log.info("Coupon usage completed - reservationId: {}, couponId: {}",
                    command.getReservationId(), coupon.getId());
            return PaymentResult.success(command.getReservationId(), command.getOrderId());

        } catch (Exception e) {
            log.error("Error processing payment completion - reservationId: {}", command.getReservationId(), e);
            return PaymentResult.failure(command.getReservationId(), "Error occurred during payment processing");
        }
    }

    @Override
    @Transactional
    public PaymentResult processPaymentFailed(PaymentFailedCommand command) {
        log.info("Payment failed processing - orderId: {}, reservationId: {}, userId: {}",
                command.getOrderId(), command.getReservationId(), command.getUserId());

        try {
            // Find coupon by reservation ID
            Optional<CouponIssue> couponOpt = loadCouponIssuePort.loadByReservationIdWithLock(command.getReservationId());

            if (couponOpt.isEmpty()) {
                log.warn("No coupon found for reservation: {}", command.getReservationId());
                return PaymentResult.failure(command.getReservationId(), "Coupon not found");
            }

            CouponIssue coupon = couponOpt.get();

            // Cancel reservation and revert status to ISSUED
            coupon.cancelReservation();
            saveCouponIssuePort.save(coupon);

            log.info("Coupon cancellation completed - reservationId: {}, couponId: {}",
                    command.getReservationId(), coupon.getId());
            return PaymentResult.success(command.getReservationId(), command.getOrderId());

        } catch (Exception e) {
            log.error("Error processing payment failure - reservationId: {}", command.getReservationId(), e);
            return PaymentResult.failure(command.getReservationId(), "Error occurred during payment failure processing");
        }
    }
}

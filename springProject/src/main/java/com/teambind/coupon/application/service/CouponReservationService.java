package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.ReserveCouponCommand;
import com.teambind.coupon.application.port.in.ReserveCouponUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.common.annotation.DistributedLock;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 예약 서비스
 * 결제 전 쿠폰을 예약 상태로 변경하는 비즈니스 로직 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponReservationService implements ReserveCouponUseCase {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final LoadCouponPolicyPort loadCouponPolicyPort;

    @Value("${coupon.reservation.timeout:10}")
    private int reservationTimeoutMinutes;

    @Override
    @Transactional
    @DistributedLock(key = "#command.userId + ':' + #command.couponId", prefix = "coupon:reserve", waitTime = 3, leaseTime = 5)
    public CouponReservationResult reserveCoupon(ReserveCouponCommand command) {
        log.info("쿠폰 예약 시작 - reservationId: {}, userId: {}, couponId: {}",
                command.getReservationId(), command.getUserId(), command.getCouponId());

        try {
            // 0. 예약 ID 유효성 검사
            if (command.getReservationId() == null || command.getReservationId().trim().isEmpty()) {
                return CouponReservationResult.builder()
                        .success(false)
                        .reservationId(command.getReservationId())
                        .couponId(command.getCouponId())
                        .discountAmount(BigDecimal.ZERO)
                        .message("예약 ID가 유효하지 않음")
                        .reservedUntil(null)
                        .build();
            }

            // 1. 쿠폰 조회
            CouponIssue couponIssue = loadCouponIssuePort
                    .loadByIdAndUserId(command.getCouponId(), command.getUserId())
                    .orElse(null);

            if (couponIssue == null) {
                return CouponReservationResult.builder()
                        .success(false)
                        .reservationId(command.getReservationId())
                        .couponId(command.getCouponId())
                        .discountAmount(BigDecimal.ZERO)
                        .message("쿠폰을 찾을 수 없음")
                        .reservedUntil(null)
                        .build();
            }

            // 2. 멱등성 체크 - 동일한 예약 ID로 이미 예약된 경우
            if (couponIssue.getStatus() == CouponStatus.RESERVED &&
                command.getReservationId().equals(couponIssue.getReservationId())) {
                log.info("쿠폰 이미 예약됨 (멱등성) - reservationId: {}", command.getReservationId());
                return CouponReservationResult.builder()
                        .success(true)
                        .reservationId(command.getReservationId())
                        .couponId(command.getCouponId())
                        .discountAmount(BigDecimal.ZERO)
                        .message("쿠폰이 이미 예약됨")
                        .reservedUntil(LocalDateTime.now().plusMinutes(reservationTimeoutMinutes))
                        .build();
            }

            // 3. 쿠폰 상태 검증
            String validationError = validateCouponForReservation(couponIssue);
            if (validationError != null) {
                return CouponReservationResult.builder()
                        .success(false)
                        .reservationId(command.getReservationId())
                        .couponId(command.getCouponId())
                        .discountAmount(BigDecimal.ZERO)
                        .message(validationError)
                        .reservedUntil(null)
                        .build();
            }

            // 4. 쿠폰 정책 조회 (할인 금액 계산을 위해)
            CouponPolicy policy = loadCouponPolicyPort.loadById(couponIssue.getPolicyId())
                    .orElseThrow(() -> new CouponDomainException("쿠폰 정책을 찾을 수 없습니다"));

            // 5. 쿠폰 예약 처리
            couponIssue.reserve(command.getReservationId());

            // 6. 예약 정보 저장
            saveCouponIssuePort.save(couponIssue);

            // 7. 예약 만료 시간 계산
            LocalDateTime reservedUntil = LocalDateTime.now().plusMinutes(reservationTimeoutMinutes);

            // 8. 할인 금액 계산 (예시 - 실제로는 상품 가격 정보가 필요함)
            BigDecimal discountAmount = policy.getDiscountPolicy() != null
                    ? policy.getDiscountPolicy().getDiscountValue()
                    : BigDecimal.ZERO;

            log.info("쿠폰 예약 성공 - reservationId: {}, couponId: {}, reservedUntil: {}",
                    command.getReservationId(), command.getCouponId(), reservedUntil);

            return CouponReservationResult.builder()
                    .success(true)
                    .reservationId(command.getReservationId())
                    .couponId(couponIssue.getId())
                    .discountAmount(discountAmount)
                    .message("쿠폰 예약 성공")
                    .reservedUntil(reservedUntil)
                    .build();

        } catch (Exception e) {
            log.error("쿠폰 예약 실패 - reservationId: {}, couponId: {}, error: {}",
                    command.getReservationId(), command.getCouponId(), e.getMessage());

            return CouponReservationResult.builder()
                    .success(false)
                    .reservationId(command.getReservationId())
                    .couponId(command.getCouponId())
                    .discountAmount(BigDecimal.ZERO)
                    .message(e.getMessage())
                    .reservedUntil(null)
                    .build();
        }
    }

    /**
     * 쿠폰 예약 가능 여부 검증
     * @return 에러 메시지 (null이면 검증 통과)
     */
    private String validateCouponForReservation(CouponIssue couponIssue) {
        // 쿠폰 상태 확인
        if (couponIssue.getStatus() != CouponStatus.ISSUED) {
            if (couponIssue.getStatus() == CouponStatus.USED) {
                return "예약할 수 없는 상태입니다";
            }
            if (couponIssue.getStatus() == CouponStatus.RESERVED) {
                return "예약할 수 없는 상태입니다";
            }
            if (couponIssue.getStatus() == CouponStatus.EXPIRED) {
                return "예약할 수 없는 상태입니다";
            }
            if (couponIssue.getStatus() == CouponStatus.CANCELLED) {
                return "예약할 수 없는 상태입니다";
            }
        }

        // 사용 가능 상태 확인
        if (!couponIssue.isUsable()) {
            return "예약할 수 없는 상태입니다";
        }

        return null; // 검증 통과
    }
}
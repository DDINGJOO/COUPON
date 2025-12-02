package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.LoadReservationPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveReservationPort;
import com.teambind.coupon.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 쿠폰 락 관리 서비스
 * 트랜잭션 경계를 명확히 하기 위해 분리된 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponLockService {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final LoadReservationPort loadReservationPort;
    private final SaveReservationPort saveReservationPort;
    private final RedisDistributedLock distributedLock;

    /**
     * 쿠폰 락 획득 및 적용
     * 트랜잭션이 적용되는 public 메서드
     *
     * @param coupon  적용할 쿠폰
     * @param request 쿠폰 적용 요청
     * @return 쿠폰 적용 응답
     */
    @Transactional
    public CouponApplyResponse tryLockAndApplyCoupon(CouponIssue coupon, CouponApplyRequest request) {
        String lockKey = "coupon:apply:" + coupon.getId();
        String lockValue = UUID.randomUUID().toString();

        // 분산 락 획득 시도 (5초 타임아웃)
        if (!distributedLock.tryLock(lockKey, lockValue, Duration.ofSeconds(5))) {
            log.warn("쿠폰 락 획득 실패 - couponId: {}", coupon.getId());
            return CouponApplyResponse.empty();
        }

        try {
            // 쿠폰 정책 조회
            CouponPolicy policy = loadCouponPolicyPort.loadById(coupon.getPolicyId())
                    .orElseThrow(() -> new RuntimeException("쿠폰 정책을 찾을 수 없습니다"));

            // 예약 생성
            String reservationId = generateReservationId();
            CouponReservation reservation = CouponReservation.builder()
                    .reservationId(reservationId)
                    .couponId(coupon.getId())
                    .userId(request.getUserId())
                    .orderId(null) // 주문 ID는 결제 시 업데이트
                    .orderAmount(request.getOrderAmount())
                    .discountAmount(calculateDiscount(policy, request.getOrderAmount()))
                    .reservedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(30)) // 30분 유효
                    .status(ReservationStatus.PENDING)
                    .lockValue(lockValue) // 락 해제를 위한 value 저장
                    .build();

            saveReservationPort.save(reservation);

            // 쿠폰 상태를 RESERVED로 변경
            coupon.reserve(null); // orderId는 나중에 업데이트
            saveCouponIssuePort.save(coupon);

            // 응답 생성
            return CouponApplyResponse.builder()
                    .couponId(String.valueOf(coupon.getId()))
                    .couponName(policy.getCouponName())
                    .discountType(policy.getDiscountType())
                    .discountValue(policy.getDiscountValue())
                    .maxDiscountAmount(policy.getMaxDiscountAmount())
                    .build();

        } catch (Exception e) {
            // 예외 발생 시 락 해제
            distributedLock.unlock(lockKey, lockValue);
            log.error("쿠폰 적용 실패 - couponId: {}", coupon.getId(), e);
            throw e; // 트랜잭션 롤백을 위해 예외 재발생
        }
    }

    /**
     * 할인 금액 계산
     */
    private BigDecimal calculateDiscount(CouponPolicy policy, BigDecimal orderAmount) {
        BigDecimal amount = orderAmount;

        if (policy.getDiscountType() == DiscountType.AMOUNT ||
            policy.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            // 정액 할인
            return policy.getDiscountValue().min(amount);
        } else if (policy.getDiscountType() == DiscountType.PERCENTAGE) {
            // 퍼센트 할인
            BigDecimal discount = amount.multiply(policy.getDiscountValue())
                    .divide(BigDecimal.valueOf(100));

            // 최대 할인 금액 제한
            if (policy.getMaxDiscountAmount() != null) {
                discount = discount.min(policy.getMaxDiscountAmount());
            }

            return discount.min(amount);
        } else {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 예약 ID 생성
     */
    private String generateReservationId() {
        return "RESV-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
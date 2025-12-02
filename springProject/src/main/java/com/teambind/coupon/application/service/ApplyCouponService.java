package com.teambind.coupon.application.service;

import com.teambind.coupon.adapter.out.redis.RedisDistributedLock;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.in.ApplyCouponUseCase;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.LoadReservationPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveReservationPort;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponReservation;
import com.teambind.coupon.domain.model.CouponStatus;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 쿠폰 적용 서비스
 * 결제 전 쿠폰을 적용하고 락을 잡는 기능을 구현합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyCouponService implements ApplyCouponUseCase {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final LoadReservationPort loadReservationPort;
    private final SaveReservationPort saveReservationPort;
    private final RedisDistributedLock distributedLock;

    @Override
    @Transactional
    public CouponApplyResponse applyCoupon(CouponApplyRequest request) {
        log.info("쿠폰 적용 요청 - userId: {}, productIds: {}, orderAmount: {}",
                request.getUserId(), request.getProductIds(), request.getOrderAmount());

        // 사용자의 사용 가능한 쿠폰 조회
        List<CouponIssue> availableCoupons = loadCouponIssuePort.findAvailableCouponsByUserId(
                request.getUserId()
        );

        if (availableCoupons.isEmpty()) {
            log.info("사용 가능한 쿠폰이 없습니다. userId: {}", request.getUserId());
            return CouponApplyResponse.empty();
        }

        // 상품별로 적용 가능한 쿠폰 찾기
        for (Long productId : request.getProductIds()) {
            Optional<CouponIssue> applicableCoupon = findApplicableCoupon(
                    availableCoupons, productId, request.getOrderAmount()
            );

            if (applicableCoupon.isPresent()) {
                // 쿠폰 락 획득 시도
                CouponApplyResponse response = tryLockAndApplyCoupon(
                        applicableCoupon.get(), request
                );

                if (!response.isEmpty()) {
                    log.info("쿠폰 적용 성공 - couponId: {}, productId: {}",
                            response.getCouponId(), productId);
                    return response;
                }
            }
        }

        log.info("적용 가능한 쿠폰이 없습니다. userId: {}", request.getUserId());
        return CouponApplyResponse.empty();
    }

    @Override
    @Transactional
    public void releaseCouponLock(String reservationId) {
        log.info("쿠폰 락 해제 요청 - reservationId: {}", reservationId);

        try {
            // 예약 정보 조회 및 취소 처리
            loadReservationPort.findById(reservationId).ifPresent(reservation -> {
                // 쿠폰 상태를 ISSUED로 복구
                loadCouponIssuePort.findById(Long.valueOf(reservation.getCouponId()))
                        .ifPresent(coupon -> {
                            coupon.rollback();
                            saveCouponIssuePort.save(coupon);
                        });

                // 예약 취소
                reservation.cancel("LOCK_RELEASE");
                saveReservationPort.save(reservation);
            });

            // Redis 락 해제
            String lockKey = "coupon:apply:" + reservationId;
            distributedLock.unlock(lockKey, reservationId);

            log.info("쿠폰 락 해제 완료 - reservationId: {}", reservationId);
        } catch (Exception e) {
            log.error("쿠폰 락 해제 실패 - reservationId: {}", reservationId, e);
            throw new RuntimeException("쿠폰 락 해제 실패", e);
        }
    }

    /**
     * 적용 가능한 쿠폰 찾기
     */
    private Optional<CouponIssue> findApplicableCoupon(
            List<CouponIssue> coupons, Long productId, Long orderAmount) {

        return coupons.stream()
                .filter(coupon -> coupon.getStatus() == CouponStatus.ISSUED)
                .filter(coupon -> !coupon.isExpired())
                .filter(coupon -> isApplicableToProduct(coupon, productId))
                .filter(coupon -> isMinimumOrderMet(coupon, orderAmount))
                .findFirst();
    }

    /**
     * 쿠폰 락 획득 및 적용
     */
    private CouponApplyResponse tryLockAndApplyCoupon(CouponIssue coupon, CouponApplyRequest request) {
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
                    .orderAmount(BigDecimal.valueOf(request.getOrderAmount()))
                    .discountAmount(calculateDiscount(policy, request.getOrderAmount()))
                    .reservedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(30)) // 30분 유효
                    .status(ReservationStatus.PENDING)
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
            return CouponApplyResponse.empty();
        }
    }

    /**
     * 상품에 적용 가능한 쿠폰인지 확인
     */
    private boolean isApplicableToProduct(CouponIssue coupon, Long productId) {
        // TODO: 쿠폰-상품 매핑 테이블 확인 또는 정책에 따른 검증 로직 구현
        // 현재는 모든 상품에 적용 가능하다고 가정
        return true;
    }

    /**
     * 최소 주문 금액 조건 확인
     */
    private boolean isMinimumOrderMet(CouponIssue coupon, Long orderAmount) {
        return loadCouponPolicyPort.loadById(coupon.getPolicyId())
                .map(policy -> {
                    BigDecimal minAmount = policy.getMinimumOrderAmount();
                    return minAmount == null ||
                           minAmount.compareTo(BigDecimal.valueOf(orderAmount)) <= 0;
                })
                .orElse(false);
    }

    /**
     * 할인 금액 계산
     */
    private BigDecimal calculateDiscount(CouponPolicy policy, Long orderAmount) {
        BigDecimal amount = BigDecimal.valueOf(orderAmount);

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
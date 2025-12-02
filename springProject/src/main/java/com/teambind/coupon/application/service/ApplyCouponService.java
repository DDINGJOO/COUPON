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
import com.teambind.coupon.domain.model.CouponReservation;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
    private final CouponLockService couponLockService;

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
                // 쿠폰 락 획득 시도 (별도 서비스 호출)
                CouponApplyResponse response = couponLockService.tryLockAndApplyCoupon(
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
            // 예약 정보 조회
            CouponReservation reservation = loadReservationPort.findById(reservationId)
                    .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다: " + reservationId));

            // 쿠폰 상태를 ISSUED로 복구
            CouponIssue coupon = loadCouponIssuePort.findById(reservation.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + reservation.getCouponId()));

            coupon.rollback();
            saveCouponIssuePort.save(coupon);

            // 예약 취소
            reservation.cancel("LOCK_RELEASE");
            saveReservationPort.save(reservation);

            // Redis 락 해제 (올바른 키와 value 사용)
            String lockKey = "coupon:apply:" + reservation.getCouponId();
            if (reservation.getLockValue() != null) {
                distributedLock.unlock(lockKey, reservation.getLockValue());
                log.info("Redis 락 해제 성공 - lockKey: {}, couponId: {}", lockKey, reservation.getCouponId());
            } else {
                log.warn("락 value가 없어 Redis 락을 해제할 수 없습니다 - reservationId: {}", reservationId);
            }

            log.info("쿠폰 락 해제 완료 - reservationId: {}, couponId: {}",
                    reservationId, reservation.getCouponId());
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

}
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
import com.teambind.coupon.domain.exception.*;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.CouponReservation;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 적용 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyCouponService implements ApplyCouponUseCase {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final LoadReservationPort loadReservationPort;
    private final SaveReservationPort saveReservationPort;
    private final RedisDistributedLock distributedLock;
    private final CouponLockService couponLockService;

    @Override
    @Transactional
    public CouponApplyResponse applyCoupon(CouponApplyRequest request) {
        log.info("쿠폰 적용 요청 - userId: {}, couponId: {}, orderAmount: {}",
                request.getUserId(), request.getCouponId(), request.getOrderAmount());

        // 쿠폰 조회
        CouponIssue coupon = loadCouponIssuePort.findById(request.getCouponId())
                .orElseThrow(() -> new CouponNotFoundException("쿠폰을 찾을 수 없습니다: " + request.getCouponId()));

        // 쿠폰 소유자 확인
        if (!coupon.getUserId().equals(request.getUserId())) {
            throw new UnauthorizedCouponAccessException("해당 쿠폰에 대한 권한이 없습니다");
        }

        // 쿠폰 상태 확인
        if (coupon.getStatus() != CouponStatus.ISSUED) {
            throw new CouponAlreadyUsedException("사용 가능한 상태가 아닙니다: " + coupon.getStatus());
        }

        // 쿠폰 정책 조회
        CouponPolicy policy = loadCouponPolicyPort.loadById(coupon.getPolicyId())
                .orElseThrow(() -> new PolicyNotFoundException("정책을 찾을 수 없습니다"));

        // 최소 주문 금액 확인
        if (policy.getMinimumOrderAmount() != null &&
            request.getOrderAmount().compareTo(policy.getMinimumOrderAmount()) < 0) {
            throw new MinimumOrderNotMetException("최소 주문 금액을 충족하지 않습니다");
        }

        // 쿠폰 락 획득 및 적용
        return couponLockService.tryLockAndApplyCoupon(coupon, request);
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

        } catch (Exception e) {
            log.error("쿠폰 락 해제 중 오류 발생 - reservationId: {}, error: {}", reservationId, e.getMessage(), e);
            // 락 해제 실패는 무시하고 진행 (최종적으로 TTL에 의해 해제됨)
        }
    }
}
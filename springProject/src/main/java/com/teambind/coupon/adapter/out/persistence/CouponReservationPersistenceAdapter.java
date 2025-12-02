package com.teambind.coupon.adapter.out.persistence;

import com.teambind.coupon.adapter.out.persistence.entity.CouponReservationJpaEntity;
import com.teambind.coupon.adapter.out.persistence.mapper.CouponReservationMapper;
import com.teambind.coupon.adapter.out.persistence.repository.CouponReservationRepository;
import com.teambind.coupon.application.port.out.LoadReservationPort;
import com.teambind.coupon.application.port.out.SaveReservationPort;
import com.teambind.coupon.domain.model.CouponReservation;
import com.teambind.coupon.domain.model.ReservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 쿠폰 예약 Persistence Adapter
 * 쿠폰 예약 정보를 데이터베이스에 저장하고 조회하는 기능을 제공합니다.
 */
@Component
@RequiredArgsConstructor
public class CouponReservationPersistenceAdapter implements SaveReservationPort, LoadReservationPort {

    private final CouponReservationRepository reservationRepository;
    private final CouponReservationMapper reservationMapper;

    @Override
    public CouponReservation save(CouponReservation reservation) {
        CouponReservationJpaEntity entity = reservationMapper.toJpaEntity(reservation);
        CouponReservationJpaEntity savedEntity = reservationRepository.save(entity);
        return reservationMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<CouponReservation> findById(String reservationId) {
        return reservationRepository.findByReservationId(reservationId)
                .map(reservationMapper::toDomain);
    }

    @Override
    public List<CouponReservation> findExpiredReservations(LocalDateTime now) {
        return reservationRepository.findExpiredReservations(ReservationStatus.PENDING, now)
                .stream()
                .map(reservationMapper::toDomain)
                .collect(Collectors.toList());
    }
}
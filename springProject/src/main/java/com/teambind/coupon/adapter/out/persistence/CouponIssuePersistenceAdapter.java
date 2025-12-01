package com.teambind.coupon.adapter.out.persistence;

import com.teambind.coupon.adapter.out.persistence.entity.CouponIssueEntity;
import com.teambind.coupon.adapter.out.persistence.mapper.CouponIssueMapper;
import com.teambind.coupon.adapter.out.persistence.repository.CouponIssueRepository;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.model.CouponIssue;
import com.teambind.coupon.domain.model.CouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 쿠폰 발급 Persistence Adapter 구현
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponIssuePersistenceAdapter implements LoadCouponIssuePort, SaveCouponIssuePort {

    private final CouponIssueRepository repository;
    private final CouponIssueMapper mapper;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    public Optional<CouponIssue> loadById(Long issueId) {
        return repository.findById(issueId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<CouponIssue> loadByIdAndUserId(Long issueId, Long userId) {
        return repository.findByIdAndUserId(issueId, userId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<CouponIssue> loadByIdAndUserIdWithLock(Long issueId, Long userId) {
        return repository.findByIdAndUserIdWithLock(issueId, userId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<CouponIssue> loadByReservationIdWithLock(String reservationId) {
        return repository.findByReservationIdWithLock(reservationId)
                .map(mapper::toDomain);
    }

    @Override
    public int countUserIssuance(Long userId, Long policyId) {
        return repository.countByUserIdAndPolicyId(userId, policyId);
    }

    @Override
    public List<CouponIssue> loadUsableCoupons(Long userId) {
        return repository.findUsableCoupons(userId, LocalDateTime.now())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponIssue> loadTimeoutReservations(LocalDateTime timeoutTime) {
        return repository.findTimeoutReservations(timeoutTime)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<CouponIssue> loadExpiredCoupons() {
        return repository.findExpiredCoupons(LocalDateTime.now())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countByUserIdAndStatus(Long userId, CouponStatus status) {
        return repository.countByUserIdAndStatus(userId, status);
    }

    @Override
    @Transactional
    public CouponIssue save(CouponIssue issue) {
        // ID가 없으면 생성
        if (issue.getId() == null) {
            issue = CouponIssue.builder()
                    .id(idGenerator.nextId())
                    .policyId(issue.getPolicyId())
                    .userId(issue.getUserId())
                    .status(issue.getStatus())
                    .reservationId(issue.getReservationId())
                    .orderId(issue.getOrderId())
                    .issuedAt(issue.getIssuedAt())
                    .reservedAt(issue.getReservedAt())
                    .usedAt(issue.getUsedAt())
                    .expiredAt(issue.getExpiredAt())
                    .actualDiscountAmount(issue.getActualDiscountAmount())
                    .couponName(issue.getCouponName())
                    .discountPolicy(issue.getDiscountPolicy())
                    .build();
        }

        CouponIssueEntity entity = mapper.toEntity(issue);
        entity = repository.save(entity);

        log.info("쿠폰 발급 저장 완료 - issueId: {}, userId: {}, status: {}",
                entity.getId(), entity.getUserId(), entity.getStatus());

        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void update(CouponIssue issue) {
        CouponIssueEntity entity = repository.findById(issue.getId())
                .orElseThrow(() -> new IllegalArgumentException("발급된 쿠폰을 찾을 수 없습니다: " + issue.getId()));

        mapper.updateEntity(entity, issue);
        repository.save(entity);

        log.info("쿠폰 발급 업데이트 완료 - issueId: {}, status: {}",
                issue.getId(), issue.getStatus());
    }

    @Override
    @Transactional
    public int updateStatusBatch(List<Long> issueIds, CouponStatus newStatus, LocalDateTime expiredAt) {
        int updated = repository.updateStatusBatch(issueIds, newStatus, expiredAt);
        log.info("쿠폰 상태 일괄 업데이트 완료 - count: {}, newStatus: {}", updated, newStatus);
        return updated;
    }

    @Override
    @Transactional
    public List<CouponIssue> saveAll(List<CouponIssue> issues) {
        List<CouponIssueEntity> entities = issues.stream()
                .map(issue -> {
                    if (issue.getId() == null) {
                        CouponIssue newIssue = CouponIssue.builder()
                                .id(idGenerator.nextId())
                                .policyId(issue.getPolicyId())
                                .userId(issue.getUserId())
                                .status(issue.getStatus())
                                .issuedAt(issue.getIssuedAt())
                                .couponName(issue.getCouponName())
                                .discountPolicy(issue.getDiscountPolicy())
                                .build();
                        return mapper.toEntity(newIssue);
                    }
                    return mapper.toEntity(issue);
                })
                .collect(Collectors.toList());

        List<CouponIssueEntity> saved = repository.saveAll(entities);

        log.info("쿠폰 일괄 발급 저장 완료 - count: {}", saved.size());

        return saved.stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
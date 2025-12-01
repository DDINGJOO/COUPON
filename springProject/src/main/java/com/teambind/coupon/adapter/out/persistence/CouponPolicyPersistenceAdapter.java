package com.teambind.coupon.adapter.out.persistence;

import com.teambind.coupon.adapter.out.persistence.entity.CouponPolicyEntity;
import com.teambind.coupon.adapter.out.persistence.mapper.CouponPolicyMapper;
import com.teambind.coupon.adapter.out.persistence.repository.CouponPolicyRepository;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
import com.teambind.coupon.domain.model.CouponPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 쿠폰 정책 Persistence Adapter 구현
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponPolicyPersistenceAdapter implements LoadCouponPolicyPort, SaveCouponPolicyPort {

    private final CouponPolicyRepository repository;
    private final CouponPolicyMapper mapper;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    public Optional<CouponPolicy> loadById(Long policyId) {
        return repository.findById(policyId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<CouponPolicy> loadByCodeAndActive(String couponCode) {
        return repository.findByCouponCodeAndIsActiveTrue(couponCode)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<CouponPolicy> loadByCodeWithLock(String couponCode) {
        return repository.findByCouponCodeWithLock(couponCode)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String couponCode) {
        return repository.existsByCouponCode(couponCode);
    }

    @Override
    @Transactional
    public CouponPolicy save(CouponPolicy policy) {
        // ID가 없으면 생성
        if (policy.getId() == null) {
            policy = CouponPolicy.builder()
                    .id(idGenerator.nextId())
                    .couponName(policy.getCouponName())
                    .couponCode(policy.getCouponCode())
                    .description(policy.getDescription())
                    .discountPolicy(policy.getDiscountPolicy())
                    .applicableRule(policy.getApplicableRule())
                    .distributionType(policy.getDistributionType())
                    .validFrom(policy.getValidFrom())
                    .validUntil(policy.getValidUntil())
                    .maxIssueCount(policy.getMaxIssueCount())
                    .maxUsagePerUser(policy.getMaxUsagePerUser())
                    .isActive(policy.isActive())
                    .createdBy(policy.getCreatedBy())
                    .build();
        }

        CouponPolicyEntity entity = mapper.toEntity(policy);
        entity = repository.save(entity);

        log.info("쿠폰 정책 저장 완료 - policyId: {}, couponCode: {}",
                entity.getId(), entity.getCouponCode());

        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void update(CouponPolicy policy) {
        CouponPolicyEntity entity = repository.findById(policy.getId())
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 정책을 찾을 수 없습니다: " + policy.getId()));

        mapper.updateEntity(entity, policy);
        repository.save(entity);

        log.info("쿠폰 정책 업데이트 완료 - policyId: {}", policy.getId());
    }

    @Override
    @Transactional
    public boolean decrementStock(Long policyId) {
        int updated = repository.decrementStock(policyId);
        return updated > 0;
    }
}
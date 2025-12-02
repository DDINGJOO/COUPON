package com.teambind.coupon.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 쿠폰 정책 Aggregate Root
 * 쿠폰의 발급 규칙과 사용 조건을 정의
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CouponPolicy {

    private Long id; // Snowflake ID

    private String couponName;
    private String couponCode; // CODE 타입일 경우 사용
    private String description;

    private DiscountPolicy discountPolicy;
    private ItemApplicableRule applicableRule;
    private DistributionType distributionType;

    // 할인 관련 필드
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderAmount;
    private BigDecimal maxDiscountAmount;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    private Integer maxIssueCount; // 최대 발급 수량
    private Integer maxUsagePerUser; // 사용자당 최대 사용 횟수

    private boolean isActive; // 활성화 상태

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;

    // 통계용 필드 (실시간으로 업데이트되지 않음, 캐시나 별도 조회)
    @Setter
    @Builder.Default
    private transient AtomicInteger currentIssueCount = new AtomicInteger(0);

    /**
     * 쿠폰 발급 가능 여부 확인
     */
    public boolean isIssuable() {
        return isIssuable(LocalDateTime.now());
    }

    /**
     * 특정 시점 기준 쿠폰 발급 가능 여부 확인
     */
    public boolean isIssuable(LocalDateTime checkTime) {
        if (!isActive) {
            return false;
        }

        // 유효기간 확인
        if (checkTime.isBefore(validFrom) || checkTime.isAfter(validUntil)) {
            return false;
        }

        // 최대 발급 수량 확인
        if (maxIssueCount != null && currentIssueCount.get() >= maxIssueCount) {
            return false;
        }

        return true;
    }

    /**
     * 쿠폰 발급 처리 (재고 차감)
     * @return 발급 성공 여부
     */
    public boolean tryIssue() {
        if (!isIssuable()) {
            return false;
        }

        if (maxIssueCount != null) {
            int current = currentIssueCount.get();
            if (current >= maxIssueCount) {
                return false;
            }
            // CAS (Compare-And-Swap) 연산으로 동시성 제어
            return currentIssueCount.compareAndSet(current, current + 1);
        }

        return true;
    }

    /**
     * 쿠폰 정책 활성화
     */
    public void activate() {
        this.isActive = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 정책 비활성화
     */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 코드 기반 정책인지 확인
     */
    public boolean isCodeType() {
        return distributionType == DistributionType.CODE;
    }

    /**
     * 직접 발급 방식인지 확인
     */
    public boolean isDirectType() {
        return distributionType == DistributionType.DIRECT;
    }

    /**
     * 사용자별 발급 제한이 있는지 확인
     */
    public boolean hasUserLimit() {
        return maxUsagePerUser != null && maxUsagePerUser > 0;
    }

    /**
     * 쿠폰 유효기간이 만료되었는지 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(validUntil);
    }

    /**
     * 쿠폰 유효기간이 아직 시작되지 않았는지 확인
     */
    public boolean isNotStarted() {
        return LocalDateTime.now().isBefore(validFrom);
    }

    /**
     * 남은 발급 수량 업데이트
     * 생성된 쿠폰 정책의 유일한 수정 가능 필드
     *
     * @param newMaxIssueCount 새로운 최대 발급 수량
     * @throws IllegalArgumentException 현재 발급 수량보다 적게 설정하려는 경우
     * @throws IllegalStateException 쿠폰이 만료된 경우
     */
    public void updateRemainingQuantity(Integer newMaxIssueCount) {
        if (isExpired()) {
            throw new IllegalStateException("만료된 쿠폰 정책은 수정할 수 없습니다.");
        }

        if (newMaxIssueCount == null) {
            // null은 무제한을 의미
            this.maxIssueCount = null;
            this.updatedAt = LocalDateTime.now();
            return;
        }

        if (newMaxIssueCount < 0) {
            throw new IllegalArgumentException("발급 수량은 0 이상이어야 합니다.");
        }

        int currentIssued = this.currentIssueCount.get();
        if (newMaxIssueCount < currentIssued) {
            throw new IllegalArgumentException(
                String.format("새로운 발급 수량(%d)은 현재 발급된 수량(%d)보다 적을 수 없습니다.",
                    newMaxIssueCount, currentIssued)
            );
        }

        this.maxIssueCount = newMaxIssueCount;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 쿠폰 정책 정보 수정 방지를 위한 검증
     * 다른 필드 수정 시도 시 예외 발생
     *
     * @deprecated 쿠폰 정책은 생성 후 수정 불가. updateRemainingQuantity()만 사용 가능
     */
    @Deprecated
    private void preventModification() {
        throw new UnsupportedOperationException("쿠폰 정책 정보는 수정할 수 없습니다. 남은 발급 수량만 수정 가능합니다.");
    }
}
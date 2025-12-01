package com.teambind.coupon.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 쿠폰 적용 가능 상품 규칙 Value Object
 * 쿠폰을 사용할 수 있는 상품 목록을 정의
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ItemApplicableRule {

    // 자주 사용되는 인스턴스
    public static final ItemApplicableRule ALL = forAllItems();

    private boolean allItemsApplicable; // 모든 상품 적용 가능 여부
    private List<Long> applicableItemIds; // 적용 가능한 상품 ID 목록

    /**
     * 특정 상품이 쿠폰 적용 가능한지 확인
     * @param itemId 상품 ID
     * @return 적용 가능 여부
     */
    public boolean isApplicable(Long itemId) {
        if (allItemsApplicable) {
            return true;
        }
        return applicableItemIds != null && applicableItemIds.contains(itemId);
    }

    /**
     * 여러 상품 중 적용 가능한 상품이 있는지 확인
     * @param itemIds 상품 ID 목록
     * @return 하나라도 적용 가능하면 true
     */
    public boolean hasApplicableItem(List<Long> itemIds) {
        if (allItemsApplicable) {
            return true;
        }
        if (applicableItemIds == null || applicableItemIds.isEmpty()) {
            return false;
        }
        return itemIds.stream().anyMatch(this::isApplicable);
    }

    /**
     * 모든 상품에 적용 가능한 규칙 생성
     */
    public static ItemApplicableRule forAllItems() {
        return new ItemApplicableRule(true, Collections.emptyList());
    }

    /**
     * 특정 상품들에만 적용 가능한 규칙 생성
     */
    public static ItemApplicableRule forSpecificItems(List<Long> itemIds) {
        return new ItemApplicableRule(false, itemIds);
    }
}
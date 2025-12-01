package com.teambind.coupon.adapter.out.persistence.entity;

import lombok.Data;

import java.util.List;

/**
 * 쿠폰 적용 가능 상품 규칙 JSON 타입
 * PostgreSQL JSONB 컬럼에 저장되는 데이터
 */
@Data
public class ItemApplicableRuleJson {
    private boolean allItemsApplicable;
    private List<Long> applicableItemIds;
}
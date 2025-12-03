package com.teambind.coupon.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 쿠폰 조회 요청 DTO
 * 커서 기반 페이지네이션 및 필터링 파라미터
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponQueryRequest {

    /**
     * 쿠폰 상태 필터
     * - AVAILABLE: 사용 가능 (유효기간 내 미사용)
     * - UNUSED: 미사용 (ISSUED 상태)
     * - USED: 사용 완료
     * - EXPIRED: 만료
     * - null: 전체 조회
     */
    private CouponStatusFilter status;

    /**
     * 상품 ID 리스트
     * 해당 상품들에 적용 가능한 쿠폰만 필터링
     */
    private List<Long> productIds;

    /**
     * 커서 (마지막으로 조회한 쿠폰 ID)
     * 첫 페이지는 null
     */
    private Long cursor;

    /**
     * 페이지당 조회 개수
     * 기본값: 20, 최대: 100
     */
    @Min(1)
    @Max(100)
    @Builder.Default
    private int limit = 20;

    /**
     * 정렬 방식
     * - EXPIRY_ASC: 만료 임박순 (기본값)
     * - ISSUED_DESC: 최신 발급순
     */
    @Builder.Default
    private SortType sortType = SortType.EXPIRY_ASC;

    public enum CouponStatusFilter {
        AVAILABLE,  // 사용 가능 (유효기간 내 미사용)
        UNUSED,     // 미사용 (ISSUED 상태 전체)
        USED,       // 사용 완료
        EXPIRED,    // 만료
        RESERVED    // 예약됨
    }

    public enum SortType {
        EXPIRY_ASC,   // 만료 임박순
        ISSUED_DESC   // 최신 발급순
    }

    /**
     * 상품 ID 리스트를 PostgreSQL 배열 형식으로 변환
     * [1, 2, 3] -> "{1,2,3}"
     */
    public String getProductIdsAsPostgresArray() {
        if (productIds == null || productIds.isEmpty()) {
            return null;
        }
        return "{" + String.join(",", productIds.stream()
                .map(String::valueOf)
                .toList()) + "}";
    }

    /**
     * 데이터베이스 상태값으로 변환
     */
    public String getStatusForQuery() {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case AVAILABLE -> "AVAILABLE";  // 특별 처리
            case UNUSED -> "ISSUED";
            case USED -> "USED";
            case EXPIRED -> "EXPIRED";
            case RESERVED -> "RESERVED";
        };
    }
}
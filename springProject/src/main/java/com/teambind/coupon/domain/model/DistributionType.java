package com.teambind.coupon.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 배포 방식
 */
@Getter
@RequiredArgsConstructor
public enum DistributionType {
    CODE("쿠폰 코드 방식 - 공개 코드로 다운로드"),
    DIRECT("직접 발급 - 관리자가 사용자에게 직접 발급"),
    EVENT("이벤트 발급 - 특정 이벤트 조건 만족시 자동 발급");

    private final String description;
}
# 에러 코드

## 에러 응답 형식

모든 API 에러는 다음과 같은 통일된 형식으로 반환됩니다:

```json
{
    "success": false,
    "error": {
        "code": "COUPON_NOT_FOUND",
        "message": "쿠폰을 찾을 수 없습니다",
        "details": {
            "couponId": 1001,
            "requestedBy": 12345
        }
    },
    "timestamp": "2024-01-01T10:00:00Z",
    "path": "/api/coupons/1001"
}
```

## 에러 코드 목록

### 공통 에러 (COMMON)

| 에러 코드 | HTTP 상태 | 설명 | 대응 방법 |
|-----------|-----------|------|-----------|
| INVALID_REQUEST | 400 | 잘못된 요청 형식 | 요청 파라미터 확인 |
| UNAUTHORIZED | 401 | 인증 실패 | 토큰 재발급 필요 |
| FORBIDDEN | 403 | 접근 권한 없음 | 권한 확인 필요 |
| NOT_FOUND | 404 | 리소스를 찾을 수 없음 | 리소스 ID 확인 |
| METHOD_NOT_ALLOWED | 405 | 허용되지 않은 HTTP 메서드 | HTTP 메서드 확인 |
| CONFLICT | 409 | 리소스 충돌 | 중복 확인 필요 |
| TOO_MANY_REQUESTS | 429 | 요청 제한 초과 | 재시도 대기 |
| INTERNAL_SERVER_ERROR | 500 | 서버 내부 오류 | 관리자 문의 |
| SERVICE_UNAVAILABLE | 503 | 서비스 이용 불가 | 잠시 후 재시도 |

### 쿠폰 정책 에러 (POLICY)

| 에러 코드 | HTTP 상태 | 설명 | 대응 방법 |
|-----------|-----------|------|-----------|
| POLICY_NOT_FOUND | 404 | 쿠폰 정책을 찾을 수 없음 | 정책 ID 확인 |
| POLICY_INACTIVE | 400 | 비활성화된 쿠폰 정책 | 활성 정책 확인 |
| DUPLICATE_COUPON_CODE | 409 | 중복된 쿠폰 코드 | 다른 코드 사용 |
| INVALID_DISCOUNT_VALUE | 400 | 잘못된 할인 값 | 할인 값 범위 확인 |
| INVALID_DATE_RANGE | 400 | 잘못된 날짜 범위 | 시작/종료 날짜 확인 |
| POLICY_MODIFICATION_RESTRICTED | 403 | 정책 수정 제한 | 발급된 쿠폰 확인 |
| MAX_ISSUE_COUNT_EXCEEDED | 400 | 최대 발급 수량 초과 | 수량 제한 확인 |

### 쿠폰 발급 에러 (ISSUE)

| 에러 코드 | HTTP 상태 | 설명 | 대응 방법 |
|-----------|-----------|------|-----------|
| COUPON_NOT_FOUND | 404 | 쿠폰을 찾을 수 없음 | 쿠폰 ID 확인 |
| COUPON_CODE_NOT_FOUND | 404 | 쿠폰 코드를 찾을 수 없음 | 코드 확인 |
| COUPON_ALREADY_ISSUED | 409 | 이미 발급받은 쿠폰 | 발급 이력 확인 |
| COUPON_ISSUE_LIMIT_EXCEEDED | 429 | 발급 한도 초과 | 사용자별 제한 확인 |
| COUPON_ISSUE_PERIOD_EXPIRED | 400 | 발급 기간 종료 | 발급 가능 기간 확인 |
| COUPON_SOLD_OUT | 410 | 쿠폰 소진 | 다른 쿠폰 확인 |
| USER_NOT_ELIGIBLE | 403 | 발급 대상이 아님 | 발급 조건 확인 |
| CONCURRENT_ISSUE_CONFLICT | 409 | 동시 발급 충돌 | 재시도 필요 |

### 쿠폰 사용 에러 (USE)

| 에러 코드 | HTTP 상태 | 설명 | 대응 방법 |
|-----------|-----------|------|-----------|
| COUPON_NOT_AVAILABLE | 400 | 사용 불가능한 쿠폰 | 쿠폰 상태 확인 |
| COUPON_ALREADY_USED | 409 | 이미 사용된 쿠폰 | 사용 이력 확인 |
| COUPON_EXPIRED | 410 | 만료된 쿠폰 | 유효기간 확인 |
| MINIMUM_ORDER_NOT_MET | 400 | 최소 주문 금액 미달 | 주문 금액 확인 |
| RESERVATION_NOT_FOUND | 404 | 예약 정보 없음 | 예약 ID 확인 |
| RESERVATION_EXPIRED | 410 | 예약 시간 만료 | 재예약 필요 |
| INVALID_ORDER_AMOUNT | 400 | 잘못된 주문 금액 | 금액 재확인 |
| COUPON_NOT_APPLICABLE | 400 | 적용 불가능한 쿠폰 | 적용 조건 확인 |

### 동시성 에러 (CONCURRENCY)

| 에러 코드 | HTTP 상태 | 설명 | 대응 방법 |
|-----------|-----------|------|-----------|
| LOCK_ACQUISITION_FAILED | 409 | 락 획득 실패 | 재시도 필요 |
| OPTIMISTIC_LOCK_FAILURE | 409 | 낙관적 락 충돌 | 재시도 필요 |
| TRANSACTION_CONFLICT | 409 | 트랜잭션 충돌 | 재시도 필요 |
| CONCURRENT_MODIFICATION | 409 | 동시 수정 감지 | 최신 데이터로 재시도 |

### 검증 에러 (VALIDATION)

| 에러 코드 | HTTP 상태 | 설명 | 세부 정보 |
|-----------|-----------|------|-----------|
| FIELD_VALIDATION_ERROR | 400 | 필드 검증 실패 | 필드별 에러 메시지 |
| MISSING_REQUIRED_FIELD | 400 | 필수 필드 누락 | 누락된 필드 목록 |
| INVALID_FIELD_FORMAT | 400 | 잘못된 필드 형식 | 올바른 형식 안내 |
| INVALID_ENUM_VALUE | 400 | 잘못된 열거형 값 | 허용된 값 목록 |
| VALUE_OUT_OF_RANGE | 400 | 값 범위 초과 | 허용 범위 안내 |

## 에러 처리 예제

### Java 클라이언트

```java
try {
    CouponResponse response = couponService.issueCoupon(request);
} catch (CouponServiceException e) {
    switch (e.getErrorCode()) {
        case "COUPON_ALREADY_ISSUED":
            // 이미 발급받은 쿠폰 처리
            break;
        case "COUPON_SOLD_OUT":
            // 쿠폰 소진 처리
            break;
        default:
            // 기타 에러 처리
    }
}
```

### JavaScript 클라이언트

```javascript
try {
    const response = await couponApi.issueCoupon(request);
} catch (error) {
    if (error.response) {
        const { code, message } = error.response.data.error;

        switch (code) {
            case 'COUPON_ALREADY_ISSUED':
                alert('이미 발급받은 쿠폰입니다.');
                break;
            case 'COUPON_SOLD_OUT':
                alert('쿠폰이 모두 소진되었습니다.');
                break;
            default:
                alert(message);
        }
    }
}
```

## 재시도 가이드라인

### 재시도 가능한 에러

| 에러 코드 | 재시도 전략 | 대기 시간 |
|-----------|-------------|-----------|
| LOCK_ACQUISITION_FAILED | Exponential Backoff | 100ms, 200ms, 400ms |
| CONCURRENT_MODIFICATION | Fixed Delay | 500ms |
| SERVICE_UNAVAILABLE | Linear Backoff | 1s, 2s, 3s |
| TOO_MANY_REQUESTS | Rate Limit Header | X-RateLimit-Reset |

### 재시도 불가능한 에러

- COUPON_ALREADY_ISSUED
- COUPON_ALREADY_USED
- COUPON_EXPIRED
- USER_NOT_ELIGIBLE
- FIELD_VALIDATION_ERROR

## 에러 모니터링

### 알람 임계치

| 에러 카테고리 | 임계치 | 알람 레벨 |
|--------------|--------|-----------|
| 5xx 에러 | > 1% | Critical |
| 4xx 에러 | > 10% | Warning |
| LOCK_ACQUISITION_FAILED | > 5% | Warning |
| SERVICE_UNAVAILABLE | Any | Critical |

### 로깅 레벨

```properties
# application.yml
logging:
  level:
    com.teambind.coupon.exception: ERROR
    com.teambind.coupon.validation: WARN
    com.teambind.coupon.concurrency: INFO
```
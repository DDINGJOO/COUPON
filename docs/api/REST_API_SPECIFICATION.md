# REST API 명세서

## 기본 정보

### Base URL
```
Production: https://api.coupon-service.com
Development: http://localhost:8080
```

### 인증
- Bearer Token (JWT)
- Header: `Authorization: Bearer <token>`

### 공통 응답 형식

#### 성공 응답
```json
{
    "success": true,
    "data": { },
    "timestamp": "2024-12-02T10:00:00Z"
}
```

#### 에러 응답
```json
{
    "success": false,
    "error": {
        "code": "COUPON_NOT_FOUND",
        "message": "쿠폰을 찾을 수 없습니다",
        "details": { }
    },
    "timestamp": "2024-12-02T10:00:00Z"
}
```

## API 엔드포인트

### 1. 쿠폰 정책 관리

#### 1.1 쿠폰 정책 생성

**POST** `/api/coupons/policies`

**Request Body**
```json
{
    "couponName": "신규 가입 쿠폰",
    "couponCode": "WELCOME2024",
    "discountType": "FIXED_AMOUNT",
    "discountValue": 10000,
    "minimumOrderAmount": 50000,
    "maxDiscountAmount": null,
    "issueType": "CODE",
    "maxIssueCount": 1000,
    "maxIssuePerUser": 1,
    "validDays": 30,
    "issueStartDate": "2024-01-01T00:00:00",
    "issueEndDate": "2024-12-31T23:59:59"
}
```

**Response (201 Created)**
```json
{
    "id": 1,
    "couponName": "신규 가입 쿠폰",
    "couponCode": "WELCOME2024",
    "discountType": "FIXED_AMOUNT",
    "discountValue": 10000,
    "currentIssueCount": 0,
    "maxIssueCount": 1000,
    "createdAt": "2024-01-01T10:00:00"
}
```

#### 1.2 쿠폰 정책 조회

**GET** `/api/coupons/policies/{policyId}`

**Response (200 OK)**
```json
{
    "id": 1,
    "couponName": "신규 가입 쿠폰",
    "couponCode": "WELCOME2024",
    "discountType": "FIXED_AMOUNT",
    "discountValue": 10000,
    "minimumOrderAmount": 50000,
    "maxDiscountAmount": null,
    "issueType": "CODE",
    "currentIssueCount": 500,
    "maxIssueCount": 1000,
    "maxIssuePerUser": 1,
    "validDays": 30,
    "issueStartDate": "2024-01-01T00:00:00",
    "issueEndDate": "2024-12-31T23:59:59",
    "isActive": true,
    "createdAt": "2024-01-01T10:00:00"
}
```

#### 1.3 쿠폰 정책 수정

**PATCH** `/api/coupons/policies/{policyId}`

**Request Body**
```json
{
    "maxIssueCount": 2000,
    "issueEndDate": "2025-03-31T23:59:59"
}
```

**Response (200 OK)**
```json
{
    "id": 1,
    "couponName": "신규 가입 쿠폰",
    "maxIssueCount": 2000,
    "issueEndDate": "2025-03-31T23:59:59",
    "updatedAt": "2024-12-02T10:00:00"
}
```

#### 1.4 쿠폰 정책 비활성화

**DELETE** `/api/coupons/policies/{policyId}`

**Response (204 No Content)**

### 2. 쿠폰 발급

#### 2.1 코드로 쿠폰 발급

**POST** `/api/coupons/issue/code`

**Request Body**
```json
{
    "couponCode": "WELCOME2024",
    "userId": 12345
}
```

**Response (200 OK)**
```json
{
    "couponId": 1001,
    "couponName": "신규 가입 쿠폰",
    "discountType": "FIXED_AMOUNT",
    "discountValue": 10000,
    "status": "ISSUED",
    "expiryDate": "2024-01-31T23:59:59",
    "issuedAt": "2024-01-01T10:00:00"
}
```

#### 2.2 직접 발급

**POST** `/api/coupons/issue/direct`

**Request Body**
```json
{
    "policyId": 1,
    "userIds": [12345, 12346, 12347]
}
```

**Response (200 OK)**
```json
{
    "totalRequested": 3,
    "successCount": 3,
    "failureCount": 0,
    "results": [
        {
            "userId": 12345,
            "couponId": 1001,
            "status": "SUCCESS"
        },
        {
            "userId": 12346,
            "couponId": 1002,
            "status": "SUCCESS"
        },
        {
            "userId": 12347,
            "couponId": 1003,
            "status": "SUCCESS"
        }
    ]
}
```

#### 2.3 배치 발급 (관리자)

**POST** `/api/coupons/issue/batch`

**Request Body**
```json
{
    "policyId": 1,
    "userIds": [12345, 12346, 12347],
    "batchSize": 100,
    "useDistributedLock": true
}
```

**Response (200 OK)**
```json
{
    "batchId": "BATCH-2024-0001",
    "totalRequested": 3,
    "successCount": 3,
    "failureCount": 0,
    "processingTimeMs": 45,
    "results": [
        {
            "userId": 12345,
            "couponId": 1001,
            "status": "SUCCESS"
        }
    ]
}
```

#### 2.4 선착순 발급 (EVENT)

**POST** `/api/coupons/issue/fcfs`

**Request Body**
```json
{
    "policyId": 1,
    "userId": 12345
}
```

**Response (200 OK)**
```json
{
    "couponId": 1001,
    "remainingStock": 499,
    "position": 501,
    "issuedAt": "2024-01-01T10:00:00.123Z"
}
```

**Response (409 Conflict - 재고 소진)**
```json
{
    "error": "STOCK_EXHAUSTED",
    "message": "쿠폰 재고가 모두 소진되었습니다",
    "policyId": 1,
    "maxStock": 1000
}
```

#### 2.5 사용자 쿠폰 목록 조회

**GET** `/api/coupons/users/{userId}`

**Query Parameters**
- `status`: ISSUED | RESERVED | USED | EXPIRED | CANCELLED
- `page`: 페이지 번호 (기본값: 0)
- `size`: 페이지 크기 (기본값: 20)
- `sort`: 정렬 기준 (기본값: issuedAt,desc)

**Response (200 OK)**
```json
{
    "content": [
        {
            "couponId": 1001,
            "couponName": "신규 가입 쿠폰",
            "discountType": "FIXED_AMOUNT",
            "discountValue": 10000,
            "status": "ISSUED",
            "expiryDate": "2024-01-31T23:59:59",
            "issuedAt": "2024-01-01T10:00:00"
        }
    ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 20
}
```

### 3. 쿠폰 사용

#### 3.1 쿠폰 예약

**POST** `/api/coupons/reserve`

**Request Body**
```json
{
    "couponId": 1001,
    "orderId": "ORDER-2024-0001",
    "orderAmount": 100000
}
```

**Response (200 OK)**
```json
{
    "reservationId": "RESV-2024-0001",
    "couponId": 1001,
    "orderId": "ORDER-2024-0001",
    "discountAmount": 10000,
    "finalAmount": 90000,
    "reservedAt": "2024-01-01T10:00:00",
    "expiresAt": "2024-01-01T10:30:00"
}
```

#### 3.2 쿠폰 사용 확정

**POST** `/api/coupons/use`

**Request Body**
```json
{
    "reservationId": "RESV-2024-0001",
    "paymentId": "PAY-2024-0001"
}
```

**Response (200 OK)**
```json
{
    "couponId": 1001,
    "status": "USED",
    "usedAt": "2024-01-01T10:05:00",
    "orderId": "ORDER-2024-0001",
    "discountAmount": 10000
}
```

#### 3.3 쿠폰 예약 취소

**DELETE** `/api/coupons/reserve/{reservationId}`

**Response (200 OK)**
```json
{
    "reservationId": "RESV-2024-0001",
    "status": "CANCELLED",
    "cancelledAt": "2024-01-01T10:10:00"
}
```

### 4. 관리자 API

#### 4.1 재고 동기화

**POST** `/api/admin/coupons/stock/sync`

**Request Body**
```json
{
    "policyId": 1,
    "forceSync": false
}
```

**Response (200 OK)**
```json
{
    "policyId": 1,
    "dbStock": 500,
    "redisStock": 500,
    "syncStatus": "SUCCESS",
    "syncedAt": "2024-01-01T10:00:00"
}
```

#### 4.2 만료 쿠폰 처리

**POST** `/api/admin/coupons/expire`

**Request Body**
```json
{
    "batchSize": 1000,
    "dryRun": false
}
```

**Response (200 OK)**
```json
{
    "processedCount": 150,
    "expiredCount": 150,
    "processingTimeMs": 234,
    "processedAt": "2024-01-01T00:00:00"
}
```

#### 4.3 예약 타임아웃 처리

**POST** `/api/admin/coupons/reservations/timeout`

**Request Body**
```json
{
    "timeoutMinutes": 30,
    "batchSize": 100
}
```

**Response (200 OK)**
```json
{
    "processedCount": 25,
    "releasedCount": 25,
    "processingTimeMs": 120,
    "processedAt": "2024-01-01T10:30:00"
}
```

#### 4.4 쿠폰 정책 재고 조정

**PATCH** `/api/admin/coupons/policies/{policyId}/stock`

**Request Body**
```json
{
    "adjustment": 1000,
    "reason": "추가 재고 할당"
}
```

**Response (200 OK)**
```json
{
    "policyId": 1,
    "previousStock": 1000,
    "newStock": 2000,
    "adjustedBy": 1000,
    "adjustedAt": "2024-01-01T10:00:00"
}
```

### 5. 쿠폰 통계

#### 5.1 실시간 통계

**GET** `/api/coupons/statistics/realtime/{policyId}`

**Response (200 OK)**
```json
{
    "policyId": 1,
    "policyName": "신규 가입 쿠폰",
    "maxIssueCount": 1000,
    "currentIssueCount": 500,
    "usedCount": 200,
    "reservedCount": 50,
    "availableCount": 500,
    "usageRate": 40.0,
    "lastIssuedAt": "2024-01-01T09:55:00",
    "lastUsedAt": "2024-01-01T09:50:00"
}
```

#### 5.2 전체 통계

**GET** `/api/coupons/statistics/global`

**Response (200 OK)**
```json
{
    "totalPolicies": 10,
    "totalIssuedCoupons": 5000,
    "totalUsedCoupons": 2000,
    "totalReservedCoupons": 500,
    "totalExpiredCoupons": 100,
    "overallUsageRate": 40.0,
    "statusDistribution": {
        "ISSUED": 2400,
        "USED": 2000,
        "RESERVED": 500,
        "EXPIRED": 100
    },
    "typeDistribution": {
        "CODE": 3000,
        "DIRECT": 2000
    }
}
```

#### 5.3 사용자 통계

**GET** `/api/coupons/statistics/user/{userId}`

**Response (200 OK)**
```json
{
    "userId": 12345,
    "totalCoupons": 10,
    "availableCoupons": 5,
    "usedCoupons": 3,
    "expiredCoupons": 2,
    "totalDiscountAmount": 30000,
    "firstIssuedAt": "2024-01-01T10:00:00",
    "lastUsedAt": "2024-11-30T15:30:00",
    "couponsByStatus": {
        "ISSUED": 5,
        "USED": 3,
        "EXPIRED": 2
    }
}
```

#### 5.4 대시보드 요약

**GET** `/api/coupons/statistics/dashboard`

**Response (200 OK)**
```json
{
    "totalPolicies": 10,
    "totalIssuedCoupons": 5000,
    "totalUsedCoupons": 2000,
    "overallUsageRate": 40.0,
    "activeReservations": 500,
    "expiredCoupons": 100,
    "todayIssued": 50,
    "todayUsed": 20,
    "weeklyTrend": {
        "issued": [45, 50, 48, 52, 49, 51, 50],
        "used": [18, 20, 19, 21, 20, 22, 20]
    }
}
```

## HTTP 상태 코드

| 상태 코드 | 설명 |
|----------|------|
| 200 | 성공 |
| 201 | 리소스 생성 성공 |
| 204 | 성공 (응답 본문 없음) |
| 400 | 잘못된 요청 |
| 401 | 인증 실패 |
| 403 | 권한 없음 |
| 404 | 리소스를 찾을 수 없음 |
| 409 | 충돌 (예: 중복 발급) |
| 429 | 요청 제한 초과 |
| 500 | 서버 내부 오류 |

## 페이지네이션

페이지네이션을 지원하는 엔드포인트는 다음 쿼리 파라미터를 사용합니다:

- `page`: 페이지 번호 (0부터 시작)
- `size`: 페이지 크기 (기본값: 20, 최대: 100)
- `sort`: 정렬 기준 (예: createdAt,desc)

## Rate Limiting

- 인증된 사용자: 분당 600 요청
- 미인증 사용자: 분당 60 요청
- 헤더: `X-RateLimit-Remaining`, `X-RateLimit-Reset`

## 버전 관리

API 버전은 URL 경로에 포함됩니다:
- v1: `/api/v1/coupons/...`
- v2: `/api/v2/coupons/...` (향후 계획)
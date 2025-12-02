
# 쿠폰 관리 서비스 (Coupon Management Service)

## 목차

- [프로젝트 개요](#프로젝트-개요)
  - [비즈니스 목표](#비즈니스-목표)
  - [핵심 가치](#핵심-가치)
- [핵심 기능](#핵심-기능)
  - [쿠폰 발급 및 다운로드](#쿠폰-발급-및-다운로드)
  - [쿠폰 사용 프로세스](#쿠폰-사용-프로세스)
  - [동시성 제어](#동시성-제어)
  - [통계 및 분석](#통계-및-분석)
- [아키텍처](#아키텍처)
  - [Hexagonal Architecture](#hexagonal-architecture)
  - [이벤트 기반 통신](#이벤트-기반-통신)
  - [패키지 구조](#패키지-구조)
- [기술 스택](#기술-스택)
- [데이터베이스 설계](#데이터베이스-설계)
  - [ERD](#erd)
  - [인덱싱 전략](#인덱싱-전략)
- [API 명세](#api-명세)
- [이벤트 스키마](#이벤트-스키마)
- [설정 및 실행](#설정-및-실행)

## 프로젝트 개요

### 비즈니스 목표

MSA 아키텍처 기반의 예약 시스템에서 **쿠폰 할인 기능**을 제공하는 독립적인 마이크로서비스입니다. 프로모션을 통한 고객 유치와 재구매 촉진을 목표로 합니다.

### 핵심 가치

- **유연한 할인 정책**: 고정 금액 / 비율 할인 (최대 한도 설정)
- **다양한 배포 방식**: 쿠폰 코드 다운로드 / 관리자 직접 발급 / 이벤트 기반 자동 발급
- **실시간 동시성 제어**: Redis 분산 락과 원자적 재고 관리로 100% 정확성 보장
- **신뢰성 있는 예약-결제 통합**: 2단계 커밋 패턴 적용
- **성능 최적화**: N+1 쿼리 해결, 배치 처리, 캐싱 전략
- **자동화**: 쿠폰 만료 자동 처리, 예약 타임아웃 관리

## 핵심 기능

### 쿠폰 발급 및 다운로드

```
사용자 → 쿠폰 코드 입력 → 검증 → 쿠폰 발급
         └─ "WELCOME2024" → 100명 한정 → 1인당 1회
```

**발급 방식**
- **CODE 타입**: 공개된 쿠폰 코드로 여러 사용자가 다운로드
- **DIRECT 타입**: 관리자가 특정 사용자에게 직접 발급

**제약 사항**
- `maxIssueCount`: 전체 발급 수량 제한
- `maxUsagePerUser`: 사용자당 발급/사용 횟수 제한
- 유효기간: 절대 날짜 기반 (예: 2025-09-10 23:59:59)

### 쿠폰 사용 프로세스

```
[예약 단계]
게이트웨이 → 쿠폰 서비스
    │
    ├─ reservationId (String)
    ├─ userId (Long)
    └─ couponId (Long)

[쿠폰 상태 변화]
ISSUED → RESERVED (10분 타임아웃) → USED
           ↓ (timeout/failure)
        ISSUED (재사용 가능)

[결제 완료]
Payment Service → Kafka(payment-completed) → Coupon Service
                     │
                     └─ reservationId 매칭 → 사용 확정
```

### 동시성 제어

**다층 방어 전략**
1. **Redis 원자적 재고 관리**: Lua 스크립트를 통한 check-and-decrement
2. **분산 락**: 선착순 발급 시 중복 요청 방지
3. **DB 비관적 락**: 재고 차감 시 정합성 보장
4. **Rate Limiting**: IP/사용자별 요청 제한
5. **무작위 대입 방지**: 실패 카운터 & 자동 차단

```lua
-- Redis Lua 스크립트: 원자적 재고 차감
local stock = redis.call('get', KEYS[1])
if stock >= quantity then
    redis.call('decrby', KEYS[1], quantity)
    return remaining_stock
else
    return -1
end
```

**성능 테스트 결과**
- 동시 요청: 100개 스레드
- 재고: 50개
- 결과: ✅ 정확히 50개만 발급, 재고 정확성 100%

### 통계 및 분석

**실시간 통계** (Redis)
- 발급/사용 카운터
- 시간대별 사용 패턴
- 할인 금액 누적

**일별 집계** (PostgreSQL)
- 사용률 분석
- 채널별 분포
- 피크 시간 분석

## 아키텍처

### Hexagonal Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Adapter Layer                      │
├─────────────────────────────────────────────────────┤
│  in                              out                 │
│  ├─ web (REST API)              ├─ kafka           │
│  ├─ kafka (Event Consumer)      ├─ persistence     │
│  └─ scheduler                   └─ redis           │
├─────────────────────────────────────────────────────┤
│              Application Layer                       │
│  ├─ CouponIssueService                             │
│  ├─ CouponUsageService                             │
│  └─ CouponStatisticsService                        │
├─────────────────────────────────────────────────────┤
│                Domain Layer                          │
│  ├─ CouponPolicy (Aggregate Root)                  │
│  ├─ CouponIssue                                    │
│  ├─ DiscountPolicy (Value Object)                  │
│  └─ ItemApplicableRule (Value Object)              │
└─────────────────────────────────────────────────────┘
```

### 이벤트 기반 통신

**수신 이벤트**
- `payment-completed`: 결제 완료 시 쿠폰 사용 확정

**발행 이벤트**
- 현재 이벤트 발행 요구사항은 제거되었습니다.

### 패키지 구조

```
com.teambind.coupon
├─ adapter
│  ├─ in
│  │  ├─ web                 # REST Controller
│  │  ├─ kafka               # Event Consumer
│  │  └─ scheduler           # 타임아웃/통계 스케줄러
│  └─ out
│     ├─ kafka               # Event Publisher
│     ├─ persistence         # JPA Repository
│     └─ redis              # Redis Operations
├─ application
│  ├─ port
│  │  ├─ in                 # Use Case Interface
│  │  └─ out                # Port Interface
│  └─ service               # Service Implementation
├─ domain
│  ├─ model                 # Domain Entity/VO
│  ├─ event                 # Domain Event
│  └─ exception            # Domain Exception
└─ common
   ├─ config               # Configuration
   └─ util                 # Snowflake ID Generator
```

## 기술 스택

| 구분 | 기술 | 버전 | 용도 |
|------|------|------|------|
| **Framework** | Spring Boot | 3.2.x | 웹 애플리케이션 |
| **Language** | Java | 17 | 개발 언어 |
| **Database** | PostgreSQL | 15.x | 메인 데이터 저장소 |
| **Cache** | Redis | 7.x | 분산 락, 캐싱, 통계 |
| **Message Queue** | Apache Kafka | 3.x | 이벤트 스트리밍 |
| **ORM** | Spring Data JPA | - | 데이터 접근 |
| **ID Generation** | Snowflake | Custom | 분산 ID 생성 |

## 데이터베이스 설계

### ERD

```
┌──────────────────┐         ┌──────────────────┐
│ coupon_policies  │────────<│  coupon_issues   │
├──────────────────┤         ├──────────────────┤
│ id (PK)         │         │ id (PK)          │
│ coupon_code     │         │ policy_id (FK)   │
│ discount_type   │         │ user_id          │
│ discount_value  │         │ status           │
│ max_discount    │         │ reservation_id   │
│ valid_from      │         │ order_id         │
│ valid_until     │         │ issued_at        │
│ applicable_rule │         │ reserved_at      │
│ max_issue_count │         │ used_at          │
│ distribution_type│         │ expires_at       │
└──────────────────┘         └──────────────────┘
         │
         │
         ▼
┌──────────────────┐
│ coupon_statistics│
├──────────────────┤
│ id (PK)         │
│ policy_id       │
│ date            │
│ total_issued    │
│ total_used      │
│ discount_amount │
└──────────────────┘
```

### 인덱싱 전략

```sql
-- 사용자 쿠폰 조회 최적화
CREATE INDEX idx_user_status ON coupon_issues(user_id, status);
CREATE INDEX idx_user_active ON coupon_issues(user_id, expires_at)
    WHERE status = 'ISSUED';

-- 예약 조회 최적화
CREATE INDEX idx_reservation ON coupon_issues(reservation_id)
    WHERE reservation_id IS NOT NULL;

-- 타임아웃 체크 최적화
CREATE INDEX idx_timeout_check ON coupon_issues(status, reserved_at)
    WHERE status = 'RESERVED';

-- JSON 쿼리 최적화 (PostgreSQL GIN Index)
CREATE INDEX idx_applicable_items ON coupon_policies
    USING GIN (applicable_rule);
```

## API 명세

### 쿠폰 예약 API

**POST** `/api/coupons/reserve`

Request:
```json
{
  "reservationId": "RES-2024-1201-001",
  "userId": 12345,
  "couponId": 67890
}
```

Response (Success):
```json
{
  "success": true,
  "reservationId": "RES-2024-1201-001",
  "couponId": 67890,
  "discountAmount": 5000,
  "message": "쿠폰이 예약되었습니다",
  "reservedUntil": "2024-12-01T10:30:00"
}
```

### 쿠폰 다운로드 API

**POST** `/api/coupons/download`

Request:
```json
{
  "couponCode": "WELCOME2024",
  "userId": 12345
}
```

Response:
```json
{
  "couponIssueId": 789456,
  "couponCode": "WELCOME2024",
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "maxDiscountAmount": 5000,
  "expiresAt": "2025-09-10T23:59:59"
}
```

### 사용 가능 쿠폰 조회 API

**GET** `/api/coupons/available?userId={userId}&itemIds={itemIds}`

Response:
```json
{
  "coupons": [
    {
      "couponIssueId": 123,
      "couponName": "신규 회원 10% 할인",
      "discountDisplay": "10% 할인 (최대 5,000원)",
      "expiresAt": "2025-09-10T23:59:59",
      "remainingDays": 280
    }
  ]
}
```

## 이벤트 스키마

### PaymentCompletedEvent (수신)

Topic: `payment-completed`

```json
{
  "paymentId": "PAY-2024-1201-001",
  "reservationId": "RES-2024-1201-001",
  "orderId": "ORD-2024-1201-001",
  "paymentKey": "toss_payment_key",
  "amount": 45000,
  "method": "CARD",
  "paidAt": "2024-12-01T10:25:00"
}
```

### CouponUsedEvent (발행)

Topic: `coupon-used`

```json
{
  "couponIssueId": 67890,
  "reservationId": "RES-2024-1201-001",
  "orderId": "ORD-2024-1201-001",
  "userId": 12345,
  "actualDiscountAmount": 5000,
  "usedAt": "2024-12-01T10:25:00"
}
```

## 설정 및 실행

### 환경 변수

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/coupon_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  redis:
    host: localhost
    port: 6379

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: coupon-service

coupon:
  reservation:
    timeout: 10m  # 예약 타임아웃
  rate-limit:
    per-minute: 5
    per-hour: 20
```

### 실행

```bash
# 개발 환경
./gradlew bootRun --args='--spring.profiles.active=dev'

# Docker Compose
docker-compose up -d

# 테스트
./gradlew test
```

### 모니터링

- **Actuator Endpoints**: `/actuator/health`, `/actuator/metrics`
- **통계 대시보드**: `/api/coupons/statistics/dashboard`
- **Redis 모니터링**: Redis Commander (http://localhost:8081)

---

## 최근 개선사항

### 성능 최적화
- **N+1 쿼리 문제 해결**: 배치 조회를 통해 100개 쿠폰 조회 시 쿼리 수 101개 → 1개로 감소
- **트랜잭션 경계 최적화**: Private 메서드 @Transactional 문제 해결
- **Redis 캐싱**: 자주 조회되는 정책 데이터 캐싱

### 동시성 제어 강화
- **CouponStockService**: Redis 기반 원자적 재고 관리
- **ConcurrentCouponIssueService**: 동시성 안전 발급 서비스
- **자동 롤백**: 실패 시 재고 자동 복구

### 자동화
- **CouponExpiryScheduler**: 매일 자정 만료 쿠폰 자동 처리
- **예약 타임아웃 처리**: 5분마다 타임아웃된 예약 자동 복구
- **배치 처리**: 대량 데이터 효율적 처리

## 트러블슈팅 가이드

### 재고 불일치 문제
```bash
# Redis 재고 동기화
curl -X POST http://localhost:8080/admin/stock/sync

# 재고 확인
redis-cli GET coupon:stock:{policyId}
```

### 쿠폰 만료 처리
```bash
# 수동 만료 처리
curl -X POST http://localhost:8080/admin/coupons/expire

# 스케줄러 상태 확인
curl http://localhost:8080/actuator/scheduledtasks
```

### 동시성 이슈
```bash
# 분산 락 확인
redis-cli KEYS "coupon:lock:*"

# 락 강제 해제 (주의!)
redis-cli DEL coupon:lock:{couponId}
```

## 성능 벤치마크

| 시나리오 | 처리량 | 응답시간 | 비고 |
|---------|--------|----------|------|
| 단일 발급 | 500 req/s | 10ms | Redis 캐시 적용 |
| 배치 발급 (100개) | 50 req/s | 200ms | 분산 락 적용 |
| 선착순 발급 | 1000 req/s | 5ms | 재고 정확성 100% |
| 쿠폰 조회 | 2000 req/s | 3ms | N+1 해결 |

## 관련 문서
- [API 명세서](docs/api/REST_API_SPECIFICATION.md)
- [헥사고날 아키텍처](docs/architecture/HEXAGONAL_ARCHITECTURE.md)
- [시스템 아키텍처](docs/architecture/SYSTEM_ARCHITECTURE.md)
- [쿠폰 발급 기능](docs/features/COUPON_ISSUE.md)
- [쿠폰 사용 기능](docs/features/COUPON_USE.md)

**Version**: 1.1.0
**Team**: TeamBind
**Last Updated**: 2024-12-02

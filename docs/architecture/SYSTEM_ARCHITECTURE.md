# 시스템 아키텍처

## 개요

쿠폰 서비스는 마이크로서비스 아키텍처를 채택하여 독립적으로 배포 가능한 서비스로 설계되었습니다. 헥사고날 아키텍처를 기반으로 비즈니스 로직과 기술적 구현을 분리하여 유연성과 확장성을 확보했습니다.

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                         Client Layer                         │
│                    (Web, Mobile, Partners)                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                      API Gateway                             │
│                  (Rate Limiting, Auth)                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Coupon Service                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                 Adapter Layer (In)                   │   │
│  │          Web Controllers / Event Listeners           │   │
│  └──────────────────────┬───────────────────────────────┘   │
│  ┌──────────────────────▼───────────────────────────────┐   │
│  │              Application Layer                       │   │
│  │          Use Cases / Service Logic                   │   │
│  └──────────────────────┬───────────────────────────────┘   │
│  ┌──────────────────────▼───────────────────────────────┐   │
│  │                Domain Layer                          │   │
│  │         Entities / Business Rules                    │   │
│  └──────────────────────┬───────────────────────────────┘   │
│  ┌──────────────────────▼───────────────────────────────┐   │
│  │               Adapter Layer (Out)                    │   │
│  │      Persistence / Message / External APIs           │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
┌──────────┐    ┌──────────┐    ┌──────────┐
│PostgreSQL│    │  Redis   │    │  Kafka   │
└──────────┘    └──────────┘    └──────────┘
```

## 핵심 컴포넌트

### 1. Adapter Layer (In)

**Web Controllers**
- `CouponPolicyController`: 쿠폰 정책 관리 API
- `CouponIssueController`: 쿠폰 발급 API
- `CouponUseController`: 쿠폰 사용/예약 API
- `CouponStatisticsController`: 통계 조회 API

**Event Listeners**
- `PaymentEventConsumer`: 결제 이벤트 처리
- 비동기 메시지 처리 및 재시도 로직 포함

### 2. Application Layer

**Use Cases**
- `CreateCouponPolicyUseCase`: 쿠폰 정책 생성
- `IssueCouponUseCase`: 쿠폰 발급 처리
- `UseCouponUseCase`: 쿠폰 사용/예약
- `GetCouponStatisticsUseCase`: 통계 조회

**Services**
- 비즈니스 로직 구현
- 트랜잭션 관리
- 도메인 이벤트 발행

### 3. Domain Layer

**Entities**
- `CouponPolicy`: 쿠폰 정책 도메인 모델
- `CouponIssue`: 발급된 쿠폰 도메인 모델
- `CouponReservation`: 쿠폰 예약 도메인 모델

**Value Objects**
- `DiscountType`: 할인 타입 (FIXED_AMOUNT, PERCENTAGE)
- `IssueType`: 발급 타입 (CODE, DIRECT)
- `CouponStatus`: 쿠폰 상태 (ISSUED, RESERVED, USED, EXPIRED, CANCELLED)

### 4. Adapter Layer (Out)

**Persistence Adapters**
- `CouponPolicyPersistenceAdapter`: 정책 저장소
- `CouponIssuePersistenceAdapter`: 발급 쿠폰 저장소
- `CouponReservationPersistenceAdapter`: 예약 저장소

**Cache Adapters**
- `RedisDistributedLock`: 분산 락 구현
- `StatisticsCache`: 통계 캐싱

**Message Adapters**
- `KafkaEventPublisher`: 이벤트 발행
- `PaymentServiceAdapter`: 결제 서비스 연동

## 데이터 흐름

### 1. 쿠폰 발급 플로우
```
Client → API Gateway → CouponIssueController
→ IssueCouponUseCase → CouponPolicy Domain
→ RedisDistributedLock (동시성 제어)
→ CouponIssuePersistenceAdapter → PostgreSQL
→ KafkaEventPublisher → Kafka
→ Response → Client
```

### 2. 쿠폰 사용 플로우
```
Payment Service → Kafka → PaymentEventConsumer
→ ProcessPaymentUseCase → CouponIssue Domain
→ CouponStatus 업데이트
→ CouponIssuePersistenceAdapter → PostgreSQL
→ 통계 업데이트 → Redis Cache
```

### 3. 통계 조회 플로우
```
Client → API Gateway → CouponStatisticsController
→ GetCouponStatisticsUseCase
→ Redis Cache (캐시 히트 시)
  ↓ (캐시 미스 시)
→ 집계 쿼리 → PostgreSQL
→ Redis Cache 업데이트
→ Response → Client
```

## 확장성 고려사항

### 수평적 확장
- 무상태 설계로 인스턴스 증설 가능
- Redis를 통한 세션 및 캐시 공유
- 데이터베이스 연결 풀 최적화

### 성능 최적화
- Redis 캐싱으로 읽기 부하 분산
- 비동기 이벤트 처리로 응답 시간 개선
- 데이터베이스 인덱스 최적화

### 장애 대응
- Circuit Breaker 패턴 적용
- 재시도 및 백오프 전략
- Dead Letter Queue 활용

## 보안 고려사항

### API 보안
- JWT 기반 인증
- Rate Limiting
- CORS 정책 설정

### 데이터 보안
- 쿠폰 코드 해싱 저장
- SQL Injection 방지
- 민감 정보 암호화

### 감사 및 모니터링
- 모든 API 요청 로깅
- 쿠폰 발급/사용 감사 로그
- 메트릭 수집 및 알람
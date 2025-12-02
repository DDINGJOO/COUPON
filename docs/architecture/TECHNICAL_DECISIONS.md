# 기술적 의사결정

## 1. 아키텍처 패턴

### 헥사고날 아키텍처 채택

**결정 사항**
- 헥사고날 아키텍처(포트와 어댑터) 패턴 적용

**근거**
- 비즈니스 로직과 기술적 세부사항 분리
- 테스트 용이성 향상
- 외부 시스템 변경에 대한 유연성
- DDD(Domain-Driven Design)와의 자연스러운 통합

**대안**
- Layered Architecture: 단순하지만 계층 간 의존성 문제
- Clean Architecture: 헥사고날과 유사하지만 더 복잡
- MVC Pattern: 웹 애플리케이션에 적합하지만 도메인 로직 분리 어려움

## 2. 데이터베이스

### PostgreSQL 선택

**결정 사항**
- 주 데이터베이스로 PostgreSQL 15 사용

**근거**
- ACID 트랜잭션 보장
- JSON 타입 지원으로 유연한 스키마 설계
- 파티셔닝 및 인덱싱 기능 우수
- 대용량 데이터 처리 성능
- 풍부한 확장 기능 (pg_stat_statements, pg_trgm 등)

**대안**
- MySQL: 널리 사용되지만 고급 기능 부족
- MongoDB: NoSQL이지만 트랜잭션 지원 약함
- DynamoDB: AWS 종속성 및 비용 문제

## 3. 캐싱 전략

### Redis 도입

**결정 사항**
- 캐싱 및 분산 락을 위해 Redis 7 사용

**근거**
- 인메모리 성능
- 분산 락 구현을 위한 Lua 스크립트 지원
- Pub/Sub 기능
- 데이터 구조 다양성 (String, Hash, Set, Sorted Set)
- 클러스터링 지원

**구현**
```java
// 분산 락
@Component
public class RedisDistributedLock {
    public boolean tryLock(String key, String value, Duration timeout) {
        String script =
            "if redis.call('get',KEYS[1]) == ARGV[1] then " +
            "   return redis.call('del',KEYS[1]) " +
            "else " +
            "   return 0 " +
            "end";
        // Lua 스크립트로 원자성 보장
    }
}

// 캐싱
@Cacheable(value = "statistics:realtime", key = "#policyId")
public RealtimeStatistics getRealtimeStatistics(Long policyId) {
    // 60초 TTL 적용
}
```

## 4. 메시지 큐

### Apache Kafka 선택

**결정 사항**
- 이벤트 스트리밍 플랫폼으로 Kafka 사용

**근거**
- 높은 처리량과 낮은 지연시간
- 메시지 순서 보장 (파티션 단위)
- 메시지 영속성 및 재처리 가능
- 수평적 확장 용이
- 이벤트 소싱 패턴 구현 가능

**대안**
- RabbitMQ: 단순하지만 처리량 제한
- AWS SQS: AWS 종속성
- Redis Pub/Sub: 메시지 영속성 없음

## 5. 동시성 제어

### 분산 락 구현

**결정 사항**
- Redis 기반 분산 락으로 쿠폰 발급 동시성 제어

**근거**
- 멀티 인스턴스 환경에서 동작
- 타임아웃 기반 자동 해제
- 원자적 연산 보장

**구현 예시**
```java
@Override
@Transactional
public CouponIssueResponse issueByCode(String couponCode, Long userId) {
    String lockKey = "coupon:issue:" + couponCode + ":" + userId;
    String lockValue = UUID.randomUUID().toString();

    if (!distributedLock.tryLock(lockKey, lockValue, Duration.ofSeconds(5))) {
        throw new CouponConcurrencyException("쿠폰 발급 처리 중");
    }

    try {
        // 쿠폰 발급 로직
    } finally {
        distributedLock.unlock(lockKey, lockValue);
    }
}
```

## 6. API 설계

### RESTful API

**결정 사항**
- REST 아키텍처 스타일 준수

**근거**
- HTTP 표준 활용
- 무상태성으로 확장성 확보
- 캐싱 용이
- 널리 알려진 패턴

**설계 원칙**
- 리소스 중심 URL 설계
- HTTP 메서드 의미 준수
- 상태 코드 활용
- HATEOAS 부분 적용

## 7. 트랜잭션 전략

### 보상 트랜잭션 패턴

**결정 사항**
- 분산 트랜잭션 대신 보상 트랜잭션 사용

**근거**
- 2PC(Two-Phase Commit) 회피
- 시스템 간 느슨한 결합
- 장애 격리
- 성능 향상

**구현**
```java
public void processCouponUsage(PaymentEvent event) {
    try {
        // 쿠폰 사용 처리
        useCoupon(event.getCouponId());

    } catch (Exception e) {
        // 보상 트랜잭션
        rollbackCoupon(event.getCouponId());
        publishCompensationEvent(event);
    }
}
```

## 8. 모니터링

### Micrometer + Prometheus

**결정 사항**
- Micrometer로 메트릭 수집, Prometheus로 저장

**근거**
- Spring Boot 네이티브 지원
- 다양한 메트릭 백엔드 지원
- Grafana와 통합 용이
- 커스텀 메트릭 정의 가능

**메트릭 예시**
```java
@Component
public class CouponMetrics {
    private final MeterRegistry meterRegistry;

    public void recordCouponIssued(String policyName) {
        meterRegistry.counter("coupon.issued",
            "policy", policyName).increment();
    }

    public void recordProcessingTime(long duration) {
        meterRegistry.timer("coupon.processing.time")
            .record(duration, TimeUnit.MILLISECONDS);
    }
}
```

## 9. 보안

### JWT + Spring Security

**결정 사항**
- JWT 토큰 기반 인증
- Spring Security 프레임워크 활용

**근거**
- 무상태 인증
- 마이크로서비스 간 토큰 공유
- 표준 기반 구현
- 세밀한 권한 제어

## 10. 테스트 전략

### 테스트 피라미드

**결정 사항**
- 단위 테스트 > 통합 테스트 > E2E 테스트 비율

**테스트 도구**
- JUnit 5: 단위 테스트
- Mockito: Mock 객체
- RestAssured: API 테스트
- Testcontainers: 통합 테스트 (현재 비활성화)
- Docker Compose: 로컬 통합 테스트

**커버리지 목표**
- 단위 테스트: 80% 이상
- 통합 테스트: 핵심 플로우
- E2E 테스트: 주요 시나리오
# 아키텍처 트레이드오프

## 1. 헥사고날 아키텍처

### 선택한 것
- 계층 분리와 포트/어댑터 패턴
- 도메인 모델과 JPA 엔티티 분리
- 인터페이스를 통한 의존성 역전

### 얻은 것
- **테스트 용이성**: Mock을 활용한 단위 테스트 가능
- **유연성**: 기술 스택 변경 시 어댑터만 교체
- **명확한 경계**: 각 계층의 책임 명확화
- **도메인 중심 설계**: 비즈니스 로직에 집중

### 포기한 것
- **단순성**: 초기 구현 복잡도 증가
- **성능**: 계층 간 매핑 오버헤드
- **개발 속도**: 보일러플레이트 코드 증가
- **학습 곡선**: 팀원 교육 필요

### 완화 전략
```java
// 매핑 라이브러리(MapStruct) 도입으로 보일러플레이트 감소
@Mapper(componentModel = "spring")
public interface CouponMapper {
    CouponPolicy toDomain(CouponPolicyJpaEntity entity);
    CouponPolicyJpaEntity toEntity(CouponPolicy domain);
}
```

## 2. 동시성 제어 전략

### 선택한 것
- Redis 분산 락
- 비관적 락 (데이터베이스)
- 타임아웃 기반 자동 해제

### 얻은 것
- **데이터 일관성**: 중복 발급 방지
- **분산 환경 지원**: 멀티 인스턴스 대응
- **자동 복구**: 타임아웃 시 락 해제

### 포기한 것
- **처리량**: 락 대기로 인한 성능 저하
- **복잡성**: 데드락 가능성
- **가용성**: 락 대기 중 서비스 지연

### 완화 전략
```java
// 재시도 로직과 백오프 전략
@Retryable(
    value = {CouponConcurrencyException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
public CouponIssueResponse issueWithRetry(CouponIssueCommand command) {
    // 재시도 로직으로 락 경합 완화
}

// 락 획득 실패 시 큐잉
public void queueCouponIssue(CouponIssueCommand command) {
    kafkaTemplate.send("coupon.issue.queue", command);
}
```

## 3. 캐싱 전략

### 선택한 것
- Redis 캐싱
- TTL 기반 만료 (60초 ~ 300초)
- 캐시 무효화 이벤트

### 얻은 것
- **응답 속도**: 캐시 히트 시 빠른 응답
- **부하 감소**: 데이터베이스 쿼리 감소
- **확장성**: 읽기 성능 향상

### 포기한 것
- **실시간성**: TTL 동안 데이터 불일치
- **메모리 비용**: Redis 인프라 비용
- **복잡성**: 캐시 무효화 로직

### 완화 전략
```java
// 캐시 워밍업
@EventListener(ApplicationReadyEvent.class)
public void warmupCache() {
    // 자주 사용되는 데이터 미리 로드
    statisticsService.preloadPopularPolicies();
}

// 캐시 무효화 이벤트
@EventListener
public void handleCouponIssued(CouponIssuedEvent event) {
    cacheManager.evict("statistics:realtime:" + event.getPolicyId());
}
```

## 4. 이벤트 기반 아키텍처

### 선택한 것
- Kafka를 통한 비동기 통신
- 이벤트 소싱 패턴
- 최종 일관성 (Eventual Consistency)

### 얻은 것
- **느슨한 결합**: 서비스 간 독립성
- **확장성**: 이벤트 스트림 처리
- **복원력**: 장애 격리
- **감사 추적**: 이벤트 히스토리

### 포기한 것
- **즉시 일관성**: 데이터 동기화 지연
- **디버깅 어려움**: 분산 트레이싱 필요
- **복잡성**: 이벤트 순서 보장 어려움
- **중복 처리**: 멱등성 보장 필요

### 완화 전략
```java
// 멱등성 보장
@Component
public class IdempotentEventProcessor {
    private final Set<String> processedEvents = new ConcurrentHashMap<>();

    public void process(Event event) {
        String eventId = event.getEventId();
        if (!processedEvents.add(eventId)) {
            log.info("이미 처리된 이벤트: {}", eventId);
            return;
        }
        // 이벤트 처리
    }
}

// 이벤트 순서 보장
@KafkaListener(topics = "payment.events")
public void handlePaymentEvent(
    @Payload PaymentEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition) {
    // 파티션 키로 순서 보장
}
```

## 5. 데이터베이스 설계

### 선택한 것
- 정규화된 관계형 모델
- 인덱스 최적화
- 파티셔닝 준비

### 얻은 것
- **데이터 일관성**: ACID 트랜잭션
- **쿼리 유연성**: 복잡한 조인 가능
- **관계 표현**: 외래키 제약

### 포기한 것
- **쓰기 성능**: 정규화로 인한 조인
- **스케일 아웃**: 수직 확장 한계
- **스키마 유연성**: 스키마 변경 어려움

### 완화 전략
```sql
-- 읽기 전용 복제본
CREATE MATERIALIZED VIEW coupon_statistics AS
SELECT
    policy_id,
    COUNT(*) as total_issued,
    SUM(CASE WHEN status = 'USED' THEN 1 ELSE 0 END) as used_count
FROM coupon_issues
GROUP BY policy_id;

-- 파티셔닝 준비
CREATE TABLE coupon_issues_2024 PARTITION OF coupon_issues
FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
```

## 6. API 설계

### 선택한 것
- RESTful API
- JSON 페이로드
- 동기식 통신

### 얻은 것
- **표준화**: HTTP 표준 활용
- **캐싱**: HTTP 캐싱 헤더 활용
- **도구 지원**: Swagger, Postman 등
- **단순성**: 학습 곡선 낮음

### 포기한 것
- **실시간 통신**: WebSocket 미지원
- **효율성**: JSON 오버헤드
- **타입 안정성**: 스키마 검증 약함
- **양방향 통신**: 클라이언트 폴링 필요

### 완화 전략
```java
// SSE(Server-Sent Events)로 실시간 알림
@GetMapping(value = "/stream/statistics", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<RealtimeStatistics>> streamStatistics() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(sequence -> ServerSentEvent.<RealtimeStatistics>builder()
            .data(getRealtimeStatistics())
            .build());
}

// GraphQL 부분 도입 고려
@QueryMapping
public CouponPolicy couponPolicy(@Argument Long id) {
    // 오버페칭/언더페칭 문제 해결
}
```

## 7. 에러 처리

### 선택한 것
- 예외 기반 에러 처리
- 전역 예외 핸들러
- 구조화된 에러 응답

### 얻은 것
- **일관성**: 통일된 에러 형식
- **추적성**: 에러 코드와 메시지
- **사용성**: 클라이언트 친화적 응답

### 포기한 것
- **성능**: 예외 스택 트레이스 비용
- **타입 안정성**: 런타임 에러
- **복구 전략**: 자동 복구 어려움

### 완화 전략
```java
// Result 타입 패턴 부분 적용
public sealed interface Result<T>
    permits Result.Success, Result.Failure {

    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(ErrorCode error) implements Result<T> {}
}

// Circuit Breaker 패턴
@CircuitBreaker(name = "payment-service", fallbackMethod = "fallbackPayment")
public PaymentResult processPayment(PaymentRequest request) {
    // 서킷 브레이커로 장애 전파 방지
}
```

## 8. 보안

### 선택한 것
- JWT 인증
- Spring Security
- HTTPS 통신

### 얻은 것
- **무상태 인증**: 서버 세션 불필요
- **표준 준수**: OAuth 2.0 호환
- **세밀한 제어**: 메서드 레벨 보안

### 포기한 것
- **토큰 크기**: JWT 페이로드 오버헤드
- **토큰 무효화**: 만료 전 취소 어려움
- **비밀 관리**: 키 로테이션 복잡

### 완화 전략
```java
// 리프레시 토큰 전략
@Service
public class TokenService {
    public TokenPair generateTokens(User user) {
        String accessToken = generateAccessToken(user, Duration.ofMinutes(15));
        String refreshToken = generateRefreshToken(user, Duration.ofDays(7));
        return new TokenPair(accessToken, refreshToken);
    }
}

// 토큰 블랙리스트
@Component
public class TokenBlacklist {
    private final RedisTemplate<String, String> redisTemplate;

    public void revoke(String token) {
        redisTemplate.opsForValue().set(
            "blacklist:" + token,
            "revoked",
            Duration.ofHours(24)
        );
    }
}
```
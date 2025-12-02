# 쿠폰 발급

## 개요

쿠폰 발급은 정의된 쿠폰 정책을 기반으로 사용자에게 실제 쿠폰을 생성하여 제공하는 과정입니다. 코드 입력 방식과 직접 발급 방식을 지원하며, 동시성 제어를 통해 중복 발급을 방지합니다.

## 도메인 모델

### CouponIssue

```java
public class CouponIssue {
    private Long id;
    private Long policyId;               // 쿠폰 정책 ID
    private Long userId;                 // 사용자 ID
    private String couponNumber;         // 쿠폰 번호 (고유)
    private CouponStatus status;         // 쿠폰 상태
    private LocalDateTime issuedAt;      // 발급 일시
    private LocalDateTime expiryDate;    // 만료 일시
    private LocalDateTime usedAt;        // 사용 일시
    private LocalDateTime reservedAt;    // 예약 일시
    private String orderId;              // 주문 ID
    private String reservationId;        // 예약 ID
}
```

### 쿠폰 상태 (CouponStatus)

| 상태 | 설명 | 다음 가능 상태 |
|------|------|---------------|
| ISSUED | 발급됨 | RESERVED, EXPIRED, CANCELLED |
| RESERVED | 예약됨 | USED, ISSUED |
| USED | 사용됨 | CANCELLED (환불 시) |
| EXPIRED | 만료됨 | - |
| CANCELLED | 취소됨 | - |

## 발급 방식

### 1. 코드 입력 발급 (CODE)

사용자가 쿠폰 코드를 입력하여 쿠폰을 발급받는 방식

#### 처리 흐름
```
1. 쿠폰 코드 검증
2. 정책 조회 및 발급 가능 여부 확인
3. 사용자별 발급 제한 확인
4. 분산 락 획득
5. 쿠폰 발급 및 저장
6. 정책 발급 수량 업데이트
7. 발급 이벤트 발행
8. 락 해제
```

#### 구현 코드
```java
@Service
@RequiredArgsConstructor
@Transactional
public class IssueCouponByCodeService {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final RedisDistributedLock distributedLock;
    private final EventPublisher eventPublisher;

    public CouponIssueResponse issueByCode(String couponCode, Long userId) {
        // 1. 분산 락 획득
        String lockKey = "coupon:issue:" + couponCode + ":" + userId;
        String lockValue = UUID.randomUUID().toString();

        if (!distributedLock.tryLock(lockKey, lockValue, Duration.ofSeconds(5))) {
            throw new CouponConcurrencyException("쿠폰 발급 처리 중입니다");
        }

        try {
            // 2. 정책 조회 (비관적 락)
            CouponPolicy policy = loadCouponPolicyPort
                .loadByCodeWithLock(couponCode)
                .orElseThrow(() -> new CouponCodeNotFoundException(couponCode));

            // 3. 발급 가능 여부 확인
            if (!policy.isIssuable()) {
                throw new CouponNotIssuableException(
                    "쿠폰을 발급할 수 없습니다: " + couponCode
                );
            }

            // 4. 중복 발급 확인
            if (isAlreadyIssued(policy.getId(), userId)) {
                throw new CouponAlreadyIssuedException(userId, policy.getId());
            }

            // 5. 쿠폰 발급
            CouponIssue coupon = createCouponIssue(policy, userId);
            CouponIssue savedCoupon = saveCouponIssuePort.save(coupon);

            // 6. 정책 발급 수량 증가
            policy.incrementIssueCount();
            saveCouponPolicyPort.save(policy);

            // 7. 이벤트 발행
            eventPublisher.publish(new CouponIssuedEvent(savedCoupon));

            return CouponIssueResponse.from(savedCoupon);

        } finally {
            // 8. 락 해제
            distributedLock.unlock(lockKey, lockValue);
        }
    }

    private boolean isAlreadyIssued(Long policyId, Long userId) {
        return loadCouponIssuePort
            .findByPolicyIdAndUserId(policyId, userId)
            .isPresent();
    }

    private CouponIssue createCouponIssue(CouponPolicy policy, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryDate = now.plusDays(policy.getValidDays());

        return CouponIssue.builder()
            .policyId(policy.getId())
            .userId(userId)
            .couponNumber(generateCouponNumber())
            .status(CouponStatus.ISSUED)
            .issuedAt(now)
            .expiryDate(expiryDate)
            .build();
    }

    private String generateCouponNumber() {
        return "CPN" + System.currentTimeMillis() +
               ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
```

### 2. 직접 발급 (DIRECT)

관리자가 특정 사용자들에게 직접 쿠폰을 발급하는 방식

#### 처리 흐름
```
1. 정책 조회 및 검증
2. 대상 사용자 검증
3. 배치 발급 처리
4. 발급 결과 집계
5. 이벤트 발행
```

#### 구현 코드
```java
@Service
@RequiredArgsConstructor
public class DirectIssueCouponService {

    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final EventPublisher eventPublisher;

    @Transactional
    public DirectIssueResponse issueDirectly(
            Long policyId,
            List<Long> userIds) {

        // 1. 정책 조회
        CouponPolicy policy = loadCouponPolicyPort
            .loadById(policyId)
            .orElseThrow(() -> new CouponPolicyNotFoundException(policyId));

        // 2. 발급 타입 확인
        if (policy.getIssueType() != IssueType.DIRECT) {
            throw new InvalidIssueTypeException(
                "직접 발급이 허용되지 않는 정책입니다"
            );
        }

        // 3. 배치 발급
        List<IssueResult> results = new ArrayList<>();
        for (Long userId : userIds) {
            IssueResult result = tryIssueCoupon(policy, userId);
            results.add(result);
        }

        // 4. 결과 집계
        DirectIssueResponse response = DirectIssueResponse.builder()
            .totalRequested(userIds.size())
            .successCount(countSuccess(results))
            .failureCount(countFailure(results))
            .results(results)
            .build();

        // 5. 성공한 발급에 대해 이벤트 발행
        publishSuccessEvents(results);

        return response;
    }

    private IssueResult tryIssueCoupon(CouponPolicy policy, Long userId) {
        try {
            // 중복 발급 확인
            if (isAlreadyIssued(policy.getId(), userId)) {
                return IssueResult.failure(userId, "이미 발급된 쿠폰");
            }

            // 쿠폰 발급
            CouponIssue coupon = createCouponIssue(policy, userId);
            CouponIssue saved = saveCouponIssuePort.save(coupon);

            return IssueResult.success(userId, saved.getId());

        } catch (Exception e) {
            log.error("쿠폰 발급 실패 - userId: {}, error: {}",
                userId, e.getMessage());
            return IssueResult.failure(userId, e.getMessage());
        }
    }
}
```

## 동시성 제어

### 분산 락 구현

Redis를 활용한 분산 락으로 동시 발급 제어

```java
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private final StringRedisTemplate redisTemplate;

    public boolean tryLock(String key, String value, Duration timeout) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(
                key, value, timeout
            )
        );
    }

    public void unlock(String key, String value) {
        String script =
            "if redis.call('get',KEYS[1]) == ARGV[1] then " +
            "   return redis.call('del',KEYS[1]) " +
            "else " +
            "   return 0 " +
            "end";

        redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(key),
            value
        );
    }
}
```

### 비관적 락

데이터베이스 레벨에서 추가 보호

```java
@Repository
public interface CouponPolicyRepository extends JpaRepository<CouponPolicyJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CouponPolicyJpaEntity p WHERE p.couponCode = :code")
    Optional<CouponPolicyJpaEntity> findByCodeWithLock(@Param("code") String code);
}
```

## 쿠폰 번호 생성

### 생성 규칙
- 형식: CPN + 타임스탬프 + 랜덤값
- 고유성 보장
- 추측 불가능

```java
@Component
public class CouponNumberGenerator {

    private static final String PREFIX = "CPN";
    private static final int RANDOM_LENGTH = 4;

    public String generate() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = generateRandomString(RANDOM_LENGTH);
        return PREFIX + timestamp + random;
    }

    private String generateRandomString(int length) {
        return ThreadLocalRandom.current()
            .ints(length, 0, 36)
            .mapToObj(i -> Integer.toString(i, 36).toUpperCase())
            .collect(Collectors.joining());
    }
}
```

## 만료 처리

### 자동 만료

```java
@Component
@RequiredArgsConstructor
public class CouponExpirationScheduler {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final EventPublisher eventPublisher;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void expireOldCoupons() {
        LocalDateTime now = LocalDateTime.now();

        List<CouponIssue> expiredCoupons = loadCouponIssuePort
            .findExpiredCoupons(now);

        for (CouponIssue coupon : expiredCoupons) {
            coupon.expire();
            saveCouponIssuePort.save(coupon);
            eventPublisher.publish(new CouponExpiredEvent(coupon));
        }

        log.info("만료된 쿠폰 처리 완료: {}개", expiredCoupons.size());
    }
}
```

## 발급 이력 관리

### 감사 로그

```java
@Entity
@Table(name = "coupon_issue_audit")
public class CouponIssueAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long couponId;
    private Long userId;
    private String action;  // ISSUED, RESERVED, USED, EXPIRED, CANCELLED
    private String details;
    private String clientIp;
    private LocalDateTime createdAt;
}
```

## 성능 최적화

### 배치 발급

대량 발급 시 배치 처리로 성능 향상

```java
@Service
public class BatchCouponIssueService {

    @Transactional
    public void issueBatch(Long policyId, List<Long> userIds) {
        int batchSize = 100;

        for (int i = 0; i < userIds.size(); i += batchSize) {
            List<Long> batch = userIds.subList(
                i,
                Math.min(i + batchSize, userIds.size())
            );
            processBatch(policyId, batch);
        }
    }

    private void processBatch(Long policyId, List<Long> userIds) {
        List<CouponIssue> coupons = userIds.stream()
            .map(userId -> createCouponIssue(policyId, userId))
            .collect(Collectors.toList());

        saveCouponIssuePort.saveAll(coupons);
    }
}
```

### 캐싱

자주 조회되는 발급 정보 캐싱

```java
@Cacheable(value = "userCoupons", key = "#userId")
public List<CouponIssue> getUserCoupons(Long userId) {
    return loadCouponIssuePort.findByUserId(userId);
}

@CacheEvict(value = "userCoupons", key = "#userId")
public void evictUserCouponsCache(Long userId) {
    // 캐시 무효화
}
```

## 모니터링

### 주요 메트릭
- 분당 발급 수
- 발급 성공률
- 평균 발급 시간
- 락 획득 실패율

### 알람 조건
- 발급 실패율 > 5%
- 락 획득 실패율 > 10%
- 발급 시간 > 1초

## 최근 개선사항 (2024-12)

### 1. 선착순(FCFS) 발급 기능 추가
EVENT 타입으로 선착순 쿠폰 발급 지원

```java
@Service
public class FCFSCouponIssueService {

    public CouponIssueResponse issueFCFS(Long policyId, Long userId) {
        // Redis atomic decrement로 재고 관리
        Long remaining = redisTemplate.opsForValue()
            .decrement("coupon:stock:" + policyId);

        if (remaining < 0) {
            // 재고 복구
            redisTemplate.opsForValue().increment("coupon:stock:" + policyId);
            throw new StockExhaustedException("재고 소진");
        }

        // 쿠폰 발급 처리
        return issueCoupon(policyId, userId);
    }
}
```

### 2. 배치 발급 성능 최적화
- 병렬 처리로 대량 발급 성능 개선
- 분산 락 기반 동시성 제어

```java
@Service
public class ConcurrentCouponIssueService {

    public BatchIssueResult issueBatchWithLock(
        Long policyId,
        List<Long> userIds,
        int batchSize
    ) {
        return userIds.parallelStream()
            .map(userId -> issueWithDistributedLock(policyId, userId))
            .collect(BatchIssueResult.collector());
    }
}
```

### 3. 만료 처리 자동화 개선
- 배치 단위 처리로 성능 향상
- 상태만 변경 (삭제하지 않음)

```java
@Component
public class CouponExpiryScheduler {

    @Scheduled(cron = "${coupon.scheduler.expiry.cron:0 0 0 * * *}")
    @Transactional
    public void processExpiredCoupons() {
        int batchSize = 1000;
        int processedCount = 0;

        do {
            processedCount = jdbcTemplate.update(
                "UPDATE coupon_issues SET status = 'EXPIRED' " +
                "WHERE status = 'ISSUED' AND expires_at < NOW() " +
                "LIMIT ?",
                batchSize
            );
        } while (processedCount == batchSize);
    }
}
```

### 4. Redis 기반 원자적 재고 관리
Lua 스크립트를 활용한 원자적 재고 차감

```lua
-- check_and_decrement.lua
local stock_key = KEYS[1]
local quantity = tonumber(ARGV[1])
local current = redis.call('get', stock_key)

if not current then
    return -1
end

current = tonumber(current)
if current >= quantity then
    redis.call('decrby', stock_key, quantity)
    return current - quantity
else
    return -1
end
```

### 5. 예약 타임아웃 자동 해제
30분 이상 미사용 예약 자동 해제

```java
@Scheduled(cron = "0 */10 * * * *")
public void releaseExpiredReservations() {
    LocalDateTime timeout = LocalDateTime.now().minusMinutes(30);

    int released = jdbcTemplate.update(
        "UPDATE coupon_issues SET status = 'ISSUED', " +
        "reservation_id = NULL, reserved_at = NULL " +
        "WHERE status = 'RESERVED' AND reserved_at < ?",
        timeout
    );

    log.info("예약 타임아웃 해제: {}건", released);
}
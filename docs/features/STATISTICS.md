# 통계 및 모니터링

## 개요

쿠폰 서비스의 실시간 통계 제공 및 모니터링 시스템입니다. Redis 캐싱을 통해 빠른 응답 속도를 보장하고, 다양한 차원의 통계 데이터를 제공합니다.

## 통계 유형

### 1. 실시간 통계 (Realtime Statistics)

쿠폰 정책별 실시간 발급 및 사용 현황

```java
public class RealtimeStatistics {
    private Long policyId;              // 정책 ID
    private String policyName;          // 정책 이름
    private Integer maxIssueCount;      // 최대 발급 수량
    private Integer currentIssueCount;  // 현재 발급 수량
    private Integer usedCount;          // 사용 수량
    private Integer reservedCount;      // 예약 수량
    private Integer availableCount;     // 남은 수량
    private Double usageRate;           // 사용률 (%)
    private LocalDateTime lastIssuedAt; // 마지막 발급 시각
    private LocalDateTime lastUsedAt;   // 마지막 사용 시각
}
```

### 2. 전체 통계 (Global Statistics)

시스템 전체 쿠폰 현황

```java
public class GlobalStatistics {
    private Integer totalPolicies;       // 전체 정책 수
    private Long totalIssuedCoupons;     // 전체 발급 쿠폰 수
    private Long totalUsedCoupons;       // 전체 사용 쿠폰 수
    private Long totalReservedCoupons;   // 전체 예약 쿠폰 수
    private Long totalExpiredCoupons;    // 전체 만료 쿠폰 수
    private Double overallUsageRate;     // 전체 사용률
    private Map<String, Long> statusDistribution;  // 상태별 분포
    private Map<String, Long> typeDistribution;    // 타입별 분포
}
```

### 3. 사용자 통계 (User Statistics)

사용자별 쿠폰 사용 현황

```java
public class UserStatistics {
    private Long userId;                 // 사용자 ID
    private Integer totalCoupons;        // 총 보유 쿠폰 수
    private Integer availableCoupons;    // 사용 가능 쿠폰 수
    private Integer usedCoupons;         // 사용한 쿠폰 수
    private Integer expiredCoupons;      // 만료된 쿠폰 수
    private BigDecimal totalDiscountAmount; // 총 할인 금액
    private LocalDateTime firstIssuedAt; // 첫 발급 시각
    private LocalDateTime lastUsedAt;    // 마지막 사용 시각
    private Map<String, Integer> couponsByStatus; // 상태별 쿠폰 수
}
```

## 통계 수집

### 실시간 집계

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponStatisticsService implements GetCouponStatisticsUseCase {

    private final CouponPolicyRepository policyRepository;
    private final CouponIssueRepository issueRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Cacheable(value = "statistics:realtime", key = "#policyId")
    public RealtimeStatistics getRealtimeStatistics(Long policyId) {
        log.info("실시간 통계 조회: policyId={}", policyId);

        // 정책 조회
        CouponPolicyJpaEntity policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new CouponPolicyNotFoundException(policyId));

        // 통계 집계
        Map<CouponStatus, Long> statusCounts = issueRepository
            .countByPolicyIdGroupByStatus(policyId);

        // 통계 생성
        return RealtimeStatistics.builder()
            .policyId(policy.getId())
            .policyName(policy.getCouponName())
            .maxIssueCount(policy.getMaxIssueCount())
            .currentIssueCount(policy.getCurrentIssueCount())
            .usedCount(statusCounts.getOrDefault(CouponStatus.USED, 0L).intValue())
            .reservedCount(statusCounts.getOrDefault(CouponStatus.RESERVED, 0L).intValue())
            .availableCount(calculateAvailableCount(policy))
            .usageRate(calculateUsageRate(statusCounts, policy.getCurrentIssueCount()))
            .lastIssuedAt(getLastIssuedTime(policyId))
            .lastUsedAt(getLastUsedTime(policyId))
            .build();
    }

    private Double calculateUsageRate(Map<CouponStatus, Long> statusCounts,
                                     Integer totalIssued) {
        if (totalIssued == 0) return 0.0;

        Long usedCount = statusCounts.getOrDefault(CouponStatus.USED, 0L);
        return (usedCount * 100.0) / totalIssued;
    }
}
```

### 전체 통계 집계

```java
@Override
@Cacheable(value = "statistics:global", key = "'global'")
public GlobalStatistics getGlobalStatistics() {
    log.info("전체 통계 조회");

    // 정책 통계
    Integer totalPolicies = policyRepository.countAll();

    // 쿠폰 통계
    Map<CouponStatus, Long> statusDistribution = issueRepository
        .countGroupByStatus();

    Long totalIssued = statusDistribution.values().stream()
        .mapToLong(Long::longValue)
        .sum();

    Long totalUsed = statusDistribution.getOrDefault(CouponStatus.USED, 0L);

    // 타입별 분포
    Map<IssueType, Long> typeDistribution = issueRepository
        .countGroupByIssueType();

    return GlobalStatistics.builder()
        .totalPolicies(totalPolicies)
        .totalIssuedCoupons(totalIssued)
        .totalUsedCoupons(totalUsed)
        .totalReservedCoupons(statusDistribution.getOrDefault(CouponStatus.RESERVED, 0L))
        .totalExpiredCoupons(statusDistribution.getOrDefault(CouponStatus.EXPIRED, 0L))
        .overallUsageRate(totalIssued > 0 ? (totalUsed * 100.0) / totalIssued : 0.0)
        .statusDistribution(convertToStringMap(statusDistribution))
        .typeDistribution(convertToStringMap(typeDistribution))
        .build();
}
```

## 캐싱 전략

### Redis 캐싱 구성

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 60000  # 기본 TTL: 60초
      cache-null-values: true
      key-prefix: "coupon:"

  redis:
    host: localhost
    port: 6379
    timeout: 2000
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

### 캐시 구성

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(60))
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
            .withCacheConfiguration("statistics:realtime",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(60)))
            .withCacheConfiguration("statistics:global",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(300)))
            .withCacheConfiguration("statistics:user",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(120)));
    }
}
```

### 캐시 무효화

```java
@Component
@RequiredArgsConstructor
public class StatisticsCacheManager {

    private final CacheManager cacheManager;

    @EventListener
    public void handleCouponIssued(CouponIssuedEvent event) {
        evictRealtimeCache(event.getPolicyId());
        evictGlobalCache();
        evictUserCache(event.getUserId());
    }

    @EventListener
    public void handleCouponUsed(CouponUsedEvent event) {
        evictRealtimeCache(event.getPolicyId());
        evictGlobalCache();
        evictUserCache(event.getUserId());
    }

    private void evictRealtimeCache(Long policyId) {
        Cache cache = cacheManager.getCache("statistics:realtime");
        if (cache != null) {
            cache.evict(policyId);
        }
    }

    private void evictGlobalCache() {
        Cache cache = cacheManager.getCache("statistics:global");
        if (cache != null) {
            cache.clear();
        }
    }

    private void evictUserCache(Long userId) {
        Cache cache = cacheManager.getCache("statistics:user");
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
```

## 일일 통계 배치

### 스케줄러

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyStatisticsScheduler {

    private final DailyStatisticsRepository dailyStatsRepository;
    private final CouponIssueRepository issueRepository;

    @Scheduled(cron = "0 0 1 * * *")  // 매일 새벽 1시
    public void calculateDailyStatistics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("일일 통계 계산 시작: {}", yesterday);

        try {
            // 발급 통계
            DailyIssueStats issueStats = calculateIssueStats(yesterday);

            // 사용 통계
            DailyUsageStats usageStats = calculateUsageStats(yesterday);

            // 저장
            DailyStatistics dailyStats = DailyStatistics.builder()
                .date(yesterday)
                .totalIssued(issueStats.getTotal())
                .totalUsed(usageStats.getTotal())
                .totalReserved(issueStats.getReserved())
                .totalExpired(calculateExpiredCount(yesterday))
                .issueByHour(issueStats.getByHour())
                .usageByHour(usageStats.getByHour())
                .topPolicies(findTopPolicies(yesterday, 10))
                .calculatedAt(LocalDateTime.now())
                .build();

            dailyStatsRepository.save(dailyStats);
            log.info("일일 통계 계산 완료");

        } catch (Exception e) {
            log.error("일일 통계 계산 실패", e);
        }
    }

    private List<TopPolicyStats> findTopPolicies(LocalDate date, int limit) {
        return issueRepository.findTopPoliciesByDate(
            date.atStartOfDay(),
            date.plusDays(1).atStartOfDay(),
            PageRequest.of(0, limit)
        );
    }
}
```

## 실시간 대시보드

### WebSocket 스트리밍

```java
@Controller
@RequiredArgsConstructor
public class StatisticsWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GetCouponStatisticsUseCase statisticsUseCase;

    @Scheduled(fixedDelay = 5000)  // 5초마다 갱신
    public void pushRealtimeStatistics() {
        GlobalStatistics stats = statisticsUseCase.getGlobalStatistics();

        DashboardUpdate update = DashboardUpdate.builder()
            .timestamp(LocalDateTime.now())
            .totalIssued(stats.getTotalIssuedCoupons())
            .totalUsed(stats.getTotalUsedCoupons())
            .usageRate(stats.getOverallUsageRate())
            .activeReservations(stats.getTotalReservedCoupons())
            .build();

        messagingTemplate.convertAndSend("/topic/dashboard", update);
    }

    @MessageMapping("/subscribe")
    @SendTo("/topic/dashboard")
    public DashboardUpdate subscribe() {
        return getCurrentDashboardUpdate();
    }
}
```

## 성능 모니터링

### Micrometer 메트릭

```java
@Component
@RequiredArgsConstructor
public class CouponMetrics {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void recordCouponIssued(CouponIssuedEvent event) {
        meterRegistry.counter("coupon.issued",
            "policy", event.getPolicyName(),
            "type", event.getIssueType().name()
        ).increment();
    }

    @EventListener
    public void recordCouponUsed(CouponUsedEvent event) {
        meterRegistry.counter("coupon.used",
            "policy", event.getPolicyName()
        ).increment();

        meterRegistry.summary("coupon.discount.amount",
            "policy", event.getPolicyName()
        ).record(event.getDiscountAmount().doubleValue());
    }

    public void recordApiLatency(String endpoint, long duration) {
        meterRegistry.timer("api.latency",
            "endpoint", endpoint
        ).record(duration, TimeUnit.MILLISECONDS);
    }
}
```

### Prometheus 설정

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: coupon-service
      environment: production
```

## 알람 설정

### 임계치 기반 알람

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class StatisticsAlertService {

    private final AlertNotificationService notificationService;

    @Scheduled(fixedDelay = 60000)  // 1분마다 체크
    public void checkAlertConditions() {
        // 발급률 체크
        checkIssueRate();

        // 에러율 체크
        checkErrorRate();

        // 응답 시간 체크
        checkResponseTime();
    }

    private void checkIssueRate() {
        double issueRate = calculateCurrentIssueRate();

        if (issueRate > 90.0) {
            Alert alert = Alert.builder()
                .level(AlertLevel.WARNING)
                .title("높은 쿠폰 발급률")
                .message(String.format("현재 발급률: %.2f%%", issueRate))
                .timestamp(LocalDateTime.now())
                .build();

            notificationService.send(alert);
        }
    }
}
```

## 리포트 생성

### 월간 리포트

```java
@Service
@RequiredArgsConstructor
public class MonthlyReportService {

    public MonthlyReport generateReport(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return MonthlyReport.builder()
            .period(yearMonth)
            .totalPoliciesCreated(countPoliciesCreated(startDate, endDate))
            .totalCouponsIssued(countCouponsIssued(startDate, endDate))
            .totalCouponsUsed(countCouponsUsed(startDate, endDate))
            .averageUsageRate(calculateAverageUsageRate(startDate, endDate))
            .topPerformingPolicies(findTopPolicies(startDate, endDate, 10))
            .dailyTrends(getDailyTrends(startDate, endDate))
            .generatedAt(LocalDateTime.now())
            .build();
    }
}
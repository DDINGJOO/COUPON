# 모니터링 및 관측성

## 개요

쿠폰 서비스의 상태를 실시간으로 모니터링하고 문제를 조기에 발견하기 위한 관측성(Observability) 전략입니다. 메트릭, 로그, 트레이싱을 통합하여 완전한 가시성을 제공합니다.

## 모니터링 스택

### 아키텍처

```
┌─────────────────────────────────────────────────┐
│                 Grafana Dashboard                │
│              (Visualization & Alerts)            │
└──────────────────┬──────────────────────────────┘
                   │
        ┌──────────┴──────────┬──────────────┐
        ▼                     ▼              ▼
┌──────────────┐    ┌──────────────┐  ┌──────────────┐
│  Prometheus  │    │     Loki     │  │    Jaeger    │
│  (Metrics)   │    │    (Logs)    │  │  (Tracing)   │
└──────┬───────┘    └──────┬───────┘  └──────┬───────┘
       │                   │                  │
       ▼                   ▼                  ▼
┌──────────────────────────────────────────────────┐
│             Coupon Service Application            │
│                  (Micrometer)                     │
└───────────────────────────────────────────────────┘
```

## 메트릭 수집

### Spring Boot Actuator 설정

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info,env
      base-path: /actuator
  endpoint:
    health:
      show-details: always
      show-components: always
      probes:
        enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
    tags:
      application: coupon-service
      environment: ${SPRING_PROFILES_ACTIVE:default}
```

### 커스텀 메트릭

```java
@Component
@RequiredArgsConstructor
public class CouponMetrics {

    private final MeterRegistry meterRegistry;

    // 카운터: 쿠폰 발급 수
    private final Counter issuedCouponsCounter = Counter.builder("coupon.issued")
        .description("Total number of coupons issued")
        .register(meterRegistry);

    // 게이지: 활성 쿠폰 수
    private final AtomicLong activeCoupons = meterRegistry
        .gauge("coupon.active", new AtomicLong(0));

    // 히스토그램: 할인 금액 분포
    private final DistributionSummary discountAmounts = DistributionSummary
        .builder("coupon.discount.amount")
        .description("Distribution of discount amounts")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry);

    // 타이머: API 응답 시간
    private final Timer apiTimer = Timer.builder("coupon.api.duration")
        .description("API response time")
        .register(meterRegistry);

    @EventListener
    public void onCouponIssued(CouponIssuedEvent event) {
        issuedCouponsCounter.increment();
        activeCoupons.incrementAndGet();

        meterRegistry.counter("coupon.issued.by.type",
            "type", event.getIssueType().name()
        ).increment();
    }

    @EventListener
    public void onCouponUsed(CouponUsedEvent event) {
        activeCoupons.decrementAndGet();
        discountAmounts.record(event.getDiscountAmount().doubleValue());

        meterRegistry.counter("coupon.used.by.policy",
            "policy", event.getPolicyName()
        ).increment();
    }

    public void recordApiCall(String endpoint, long duration) {
        apiTimer.record(duration, TimeUnit.MILLISECONDS);

        meterRegistry.timer("api.calls",
            "endpoint", endpoint,
            "method", "GET"
        ).record(duration, TimeUnit.MILLISECONDS);
    }
}
```

### Prometheus 설정

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

rule_files:
  - "rules/*.yml"

scrape_configs:
  - job_name: 'coupon-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['coupon-service:8080']
    relabel_configs:
      - source_labels: [__address__]
        target_label: instance
        replacement: 'coupon-service'

  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']

  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

  - job_name: 'kafka'
    static_configs:
      - targets: ['kafka-exporter:9308']
```

## 로그 수집

### Logback 설정

```xml
<!-- logback-spring.xml -->
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="!local">
        <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
            <http>
                <url>http://loki:3100/loki/api/v1/push</url>
            </http>
            <format>
                <label>
                    <pattern>app=coupon-service,env=${SPRING_PROFILES_ACTIVE},level=%level</pattern>
                </label>
                <message>
                    <pattern>${FILE_LOG_PATTERN}</pattern>
                </message>
            </format>
        </appender>
    </springProfile>

    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app":"coupon-service","env":"${SPRING_PROFILES_ACTIVE}"}</customFields>
            <includeMdc>true</includeMdc>
            <includeContext>true</includeContext>
            <includeCallerData>true</includeCallerData>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
        <springProfile name="!local">
            <appender-ref ref="LOKI"/>
        </springProfile>
    </root>
</configuration>
```

### 구조화된 로깅

```java
@Slf4j
@Component
public class StructuredLogger {

    public void logCouponIssued(Long userId, Long couponId, String couponCode) {
        MDC.put("userId", String.valueOf(userId));
        MDC.put("couponId", String.valueOf(couponId));
        MDC.put("couponCode", couponCode);
        MDC.put("action", "COUPON_ISSUED");

        log.info("Coupon issued successfully");

        MDC.clear();
    }

    public void logError(String operation, Exception e) {
        MDC.put("operation", operation);
        MDC.put("errorType", e.getClass().getSimpleName());
        MDC.put("errorMessage", e.getMessage());

        log.error("Operation failed", e);

        MDC.clear();
    }
}
```

## 분산 트레이싱

### Spring Cloud Sleuth + Zipkin

```yaml
# application.yml
spring:
  sleuth:
    enabled: true
    sampler:
      probability: 1.0
    propagation:
      type: B3
    log:
      slf4j:
        enabled: true
  zipkin:
    base-url: http://zipkin:9411
    enabled: true
    sender:
      type: web

logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId:-},%X{spanId:-}]"
```

### OpenTelemetry 설정

```java
@Configuration
public class TracingConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "coupon-service",
                ResourceAttributes.SERVICE_VERSION, "1.0.0"
            )));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(
                JaegerGrpcSpanExporter.builder()
                    .setEndpoint("http://jaeger:14250")
                    .build()
            ).build())
            .setResource(resource)
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(
                W3CTraceContextPropagator.getInstance()
            ))
            .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("coupon-service", "1.0.0");
    }
}
```

## Grafana 대시보드

### 주요 대시보드

#### 1. 비즈니스 메트릭 대시보드

```json
{
  "dashboard": {
    "title": "Coupon Service - Business Metrics",
    "panels": [
      {
        "title": "쿠폰 발급률",
        "targets": [
          {
            "expr": "rate(coupon_issued_total[5m])"
          }
        ]
      },
      {
        "title": "쿠폰 사용률",
        "targets": [
          {
            "expr": "coupon_used_total / coupon_issued_total * 100"
          }
        ]
      },
      {
        "title": "평균 할인 금액",
        "targets": [
          {
            "expr": "coupon_discount_amount_sum / coupon_discount_amount_count"
          }
        ]
      }
    ]
  }
}
```

#### 2. 시스템 메트릭 대시보드

```json
{
  "dashboard": {
    "title": "Coupon Service - System Metrics",
    "panels": [
      {
        "title": "CPU 사용률",
        "targets": [
          {
            "expr": "process_cpu_usage"
          }
        ]
      },
      {
        "title": "메모리 사용량",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes"
          }
        ]
      },
      {
        "title": "API 응답 시간 (p95)",
        "targets": [
          {
            "expr": "http_server_requests_seconds{quantile=\"0.95\"}"
          }
        ]
      }
    ]
  }
}
```

## 알람 설정

### Prometheus Alert Rules

```yaml
# alerts.yml
groups:
  - name: coupon-service
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
          service: coupon-service
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }}"

      - alert: HighResponseTime
        expr: histogram_quantile(0.95, http_server_requests_seconds_bucket) > 1
        for: 5m
        labels:
          severity: warning
          service: coupon-service
        annotations:
          summary: "High response time"
          description: "95th percentile response time is {{ $value }}s"

      - alert: CouponIssueLimitApproaching
        expr: (coupon_current_issue_count / coupon_max_issue_count) > 0.9
        for: 1m
        labels:
          severity: warning
          service: coupon-service
        annotations:
          summary: "Coupon issue limit approaching"
          description: "{{ $value | humanizePercentage }} of limit reached"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 5m
        labels:
          severity: critical
          service: coupon-service
        annotations:
          summary: "Database connection pool exhausted"
          description: "{{ $value | humanizePercentage }} of connections in use"
```

### AlertManager 설정

```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: pagerduty
    - match:
        severity: warning
      receiver: slack

receivers:
  - name: 'default'
    slack_configs:
      - api_url: ${SLACK_WEBHOOK_URL}
        channel: '#alerts'
        title: 'Coupon Service Alert'

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: ${PAGERDUTY_SERVICE_KEY}

  - name: 'slack'
    slack_configs:
      - api_url: ${SLACK_WEBHOOK_URL}
        channel: '#coupon-alerts'
```

## 헬스 체크

### 커스텀 헬스 인디케이터

```java
@Component
public class CouponServiceHealthIndicator implements HealthIndicator {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Health health() {
        try {
            // Database health
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            // Redis health
            redisTemplate.getConnectionFactory().getConnection().ping();

            // Kafka health
            kafkaTemplate.send("health-check", "ping").get(5, TimeUnit.SECONDS);

            // Business health checks
            Map<String, Object> details = new HashMap<>();
            details.put("database", "UP");
            details.put("redis", "UP");
            details.put("kafka", "UP");
            details.put("activeConnections", getActiveConnections());
            details.put("queueDepth", getQueueDepth());

            return Health.up().withDetails(details).build();

        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

## 성능 프로파일링

### Async Profiler 통합

```bash
# JVM 옵션 추가
JAVA_OPTS="$JAVA_OPTS -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints"

# 프로파일링 시작
curl -X POST http://localhost:8080/actuator/profiler/start

# 프로파일링 중지 및 결과 다운로드
curl -X POST http://localhost:8080/actuator/profiler/stop -o profile.html
```

## SLI/SLO 정의

### Service Level Indicators (SLI)

| 메트릭 | 정의 | 측정 방법 |
|--------|------|-----------|
| 가용성 | 서비스 가동 시간 | `up / (up + down) * 100` |
| 응답 시간 | API 응답 속도 | `http_server_requests_seconds` |
| 에러율 | 실패한 요청 비율 | `errors / total * 100` |
| 처리량 | 초당 처리 요청 수 | `rate(requests_total[1m])` |

### Service Level Objectives (SLO)

| SLI | SLO | 측정 기간 |
|-----|-----|----------|
| 가용성 | 99.9% | 월간 |
| 응답 시간 (p99) | < 500ms | 일간 |
| 에러율 | < 0.1% | 주간 |
| 처리량 | > 1000 RPS | 실시간 |

## 대시보드 URL

- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090
- Jaeger: http://localhost:16686
- Kibana: http://localhost:5601
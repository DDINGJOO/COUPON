# 헥사고날 아키텍처

## 개요

쿠폰 서비스는 헥사고날 아키텍처(Hexagonal Architecture) 또는 포트와 어댑터 아키텍처(Ports and Adapters Architecture)를 채택하여 구현되었습니다. 이 아키텍처는 비즈니스 로직을 외부 시스템으로부터 격리하여 테스트 가능성과 유지보수성을 향상시킵니다.

## 아키텍처 원칙

### 1. 의존성 방향
- 모든 의존성은 바깥쪽에서 안쪽으로 향함
- 도메인 계층은 외부 계층에 의존하지 않음
- 인터페이스를 통한 의존성 역전

### 2. 계층 분리
- **Domain**: 순수 비즈니스 로직
- **Application**: 유스케이스 구현
- **Adapter**: 외부 시스템과의 통신

## 패키지 구조

```
com.teambind.coupon/
├── domain/                    # 도메인 계층
│   ├── model/                 # 엔티티, VO
│   │   ├── CouponPolicy.java
│   │   ├── CouponIssue.java
│   │   └── CouponReservation.java
│   ├── exception/             # 도메인 예외
│   │   └── CouponDomainException.java
│   └── service/               # 도메인 서비스
│       └── CouponValidator.java
│
├── application/               # 애플리케이션 계층
│   ├── port/                  # 포트 정의
│   │   ├── in/               # 인바운드 포트
│   │   │   ├── CreateCouponPolicyUseCase.java
│   │   │   ├── IssueCouponUseCase.java
│   │   │   └── UseCouponUseCase.java
│   │   └── out/              # 아웃바운드 포트
│   │       ├── LoadCouponPolicyPort.java
│   │       ├── SaveCouponPolicyPort.java
│   │       └── PublishEventPort.java
│   └── service/              # 유스케이스 구현
│       ├── CreateCouponPolicyService.java
│       ├── IssueCouponService.java
│       └── UseCouponService.java
│
└── adapter/                   # 어댑터 계층
    ├── in/                   # 인바운드 어댑터
    │   ├── web/              # REST API
    │   │   ├── CouponPolicyController.java
    │   │   └── CouponIssueController.java
    │   └── message/          # 메시지 리스너
    │       └── PaymentEventConsumer.java
    └── out/                  # 아웃바운드 어댑터
        ├── persistence/      # 데이터 저장
        │   ├── CouponPolicyPersistenceAdapter.java
        │   └── repository/
        │       └── CouponPolicyRepository.java
        ├── cache/           # 캐시
        │   └── RedisAdapter.java
        └── message/         # 메시지 발행
            └── KafkaEventPublisher.java
```

## 포트와 어댑터

### 인바운드 포트 (Input Ports)

**정의**
```java
public interface IssueCouponUseCase {
    CouponIssueResponse issueCoupon(CouponIssueCommand command);
}
```

**구현**
```java
@Service
@RequiredArgsConstructor
public class IssueCouponService implements IssueCouponUseCase {
    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final PublishEventPort publishEventPort;

    @Override
    public CouponIssueResponse issueCoupon(CouponIssueCommand command) {
        // 비즈니스 로직 구현
    }
}
```

### 아웃바운드 포트 (Output Ports)

**정의**
```java
public interface LoadCouponPolicyPort {
    Optional<CouponPolicy> loadById(Long policyId);
    Optional<CouponPolicy> loadByCodeAndActive(String couponCode);
}
```

**구현**
```java
@Component
@RequiredArgsConstructor
public class CouponPolicyPersistenceAdapter
    implements LoadCouponPolicyPort, SaveCouponPolicyPort {

    private final CouponPolicyRepository repository;
    private final CouponPolicyMapper mapper;

    @Override
    public Optional<CouponPolicy> loadById(Long policyId) {
        return repository.findById(policyId)
            .map(mapper::toDomain);
    }
}
```

## 데이터 매핑

### JPA 엔티티와 도메인 모델 분리

**JPA 엔티티**
```java
@Entity
@Table(name = "coupon_policies")
public class CouponPolicyJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String couponName;
    private String couponCode;
    // JPA 관련 어노테이션
}
```

**도메인 모델**
```java
@Getter
public class CouponPolicy {
    private final Long id;
    private final String couponName;
    private final String couponCode;
    // 순수 비즈니스 로직

    public boolean isIssuable() {
        return isActive() && hasRemainingQuantity();
    }
}
```

**매퍼**
```java
@Component
public class CouponPolicyMapper {
    public CouponPolicy toDomain(CouponPolicyJpaEntity entity) {
        return CouponPolicy.builder()
            .id(entity.getId())
            .couponName(entity.getCouponName())
            .couponCode(entity.getCouponCode())
            .build();
    }

    public CouponPolicyJpaEntity toJpaEntity(CouponPolicy domain) {
        // 도메인 → JPA 엔티티 변환
    }
}
```

## 의존성 주입

### Spring Configuration

```java
@Configuration
@ComponentScan(basePackages = "com.teambind.coupon")
public class CouponConfiguration {

    @Bean
    public IssueCouponUseCase issueCouponUseCase(
            LoadCouponPolicyPort loadCouponPolicyPort,
            SaveCouponIssuePort saveCouponIssuePort,
            PublishEventPort publishEventPort) {
        return new IssueCouponService(
            loadCouponPolicyPort,
            saveCouponIssuePort,
            publishEventPort
        );
    }
}
```

## 테스트 전략

### 단위 테스트
- 도메인 모델: 외부 의존성 없이 테스트
- 서비스: Mock 포트를 사용한 테스트

```java
@Test
void 쿠폰_발급_성공() {
    // given
    CouponPolicy policy = CouponPolicy.builder()
        .maxIssueCount(100)
        .currentIssueCount(50)
        .build();

    when(loadCouponPolicyPort.loadById(1L))
        .thenReturn(Optional.of(policy));

    // when
    CouponIssueResponse response = service.issueCoupon(command);

    // then
    assertThat(response.isSuccess()).isTrue();
}
```

### 통합 테스트
- 실제 데이터베이스 연동
- 전체 플로우 검증

```java
@SpringBootTest
@AutoConfigureMockMvc
class CouponIssueIntegrationTest {
    @Test
    void 쿠폰_발급_전체_플로우() {
        // API 호출부터 데이터베이스 저장까지 검증
    }
}
```

## 장점

1. **테스트 용이성**: 비즈니스 로직을 독립적으로 테스트 가능
2. **유연성**: 어댑터 교체로 기술 스택 변경 용이
3. **명확한 경계**: 각 계층의 책임이 명확히 분리
4. **유지보수성**: 변경 영향 범위 최소화

## 고려사항

1. **복잡도**: 초기 구현 복잡도 증가
2. **매핑 오버헤드**: 계층 간 데이터 변환 비용
3. **학습 곡선**: 팀원들의 아키텍처 이해 필요
4. **과도한 추상화**: 실용성과 균형 필요
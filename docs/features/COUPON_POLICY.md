# 쿠폰 정책 관리

## 개요

쿠폰 정책은 쿠폰 발급의 기준이 되는 템플릿입니다. 하나의 정책으로 여러 개의 쿠폰을 발급할 수 있으며, 각 정책은 할인 조건, 발급 조건, 사용 조건을 정의합니다.

## 도메인 모델

### CouponPolicy

```java
public class CouponPolicy {
    private Long id;
    private String couponName;           // 쿠폰 이름
    private String couponCode;           // 쿠폰 코드
    private DiscountType discountType;  // 할인 타입 (정액/정률)
    private BigDecimal discountValue;   // 할인 값
    private BigDecimal minimumOrderAmount; // 최소 주문 금액
    private BigDecimal maxDiscountAmount;  // 최대 할인 금액
    private IssueType issueType;        // 발급 타입 (CODE/DIRECT)
    private Integer maxIssueCount;       // 최대 발급 수량
    private Integer currentIssueCount;   // 현재 발급 수량
    private Integer maxIssuePerUser;     // 사용자당 최대 발급 수
    private Integer validDays;           // 유효 기간 (일)
    private LocalDateTime issueStartDate; // 발급 시작일
    private LocalDateTime issueEndDate;   // 발급 종료일
    private Boolean isActive;            // 활성화 여부
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 할인 타입 (DiscountType)

| 타입 | 설명 | 예시 |
|------|------|------|
| FIXED_AMOUNT | 정액 할인 | 10,000원 할인 |
| PERCENTAGE | 정률 할인 | 20% 할인 |

### 발급 타입 (IssueType)

| 타입 | 설명 | 사용 시나리오 |
|------|------|---------------|
| CODE | 쿠폰 코드 입력 방식 | 프로모션 코드 배포 |
| DIRECT | 직접 발급 방식 | 타겟 마케팅, 보상 |

## 주요 기능

### 1. 쿠폰 정책 생성

#### 비즈니스 규칙
- 쿠폰 코드는 고유해야 함
- 정률 할인은 0-100% 범위
- 발급 시작일은 종료일보다 이전
- 최대 발급 수량은 1 이상

#### 처리 흐름
```
1. 입력 값 검증
2. 쿠폰 코드 중복 확인
3. 정책 생성 및 저장
4. 생성 이벤트 발행
```

#### 구현 코드
```java
@Service
@RequiredArgsConstructor
public class CreateCouponPolicyService implements CreateCouponPolicyUseCase {

    private final SaveCouponPolicyPort saveCouponPolicyPort;
    private final LoadCouponPolicyPort loadCouponPolicyPort;

    @Override
    @Transactional
    public CouponPolicy create(CreateCouponPolicyCommand command) {
        // 1. 검증
        validateCommand(command);

        // 2. 중복 확인
        if (loadCouponPolicyPort.existsByCode(command.getCouponCode())) {
            throw new DuplicateCouponCodeException(command.getCouponCode());
        }

        // 3. 도메인 객체 생성
        CouponPolicy policy = CouponPolicy.builder()
            .couponName(command.getCouponName())
            .couponCode(command.getCouponCode())
            .discountType(command.getDiscountType())
            .discountValue(command.getDiscountValue())
            .minimumOrderAmount(command.getMinimumOrderAmount())
            .maxDiscountAmount(command.getMaxDiscountAmount())
            .issueType(command.getIssueType())
            .maxIssueCount(command.getMaxIssueCount())
            .currentIssueCount(0)
            .maxIssuePerUser(command.getMaxIssuePerUser())
            .validDays(command.getValidDays())
            .issueStartDate(command.getIssueStartDate())
            .issueEndDate(command.getIssueEndDate())
            .isActive(true)
            .build();

        // 4. 저장
        return saveCouponPolicyPort.save(policy);
    }

    private void validateCommand(CreateCouponPolicyCommand command) {
        if (command.getDiscountType() == DiscountType.PERCENTAGE) {
            if (command.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new InvalidDiscountValueException("정률 할인은 100%를 초과할 수 없습니다");
            }
        }

        if (command.getIssueStartDate().isAfter(command.getIssueEndDate())) {
            throw new InvalidDateRangeException("발급 시작일이 종료일보다 늦습니다");
        }
    }
}
```

### 2. 쿠폰 정책 수정

#### 수정 가능 항목
- 최대 발급 수량 (증가만 가능)
- 발급 종료일 (연장만 가능)
- 활성화 상태

#### 수정 제한 사항
- 이미 발급된 쿠폰이 있는 경우 할인 조건 변경 불가
- 사용된 쿠폰이 있는 경우 삭제 불가

### 3. 쿠폰 정책 조회

#### 조회 유형

**단일 조회**
- ID로 조회
- 쿠폰 코드로 조회

**목록 조회**
- 페이징 지원
- 필터링: 활성 상태, 발급 타입, 날짜 범위
- 정렬: 생성일, 발급 수량, 사용률

#### 캐싱 전략
```java
@Cacheable(value = "couponPolicy", key = "#policyId")
public CouponPolicy findById(Long policyId) {
    return loadCouponPolicyPort.loadById(policyId)
        .orElseThrow(() -> new CouponPolicyNotFoundException(policyId));
}
```

### 4. 쿠폰 정책 비활성화

#### 비활성화 조건
- 발급 종료일 도달
- 최대 발급 수량 도달
- 관리자 수동 비활성화

#### 비활성화 영향
- 신규 발급 불가
- 기존 발급 쿠폰은 유효기간까지 사용 가능
- 통계에서 제외 가능

## 발급 규칙 엔진

### 발급 가능 여부 판단

```java
public class CouponPolicy {

    public boolean isIssuable() {
        return isActive
            && isWithinIssuePeriod()
            && hasRemainingQuantity();
    }

    private boolean isWithinIssuePeriod() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(issueStartDate)
            && !now.isAfter(issueEndDate);
    }

    private boolean hasRemainingQuantity() {
        return maxIssueCount == null
            || currentIssueCount < maxIssueCount;
    }

    public void incrementIssueCount() {
        if (!hasRemainingQuantity()) {
            throw new CouponSoldOutException();
        }
        this.currentIssueCount++;
    }
}
```

### 사용자별 발급 제한

```java
@Component
@RequiredArgsConstructor
public class UserIssueLimitValidator {

    private final LoadCouponIssuePort loadCouponIssuePort;

    public void validate(Long policyId, Long userId, Integer maxIssuePerUser) {
        if (maxIssuePerUser == null) {
            return; // 무제한
        }

        int issuedCount = loadCouponIssuePort.countByPolicyIdAndUserId(
            policyId, userId
        );

        if (issuedCount >= maxIssuePerUser) {
            throw new UserIssueLimitExceededException(
                userId, policyId, maxIssuePerUser
            );
        }
    }
}
```

## 할인 금액 계산

### 정액 할인

```java
public BigDecimal calculateDiscount(BigDecimal orderAmount) {
    if (discountType != DiscountType.FIXED_AMOUNT) {
        throw new IllegalStateException();
    }

    if (orderAmount.compareTo(minimumOrderAmount) < 0) {
        throw new MinimumOrderNotMetException();
    }

    return discountValue;
}
```

### 정률 할인

```java
public BigDecimal calculateDiscount(BigDecimal orderAmount) {
    if (discountType != DiscountType.PERCENTAGE) {
        throw new IllegalStateException();
    }

    if (orderAmount.compareTo(minimumOrderAmount) < 0) {
        throw new MinimumOrderNotMetException();
    }

    BigDecimal discount = orderAmount
        .multiply(discountValue)
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);

    // 최대 할인 금액 제한
    if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
        return maxDiscountAmount;
    }

    return discount;
}
```

## 성능 최적화

### 인덱스 전략

```sql
-- 쿠폰 코드 조회 최적화
CREATE UNIQUE INDEX idx_coupon_code ON coupon_policies(coupon_code);

-- 활성 정책 조회 최적화
CREATE INDEX idx_active_policies ON coupon_policies(is_active, issue_end_date);

-- 발급 가능 정책 조회 최적화
CREATE INDEX idx_issuable_policies ON coupon_policies(
    is_active,
    issue_start_date,
    issue_end_date
) WHERE is_active = true;
```

### 벌크 연산

```java
@Modifying
@Query("UPDATE CouponPolicyJpaEntity p SET p.isActive = false " +
       "WHERE p.issueEndDate < :now AND p.isActive = true")
int deactivateExpiredPolicies(@Param("now") LocalDateTime now);
```

## 모니터링

### 주요 메트릭
- 정책별 발급률
- 정책별 사용률
- 평균 할인 금액
- 발급 속도

### 알람 조건
- 발급률 90% 초과
- 비정상적인 발급 패턴
- 정책 생성 실패율 증가
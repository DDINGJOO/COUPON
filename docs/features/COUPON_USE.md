# 쿠폰 사용

## 개요

쿠폰 사용은 발급된 쿠폰을 실제 주문에 적용하여 할인을 받는 과정입니다. 예약-확정 2단계 프로세스를 통해 동시성 문제를 해결하고, 결제 시스템과의 연동을 통해 트랜잭션 일관성을 보장합니다.

## 사용 프로세스

### 2단계 사용 프로세스

```
1. 예약 단계 (Reserve)
   - 쿠폰 상태를 RESERVED로 변경
   - 예약 ID 발급
   - 타임아웃 설정 (기본 30분)

2. 확정 단계 (Confirm)
   - 결제 완료 이벤트 수신
   - 쿠폰 상태를 USED로 변경
   - 사용 일시 기록
```

## 도메인 모델

### CouponReservation

```java
public class CouponReservation {
    private String reservationId;        // 예약 ID
    private Long couponId;              // 쿠폰 ID
    private Long userId;                // 사용자 ID
    private String orderId;             // 주문 ID
    private BigDecimal orderAmount;     // 주문 금액
    private BigDecimal discountAmount;  // 할인 금액
    private LocalDateTime reservedAt;   // 예약 일시
    private LocalDateTime expiresAt;    // 예약 만료 일시
    private ReservationStatus status;   // 예약 상태
}
```

### 예약 상태 (ReservationStatus)

| 상태 | 설명 | 다음 가능 상태 |
|------|------|---------------|
| PENDING | 예약 대기 중 | CONFIRMED, CANCELLED, EXPIRED |
| CONFIRMED | 사용 확정 | - |
| CANCELLED | 취소됨 | - |
| EXPIRED | 만료됨 | - |

## 쿠폰 예약

### 예약 처리

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ReserveCouponService {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final SaveReservationPort saveReservationPort;
    private final LoadCouponPolicyPort loadCouponPolicyPort;
    private final RedisDistributedLock distributedLock;

    public ReservationResponse reserve(ReservationCommand command) {
        // 1. 분산 락 획득
        String lockKey = "coupon:reserve:" + command.getCouponId();
        String lockValue = UUID.randomUUID().toString();

        if (!distributedLock.tryLock(lockKey, lockValue, Duration.ofSeconds(5))) {
            throw new CouponConcurrencyException("쿠폰 예약 처리 중입니다");
        }

        try {
            // 2. 쿠폰 조회 및 검증
            CouponIssue coupon = loadCouponIssuePort
                .findByIdWithLock(command.getCouponId())
                .orElseThrow(() -> new CouponNotFoundException(command.getCouponId()));

            validateCoupon(coupon, command.getUserId());

            // 3. 정책 조회 및 할인 계산
            CouponPolicy policy = loadCouponPolicyPort
                .loadById(coupon.getPolicyId())
                .orElseThrow(() -> new CouponPolicyNotFoundException(coupon.getPolicyId()));

            BigDecimal discountAmount = calculateDiscount(
                policy,
                command.getOrderAmount()
            );

            // 4. 쿠폰 상태 변경
            coupon.reserve(command.getOrderId());
            saveCouponIssuePort.save(coupon);

            // 5. 예약 정보 생성
            CouponReservation reservation = CouponReservation.builder()
                .reservationId(generateReservationId())
                .couponId(coupon.getId())
                .userId(command.getUserId())
                .orderId(command.getOrderId())
                .orderAmount(command.getOrderAmount())
                .discountAmount(discountAmount)
                .reservedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .status(ReservationStatus.PENDING)
                .build();

            CouponReservation saved = saveReservationPort.save(reservation);

            // 6. 이벤트 발행
            publishReservationEvent(saved);

            return ReservationResponse.from(saved);

        } finally {
            distributedLock.unlock(lockKey, lockValue);
        }
    }

    private void validateCoupon(CouponIssue coupon, Long userId) {
        // 소유자 확인
        if (!coupon.getUserId().equals(userId)) {
            throw new UnauthorizedCouponAccessException(
                "쿠폰 소유자가 아닙니다"
            );
        }

        // 상태 확인
        if (coupon.getStatus() != CouponStatus.ISSUED) {
            throw new CouponNotAvailableException(
                "사용 가능한 상태가 아닙니다: " + coupon.getStatus()
            );
        }

        // 만료 확인
        if (coupon.isExpired()) {
            throw new CouponExpiredException(
                "만료된 쿠폰입니다"
            );
        }
    }

    private BigDecimal calculateDiscount(
            CouponPolicy policy,
            BigDecimal orderAmount) {

        // 최소 주문 금액 확인
        if (orderAmount.compareTo(policy.getMinimumOrderAmount()) < 0) {
            throw new MinimumOrderNotMetException(
                String.format("최소 주문 금액 %s원 이상이어야 합니다",
                    policy.getMinimumOrderAmount())
            );
        }

        // 할인 금액 계산
        return policy.calculateDiscount(orderAmount);
    }
}
```

## 쿠폰 사용 확정

### 결제 완료 이벤트 처리

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ProcessPaymentUseCase processPaymentUseCase;

    @KafkaListener(topics = "payment.completed")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("결제 완료 이벤트 수신: {}", event);

        try {
            // 쿠폰 사용 확정
            processPaymentUseCase.confirmCouponUsage(
                event.getReservationId(),
                event.getPaymentId()
            );

        } catch (Exception e) {
            log.error("쿠폰 사용 확정 실패: {}", e.getMessage());
            // DLQ로 전송 또는 재시도
            throw e;
        }
    }

    @KafkaListener(topics = "payment.failed")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("결제 실패 이벤트 수신: {}", event);

        try {
            // 쿠폰 예약 취소
            processPaymentUseCase.cancelCouponReservation(
                event.getReservationId(),
                event.getFailureReason()
            );

        } catch (Exception e) {
            log.error("쿠폰 예약 취소 실패: {}", e.getMessage());
            throw e;
        }
    }
}
```

### 사용 확정 처리

```java
@Service
@RequiredArgsConstructor
@Transactional
public class ConfirmCouponUsageService {

    private final LoadReservationPort loadReservationPort;
    private final SaveReservationPort saveReservationPort;
    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;
    private final EventPublisher eventPublisher;

    public void confirmUsage(String reservationId, String paymentId) {
        // 1. 예약 정보 조회
        CouponReservation reservation = loadReservationPort
            .findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 2. 예약 상태 확인
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            log.warn("이미 처리된 예약: {}", reservationId);
            return; // 멱등성 보장
        }

        // 3. 쿠폰 조회 및 사용 처리
        CouponIssue coupon = loadCouponIssuePort
            .findById(reservation.getCouponId())
            .orElseThrow(() -> new CouponNotFoundException(reservation.getCouponId()));

        coupon.use(paymentId);
        saveCouponIssuePort.save(coupon);

        // 4. 예약 확정
        reservation.confirm();
        saveReservationPort.save(reservation);

        // 5. 이벤트 발행
        eventPublisher.publish(new CouponUsedEvent(
            coupon.getId(),
            reservation.getUserId(),
            reservation.getOrderId(),
            reservation.getDiscountAmount()
        ));

        log.info("쿠폰 사용 확정: couponId={}, orderId={}",
            coupon.getId(), reservation.getOrderId());
    }
}
```

## 쿠폰 예약 취소

### 취소 처리

```java
@Service
@RequiredArgsConstructor
@Transactional
public class CancelCouponReservationService {

    public void cancelReservation(String reservationId, String reason) {
        // 1. 예약 정보 조회
        CouponReservation reservation = loadReservationPort
            .findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 2. 취소 가능 여부 확인
        if (!reservation.isCancellable()) {
            throw new ReservationNotCancellableException(
                "취소할 수 없는 예약입니다"
            );
        }

        // 3. 쿠폰 상태 복구
        CouponIssue coupon = loadCouponIssuePort
            .findById(reservation.getCouponId())
            .orElseThrow(() -> new CouponNotFoundException(reservation.getCouponId()));

        coupon.rollback();
        saveCouponIssuePort.save(coupon);

        // 4. 예약 취소
        reservation.cancel(reason);
        saveReservationPort.save(reservation);

        // 5. 이벤트 발행
        eventPublisher.publish(new CouponCancelledEvent(
            coupon.getId(),
            reservationId,
            reason
        ));
    }
}
```

## 예약 만료 처리

### 스케줄러

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpirationScheduler {

    private final LoadReservationPort loadReservationPort;
    private final CancelCouponReservationService cancelService;

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void expireReservations() {
        LocalDateTime now = LocalDateTime.now();

        List<CouponReservation> expiredReservations = loadReservationPort
            .findExpiredReservations(now);

        for (CouponReservation reservation : expiredReservations) {
            try {
                cancelService.cancelReservation(
                    reservation.getReservationId(),
                    "RESERVATION_EXPIRED"
                );
            } catch (Exception e) {
                log.error("예약 만료 처리 실패: {}", e.getMessage());
            }
        }

        if (!expiredReservations.isEmpty()) {
            log.info("만료된 예약 처리: {}건", expiredReservations.size());
        }
    }
}
```

## 할인 금액 계산

### 계산 로직

```java
public class DiscountCalculator {

    public BigDecimal calculate(
            CouponPolicy policy,
            BigDecimal orderAmount) {

        switch (policy.getDiscountType()) {
            case FIXED_AMOUNT:
                return calculateFixedDiscount(
                    policy.getDiscountValue(),
                    orderAmount
                );

            case PERCENTAGE:
                return calculatePercentageDiscount(
                    policy.getDiscountValue(),
                    orderAmount,
                    policy.getMaxDiscountAmount()
                );

            default:
                throw new UnsupportedDiscountTypeException(
                    policy.getDiscountType()
                );
        }
    }

    private BigDecimal calculateFixedDiscount(
            BigDecimal discountValue,
            BigDecimal orderAmount) {

        // 할인 금액이 주문 금액을 초과하지 않도록
        return discountValue.min(orderAmount);
    }

    private BigDecimal calculatePercentageDiscount(
            BigDecimal percentage,
            BigDecimal orderAmount,
            BigDecimal maxDiscount) {

        BigDecimal discount = orderAmount
            .multiply(percentage)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);

        // 최대 할인 금액 제한
        if (maxDiscount != null && discount.compareTo(maxDiscount) > 0) {
            return maxDiscount;
        }

        // 할인 금액이 주문 금액을 초과하지 않도록
        return discount.min(orderAmount);
    }
}
```

## 보상 트랜잭션

### 실패 시 롤백

```java
@Component
@RequiredArgsConstructor
public class CouponCompensationService {

    private final LoadCouponIssuePort loadCouponIssuePort;
    private final SaveCouponIssuePort saveCouponIssuePort;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void compensate(Long couponId, String reason) {
        try {
            CouponIssue coupon = loadCouponIssuePort
                .findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));

            // 상태에 따른 보상 처리
            switch (coupon.getStatus()) {
                case RESERVED:
                    coupon.rollback();
                    break;
                case USED:
                    coupon.refund();
                    break;
                default:
                    log.warn("보상 불필요: {}", coupon.getStatus());
                    return;
            }

            saveCouponIssuePort.save(coupon);
            log.info("쿠폰 보상 완료: couponId={}, reason={}",
                couponId, reason);

        } catch (Exception e) {
            log.error("쿠폰 보상 실패: {}", e.getMessage());
            // DLQ로 전송 또는 수동 처리 필요
            throw e;
        }
    }
}
```

## 성능 최적화

### 예약 정보 캐싱

```java
@Service
public class ReservationCacheService {

    private final RedisTemplate<String, CouponReservation> redisTemplate;

    public void cache(CouponReservation reservation) {
        String key = "reservation:" + reservation.getReservationId();
        redisTemplate.opsForValue().set(
            key,
            reservation,
            Duration.ofMinutes(30)
        );
    }

    public Optional<CouponReservation> get(String reservationId) {
        String key = "reservation:" + reservationId;
        return Optional.ofNullable(
            redisTemplate.opsForValue().get(key)
        );
    }
}
```

## 모니터링

### 주요 메트릭
- 예약 수 / 사용 확정 수
- 예약 취소율
- 평균 예약-확정 시간
- 만료율

### 알람 조건
- 예약 취소율 > 20%
- 예약-확정 시간 > 5분
- 만료율 > 10%
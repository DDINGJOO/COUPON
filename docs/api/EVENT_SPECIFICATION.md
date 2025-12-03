# 이벤트 명세

## 개요

쿠폰 서비스는 Apache Kafka를 통해 이벤트 기반 통신을 수행합니다. 이벤트는 JSON 형식으로 직렬화되며, 각 이벤트는 고유한 ID와 타임스탬프를 포함합니다.

## Kafka 설정

### 브로커
- Production: `kafka-prod-1:9092,kafka-prod-2:9092,kafka-prod-3:9092`
- Development: `localhost:29092`

### 토픽 설정
| 토픽명 | 파티션 | 복제 계수 | 보존 기간 |
|-------|--------|----------|----------|
| coupon.issued.v1 | 3 | 2 | 7일 |
| coupon.used.v1 | 3 | 2 | 7일 |
| coupon.reserved.v1 | 3 | 2 | 1일 |
| payment.completed.v1 | 3 | 2 | 7일 |
| payment.failed.v1 | 3 | 2 | 7일 |

### 컨슈머 설정
- **동시 처리 스레드**: 10 (기존 3에서 증가)
- **ACK 모드**: MANUAL (수동 확인)
- **재시도 정책**: 비즈니스 로직 실패 시 재처리
- **멱등성**: reservationId/orderId 기반 중복 처리 방지

## 발행 이벤트 (Published Events)

### 1. CouponIssuedEvent

**토픽**: `coupon.issued`

**설명**: 쿠폰이 발급되었을 때 발생

**스키마**
```json
{
    "eventId": "evt_2024010110000001",
    "eventType": "COUPON_ISSUED",
    "timestamp": "2024-01-01T10:00:00Z",
    "data": {
        "couponId": 1001,
        "policyId": 1,
        "userId": 12345,
        "couponCode": "WELCOME2024",
        "couponName": "신규 가입 쿠폰",
        "discountType": "FIXED_AMOUNT",
        "discountValue": 10000,
        "issueType": "CODE",
        "status": "ISSUED",
        "expiryDate": "2024-01-31T23:59:59",
        "issuedAt": "2024-01-01T10:00:00"
    },
    "metadata": {
        "correlationId": "corr_2024010110000001",
        "source": "coupon-service",
        "version": "1.0"
    }
}
```

**발생 시점**
- 코드 입력으로 쿠폰 발급 성공
- 직접 발급 성공
- 이벤트 기반 자동 발급

### 2. CouponUsedEvent

**토픽**: `coupon.used`

**설명**: 쿠폰이 사용되었을 때 발생

**스키마**
```json
{
    "eventId": "evt_2024010110000002",
    "eventType": "COUPON_USED",
    "timestamp": "2024-01-01T10:05:00Z",
    "data": {
        "couponId": 1001,
        "userId": 12345,
        "orderId": "ORDER-2024-0001",
        "paymentId": "PAY-2024-0001",
        "originalAmount": 100000,
        "discountAmount": 10000,
        "finalAmount": 90000,
        "usedAt": "2024-01-01T10:05:00"
    },
    "metadata": {
        "correlationId": "corr_2024010110000002",
        "source": "coupon-service",
        "version": "1.0"
    }
}
```

**발생 시점**
- 결제 완료로 쿠폰 사용 확정
- 관리자 수동 사용 처리

### 3. CouponReservedEvent

**토픽**: `coupon.reserved`

**설명**: 쿠폰이 예약되었을 때 발생

**스키마**
```json
{
    "eventId": "evt_2024010110000003",
    "eventType": "COUPON_RESERVED",
    "timestamp": "2024-01-01T10:00:00Z",
    "data": {
        "reservationId": "RESV-2024-0001",
        "couponId": 1001,
        "userId": 12345,
        "orderId": "ORDER-2024-0001",
        "orderAmount": 100000,
        "discountAmount": 10000,
        "reservedAt": "2024-01-01T10:00:00",
        "expiresAt": "2024-01-01T10:30:00"
    },
    "metadata": {
        "correlationId": "corr_2024010110000003",
        "source": "coupon-service",
        "version": "1.0"
    }
}
```

**발생 시점**
- 주문 시 쿠폰 예약
- 장바구니에 쿠폰 적용

### 4. CouponExpiredEvent

**토픽**: `coupon.expired`

**설명**: 쿠폰이 만료되었을 때 발생

**스키마**
```json
{
    "eventId": "evt_2024020100000001",
    "eventType": "COUPON_EXPIRED",
    "timestamp": "2024-02-01T00:00:00Z",
    "data": {
        "couponId": 1001,
        "userId": 12345,
        "policyId": 1,
        "expiredAt": "2024-01-31T23:59:59",
        "reason": "VALIDITY_PERIOD_EXPIRED"
    },
    "metadata": {
        "correlationId": "corr_2024020100000001",
        "source": "coupon-service",
        "version": "1.0"
    }
}
```

**발생 시점**
- 유효기간 만료
- 정책 비활성화로 인한 만료

### 5. CouponCancelledEvent

**토픽**: `coupon.cancelled`

**설명**: 쿠폰 예약이 취소되었을 때 발생

**스키마**
```json
{
    "eventId": "evt_2024010110100001",
    "eventType": "COUPON_CANCELLED",
    "timestamp": "2024-01-01T10:10:00Z",
    "data": {
        "couponId": 1001,
        "reservationId": "RESV-2024-0001",
        "userId": 12345,
        "orderId": "ORDER-2024-0001",
        "cancelledAt": "2024-01-01T10:10:00",
        "reason": "PAYMENT_FAILED"
    },
    "metadata": {
        "correlationId": "corr_2024010110100001",
        "source": "coupon-service",
        "version": "1.0"
    }
}
```

**발생 시점**
- 결제 실패
- 주문 취소
- 예약 타임아웃

## 구독 이벤트 (Subscribed Events)

### 1. PaymentCompletedEvent

**토픽**: `payment.completed.v1`

**설명**: 결제가 완료되었을 때 수신

**스키마**
```json
{
    "eventId": "evt_pay_2024010110000001",
    "eventType": "PAYMENT_COMPLETED",
    "timestamp": "2024-01-01T10:05:00Z",
    "data": {
        "paymentId": "PAY-2024-0001",
        "orderId": "ORDER-2024-0001",
        "userId": 12345,
        "amount": 90000,
        "couponId": 1001,
        "reservationId": "RESV-2024-0001",
        "paymentMethod": "CREDIT_CARD",
        "completedAt": "2024-01-01T10:05:00"
    },
    "metadata": {
        "correlationId": "corr_pay_2024010110000001",
        "source": "payment-service",
        "version": "1.0"
    }
}
```

**처리 로직**
1. 멱등성 체크 (이미 처리된 orderId인지 확인)
2. 예약된 쿠폰 상태를 USED로 변경
3. 사용 일시 기록
4. CouponUsedEvent 발행
5. 성공 시에만 ACK (실패 시 재처리)

### 2. PaymentFailedEvent

**토픽**: `payment.failed.v1`

**설명**: 결제가 실패했을 때 수신

**스키마**
```json
{
    "eventId": "evt_pay_2024010110100001",
    "eventType": "PAYMENT_FAILED",
    "timestamp": "2024-01-01T10:10:00Z",
    "data": {
        "paymentId": "PAY-2024-0002",
        "orderId": "ORDER-2024-0002",
        "userId": 12345,
        "couponId": 1001,
        "reservationId": "RESV-2024-0002",
        "failureReason": "INSUFFICIENT_BALANCE",
        "failedAt": "2024-01-01T10:10:00"
    },
    "metadata": {
        "correlationId": "corr_pay_2024010110100001",
        "source": "payment-service",
        "version": "1.0"
    }
}
```

**처리 로직**
1. 예약된 쿠폰 상태를 ISSUED로 복구
2. 예약 정보 삭제
3. CouponCancelledEvent 발행

### 3. OrderCancelledEvent

**토픽**: `order.cancelled`

**설명**: 주문이 취소되었을 때 수신

**스키마**
```json
{
    "eventId": "evt_ord_2024010111000001",
    "eventType": "ORDER_CANCELLED",
    "timestamp": "2024-01-01T11:00:00Z",
    "data": {
        "orderId": "ORDER-2024-0001",
        "userId": 12345,
        "couponId": 1001,
        "cancelReason": "CUSTOMER_REQUEST",
        "cancelledAt": "2024-01-01T11:00:00"
    },
    "metadata": {
        "correlationId": "corr_ord_2024010111000001",
        "source": "order-service",
        "version": "1.0"
    }
}
```

**처리 로직**
1. 사용된 쿠폰 상태를 ISSUED로 복구
2. 사용 기록 취소 처리
3. 환불 가능 여부 확인

## 이벤트 처리 패턴

### 멱등성 보장

```java
@Component
public class EventProcessor {
    private final Set<String> processedEvents = new ConcurrentHashMap<>();

    @KafkaListener(topics = "payment.completed")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        String eventId = event.getEventId();

        // 중복 처리 방지
        if (!processedEvents.add(eventId)) {
            log.info("이미 처리된 이벤트: {}", eventId);
            return;
        }

        // 이벤트 처리
        processCouponUsage(event);
    }
}
```

### 재시도 정책

```yaml
kafka:
  consumer:
    properties:
      max.poll.records: 10
      enable.auto.commit: false
  listener:
    ack-mode: manual
    concurrency: 3
    retry:
      attempts: 3
      delay: 1000
      multiplier: 2
      max-delay: 10000
```

### Dead Letter Queue

실패한 메시지는 DLQ로 전송됩니다:

- DLQ 토픽: `{original-topic}.DLQ`
- 보존 기간: 30일
- 수동 재처리 가능

## 모니터링

### 메트릭
- 이벤트 발행 수
- 이벤트 처리 수
- 처리 지연 시간
- 에러율

### 알람 조건
- 처리 지연 > 5초
- 에러율 > 1%
- DLQ 메시지 수 > 100

## 버전 관리

### 하위 호환성
- 필드 추가는 허용
- 필드 삭제는 deprecation 후 제거
- 필드 타입 변경 금지

### 버전 전략
```json
{
    "metadata": {
        "version": "1.0",
        "compatibleVersions": ["1.0", "1.1"]
    }
}
```
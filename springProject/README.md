# 쿠폰 서비스

## 개요
이커머스 플랫폼을 위한 고성능 쿠폰 관리 시스템입니다. 헥사고날 아키텍처를 기반으로 설계되었으며, 이벤트 기반 처리와 분산 락을 활용한 동시성 제어를 지원합니다.

## 주요 기능

### 쿠폰 정책 관리
- 다양한 할인 유형 지원 (정액, 정률)
- 발급 조건 및 제한 설정
- 유효 기간 관리
- 최대 발급 수량 제어

### 쿠폰 발급
- 선착순 발급 (분산 락 적용)
- 코드 기반 발급
- 다운로드 발급
- 이벤트 기반 발급

### 쿠폰 사용
- 예약 기반 사용 프로세스
- 결제 연동 (Kafka 이벤트 처리)
- 멱등성 보장
- 자동 타임아웃 처리

### 통계 및 모니터링
- 실시간 발급/사용 통계
- 정책별 성과 분석
- 사용자별 쿠폰 현황

## 기술 스택

- **언어**: Java 17
- **프레임워크**: Spring Boot 3.1.5
- **데이터베이스**: PostgreSQL
- **캐시**: Redis (분산 락)
- **메시징**: Apache Kafka
- **빌드**: Gradle
- **테스트**: JUnit 5, Mockito, TestContainers

## 아키텍처

### 헥사고날 아키텍처
```
adapter/
├── in/           # 인바운드 어댑터
│   ├── web/      # REST API
│   └── message/  # Kafka Consumer
├── out/          # 아웃바운드 어댑터
│   ├── persistence/  # JPA
│   └── cache/        # Redis
application/
├── port/         # 포트 인터페이스
├── service/      # 유스케이스 구현
domain/
└── model/        # 도메인 모델
```

### 이벤트 기반 처리
- Kafka를 통한 비동기 결제 이벤트 처리
- 수동 ACK 모드로 데이터 무결성 보장
- 멱등성 키를 통한 중복 처리 방지

### 동시성 제어
- Redis 분산 락으로 선착순 발급 구현
- 쿠폰 예약 시 사용자별 락 적용
- 낙관적 락으로 재고 관리

## 성능 최적화

### 최근 개선사항 (2024.12)

#### 1. Kafka 처리 성능 향상
- **동시 처리 스레드**: 3 → 10 증가
- **처리량**: 약 3.3배 향상
- **지연시간**: 평균 200ms → 60ms 감소

#### 2. 배치 처리 도입
- **타임아웃 처리**: 100건 단위 배치
- **DB 연결**: 100회 → 1회로 감소
- **처리 시간**: 80% 감소

#### 3. 쿼리 최적화
- 복합 인덱스 추가
- N+1 문제 해결 (Fetch Join)
- 불필요한 조회 제거

#### 4. 캐싱 전략
- 정책 정보 캐싱 (TTL: 5분)
- 사용자 쿠폰 목록 캐싱
- 캐시 워밍업 구현

### 성능 지표
- **TPS**: 5,000+ (단일 인스턴스)
- **응답시간**: P95 < 100ms
- **동시 사용자**: 10,000+
- **일일 처리량**: 1,000만+ 트랜잭션

## 설치 및 실행

### 사전 요구사항
- JDK 17+
- Docker & Docker Compose
- PostgreSQL 14+
- Redis 6+
- Kafka 3+

### 로컬 환경 실행
```bash
# 인프라 실행
docker-compose up -d

# 애플리케이션 빌드
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun
```

### 테스트 실행
```bash
# 전체 테스트
./gradlew test

# 통합 테스트
./gradlew integrationTest

# 테스트 커버리지
./gradlew jacocoTestReport
```

## API 문서

주요 API 엔드포인트는 [API.md](./docs/API.md) 참조

### 주요 엔드포인트
- `POST /api/v1/coupons/policies` - 쿠폰 정책 생성
- `POST /api/v1/coupons/issue` - 쿠폰 발급
- `POST /api/v1/coupons/reserve` - 쿠폰 예약
- `GET /api/v1/coupons/users/{userId}` - 사용자 쿠폰 조회

## 모니터링

### 메트릭
- Micrometer + Prometheus
- 커스텀 메트릭 (발급/사용 카운터)

### 로깅
- Logback + ELK Stack
- 구조화된 로깅 (JSON 형식)
- 트레이스 ID 기반 추적

### 알림
- 발급 한도 도달 시 알림
- 시스템 오류 임계값 초과 시 알림
- Kafka 랙 모니터링

## 보안

### 인증/인가
- JWT 토큰 기반 인증
- Spring Security 적용
- API 레이트 리미팅

### 데이터 보호
- 민감 정보 암호화
- SQL 인젝션 방지
- XSS 방지

## 트러블슈팅

### 일반적인 문제

#### 1. 쿠폰 발급 실패
- 분산 락 타임아웃 확인
- Redis 연결 상태 확인
- 정책 활성화 상태 확인

#### 2. Kafka 메시지 처리 지연
- 컨슈머 랙 확인
- 파티션 리밸런싱 상태 확인
- 스레드 풀 상태 확인

#### 3. 데이터베이스 연결 문제
- 커넥션 풀 설정 확인
- PostgreSQL 로그 확인
- 슬로우 쿼리 확인

## 기여하기

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 라이센스

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 연락처

- Team: TeamBind
- Email: support@teambind.com
- Documentation: [Wiki](https://github.com/teambind/coupon-service/wiki)
# 로컬 개발 환경 설정

## 개요

로컬 개발 환경을 구성하기 위한 단계별 가이드입니다. Docker Compose를 활용하여 의존성 서비스를 실행하고 IDE에서 개발할 수 있는 환경을 제공합니다.

## 필수 도구 설치

### 1. Java 21 설치

#### macOS
```bash
# Homebrew를 사용한 설치
brew install openjdk@21

# 환경 변수 설정
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 설치 확인
java -version
```

#### Ubuntu/Debian
```bash
# APT를 사용한 설치
sudo apt update
sudo apt install openjdk-21-jdk

# 설치 확인
java -version
```

#### Windows
```powershell
# Chocolatey를 사용한 설치
choco install openjdk21

# 또는 https://adoptium.net 에서 직접 다운로드

# 설치 확인
java -version
```

### 2. Gradle 설치

```bash
# SDKMAN을 사용한 설치 (추천)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.7

# 또는 Homebrew (macOS)
brew install gradle

# 설치 확인
gradle -v
```

### 3. Docker & Docker Compose 설치

#### macOS
```bash
# Docker Desktop 설치
brew install --cask docker

# Docker Desktop 실행 후 확인
docker --version
docker-compose --version
```

#### Ubuntu
```bash
# Docker 설치
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 사용자 그룹 추가
sudo usermod -aG docker $USER
newgrp docker

# 확인
docker --version
docker-compose --version
```

### 4. IDE 설정

#### IntelliJ IDEA
1. IntelliJ IDEA 설치 (Community 또는 Ultimate)
2. Spring Boot 플러그인 설치
3. Lombok 플러그인 설치 및 활성화
   - Settings → Plugins → Lombok 검색 및 설치
   - Settings → Build → Compiler → Annotation Processors → Enable annotation processing 체크

#### VS Code
```bash
# 확장 프로그램 설치
code --install-extension vscjava.vscode-java-pack
code --install-extension vscjava.vscode-spring-boot-dashboard
code --install-extension gabrielbb.vscode-lombok
```

## 프로젝트 설정

### 1. 소스 코드 클론

```bash
# 저장소 클론
git clone https://github.com/teambind/coupon-service.git
cd coupon-service

# 개발 브랜치 체크아웃
git checkout develop
```

### 2. 환경 변수 설정

```bash
# 로컬 환경 변수 파일 생성
cp .env.example .env.local

# .env.local 파일 편집
cat > .env.local << EOF
# Application
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080

# Database
DB_HOST=localhost
DB_PORT=25432
DB_NAME=coupon_local_db
DB_USERNAME=coupon_user
DB_PASSWORD=coupon_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=26379
REDIS_PASSWORD=redis_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:29092

# JWT (개발용)
JWT_SECRET=local_development_secret_key_for_testing_only
JWT_EXPIRATION=3600000
EOF
```

### 3. application-local.yml 생성

```yaml
# src/main/resources/application-local.yml
spring:
  profiles:
    active: local

  datasource:
    url: jdbc:postgresql://localhost:25432/coupon_local_db
    username: coupon_user
    password: coupon_password
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: true

  redis:
    host: localhost
    port: 26379
    password: redis_password
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0

  kafka:
    bootstrap-servers: localhost:29092
    consumer:
      group-id: coupon-service-local
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

logging:
  level:
    com.teambind.coupon: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.springframework.web: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: "*"
```

## Docker Compose 실행

### 1. 로컬 개발용 docker-compose.local.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: coupon-postgres-local
    ports:
      - "25432:5432"
    environment:
      POSTGRES_DB: coupon_local_db
      POSTGRES_USER: coupon_user
      POSTGRES_PASSWORD: coupon_password
    volumes:
      - postgres-local-data:/var/lib/postgresql/data
      - ./init-scripts/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U coupon_user"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: coupon-redis-local
    ports:
      - "26379:6379"
    command: redis-server --requirepass redis_password
    volumes:
      - redis-local-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "--auth", "redis_password", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    container_name: coupon-zookeeper-local
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    container_name: coupon-kafka-local
    ports:
      - "29092:29092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
    depends_on:
      - zookeeper
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: coupon-kafka-ui-local
    ports:
      - "28080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    depends_on:
      - kafka

  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: coupon-pgadmin-local
    ports:
      - "25433:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@localhost.com
      PGADMIN_DEFAULT_PASSWORD: admin
    volumes:
      - pgadmin-local-data:/var/lib/pgadmin

volumes:
  postgres-local-data:
  redis-local-data:
  pgadmin-local-data:
```

### 2. 인프라 실행

```bash
# Docker Compose 실행
docker-compose -f docker-compose.local.yml up -d

# 상태 확인
docker-compose -f docker-compose.local.yml ps

# 로그 확인
docker-compose -f docker-compose.local.yml logs -f

# 개별 서비스 로그
docker-compose -f docker-compose.local.yml logs -f postgres
docker-compose -f docker-compose.local.yml logs -f redis
docker-compose -f docker-compose.local.yml logs -f kafka
```

### 3. 초기 데이터베이스 설정

```sql
-- init-scripts/init.sql
-- 테이블 생성 (JPA가 자동 생성하지 않는 경우)
CREATE TABLE IF NOT EXISTS coupon_policies (
    id BIGSERIAL PRIMARY KEY,
    coupon_name VARCHAR(100) NOT NULL,
    coupon_code VARCHAR(50) UNIQUE NOT NULL,
    -- ... 나머지 컬럼
);

-- 초기 데이터
INSERT INTO coupon_policies (
    coupon_name, coupon_code, discount_type, discount_value,
    minimum_order_amount, issue_type, max_issue_count,
    current_issue_count, valid_days, issue_start_date,
    issue_end_date, is_active
) VALUES
    ('신규 가입 쿠폰', 'WELCOME2024', 'FIXED_AMOUNT', 10000,
     50000, 'CODE', 1000, 0, 30, NOW(), NOW() + INTERVAL '1 year', true),
    ('첫 구매 할인', 'FIRST2024', 'PERCENTAGE', 20,
     30000, 'DIRECT', 500, 0, 14, NOW(), NOW() + INTERVAL '6 months', true);
```

## 애플리케이션 실행

### 1. Gradle로 실행

```bash
# 의존성 다운로드
./gradlew dependencies

# 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 또는 환경 변수로 설정
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

### 2. IDE에서 실행

#### IntelliJ IDEA
1. Run/Debug Configurations 열기
2. Spring Boot Configuration 생성
3. Main class: `com.teambind.coupon.CouponServiceApplication`
4. Environment variables: `SPRING_PROFILES_ACTIVE=local`
5. Run 버튼 클릭

#### VS Code
1. `.vscode/launch.json` 생성
```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Spring Boot-CouponServiceApplication",
            "request": "launch",
            "cwd": "${workspaceFolder}",
            "mainClass": "com.teambind.coupon.CouponServiceApplication",
            "projectName": "coupon-service",
            "args": "--spring.profiles.active=local",
            "envFile": "${workspaceFolder}/.env.local"
        }
    ]
}
```
2. F5 키로 디버깅 시작

## 개발 도구

### 1. API 테스트 (Postman/Insomnia)

```bash
# Postman Collection 임포트
curl -o coupon-api.postman_collection.json \
    https://raw.githubusercontent.com/teambind/coupon-service/main/postman/collection.json
```

### 2. 데이터베이스 관리

```bash
# PgAdmin 접속
# URL: http://localhost:25433
# Email: admin@localhost.com
# Password: admin

# PostgreSQL 직접 접속
psql -h localhost -p 25432 -U coupon_user -d coupon_local_db
```

### 3. Redis 관리

```bash
# Redis CLI 접속
docker exec -it coupon-redis-local redis-cli -a redis_password

# Redis Commander 실행 (선택사항)
npm install -g redis-commander
redis-commander --redis-password redis_password --port 8081
```

### 4. Kafka 관리

```bash
# Kafka UI 접속
# URL: http://localhost:28080

# Kafka 토픽 생성
docker exec -it coupon-kafka-local kafka-topics \
    --create --topic coupon.issued \
    --bootstrap-server localhost:9092 \
    --partitions 3 --replication-factor 1

# 토픽 목록 확인
docker exec -it coupon-kafka-local kafka-topics \
    --list --bootstrap-server localhost:9092
```

## 테스트 실행

### 1. 단위 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 클래스 테스트
./gradlew test --tests "com.teambind.coupon.service.CouponServiceTest"

# 테스트 리포트 확인
open build/reports/tests/test/index.html
```

### 2. 통합 테스트

```bash
# 통합 테스트 프로필로 실행
./gradlew test --tests "*IntegrationTest" \
    -Dspring.profiles.active=test
```

### 3. 테스트 커버리지

```bash
# JaCoCo 리포트 생성
./gradlew test jacocoTestReport

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

## 문제 해결

### 포트 충돌

```bash
# 사용 중인 포트 확인
lsof -i :8080
lsof -i :25432
lsof -i :26379
lsof -i :29092

# 프로세스 종료
kill -9 <PID>
```

### Docker 리소스 정리

```bash
# 컨테이너 정리
docker-compose -f docker-compose.local.yml down -v

# 모든 미사용 리소스 정리
docker system prune -a --volumes
```

### 로그 확인

```bash
# Spring Boot 로그
tail -f logs/spring-boot.log

# Docker 컨테이너 로그
docker logs -f coupon-postgres-local
docker logs -f coupon-redis-local
docker logs -f coupon-kafka-local
```

## 개발 팁

### 1. Hot Reload 설정

```gradle
// build.gradle
configurations {
    developmentOnly
    runtimeClasspath {
        extendsFrom developmentOnly
    }
}

dependencies {
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}
```

### 2. 로컬 프로필 전환

```bash
# 테스트 프로필
SPRING_PROFILES_ACTIVE=test ./gradlew bootRun

# 디버그 모드
SPRING_PROFILES_ACTIVE=local,debug ./gradlew bootRun
```

### 3. 성능 프로파일링

```bash
# JVM 옵션 추가
export JAVA_OPTS="-XX:+FlightRecorder -XX:StartFlightRecording=filename=recording.jfr"
./gradlew bootRun
```
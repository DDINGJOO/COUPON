# Docker Compose 구성

## 개요

Docker Compose를 사용한 쿠폰 서비스 실행 가이드입니다. 개발, 테스트, 프로덕션 환경별 구성을 제공합니다.

## 디렉토리 구조

```
docker/
├── docker-compose.yml          # 기본 구성
├── docker-compose.dev.yml      # 개발 환경
├── docker-compose.test.yml     # 테스트 환경
├── docker-compose.prod.yml     # 프로덕션 환경
├── .env.example                # 환경 변수 예시
├── nginx/
│   ├── nginx.conf             # Nginx 설정
│   └── ssl/                   # SSL 인증서
├── postgres/
│   ├── init.sql               # DB 초기화 스크립트
│   └── backup.sh              # 백업 스크립트
└── monitoring/
    ├── prometheus.yml          # Prometheus 설정
    └── grafana/               # Grafana 대시보드
```

## 기본 Docker Compose 구성

### docker-compose.yml

```yaml
version: '3.8'

x-common-variables: &common-variables
  TZ: Asia/Seoul
  LANG: C.UTF-8

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: coupon-postgres
    restart: unless-stopped
    ports:
      - "${DB_PORT:-5432}:5432"
    environment:
      <<: *common-variables
      POSTGRES_DB: ${DB_NAME:-coupon_db}
      POSTGRES_USER: ${DB_USER:-coupon_user}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_INITDB_ARGS: "--encoding=UTF-8 --lc-collate=C --lc-ctype=C"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
      - ./postgres/backup:/backup
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-coupon_user}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - coupon-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # Redis Cache
  redis:
    image: redis:7-alpine
    container_name: coupon-redis
    restart: unless-stopped
    ports:
      - "${REDIS_PORT:-6379}:6379"
    environment:
      <<: *common-variables
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
      --appendonly yes
      --appendfilename "redis-appendonly.aof"
    volumes:
      - redis-data:/data
      - ./redis/redis.conf:/usr/local/etc/redis/redis.conf:ro
    healthcheck:
      test: ["CMD", "redis-cli", "--auth", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - coupon-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # Zookeeper (for Kafka)
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    container_name: coupon-zookeeper
    restart: unless-stopped
    environment:
      <<: *common-variables
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
      ZOOKEEPER_SYNC_LIMIT: 2
      ZOOKEEPER_INIT_LIMIT: 5
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
      - zookeeper-logs:/var/lib/zookeeper/log
    networks:
      - coupon-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # Kafka Message Broker
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    container_name: coupon-kafka
    restart: unless-stopped
    ports:
      - "${KAFKA_PORT:-9092}:9092"
      - "${KAFKA_EXTERNAL_PORT:-29092}:29092"
    environment:
      <<: *common-variables
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:9092,EXTERNAL://localhost:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_SEGMENT_BYTES: 1073741824
      KAFKA_LOG_RETENTION_BYTES: 1073741824
    volumes:
      - kafka-data:/var/lib/kafka/data
    depends_on:
      zookeeper:
        condition: service_started
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - coupon-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  # Coupon Service Application
  coupon-service:
    build:
      context: .
      dockerfile: Dockerfile
      args:
        JAR_FILE: build/libs/coupon-service-*.jar
    image: coupon-service:${VERSION:-latest}
    container_name: coupon-service
    restart: unless-stopped
    ports:
      - "${APP_PORT:-8080}:8080"
      - "${DEBUG_PORT:-5005}:5005"
    environment:
      <<: *common-variables
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILE:-docker}
      # Database
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${DB_NAME:-coupon_db}
      SPRING_DATASOURCE_USERNAME: ${DB_USER:-coupon_user}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      # Redis
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
      SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD}
      # Kafka
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      # JVM Options
      JAVA_OPTS: >
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -XX:InitialRAMPercentage=50.0
        -XX:+UseG1GC
        -XX:+DisableExplicitGC
        -Djava.security.egd=file:/dev/./urandom
        -Dspring.profiles.active=${SPRING_PROFILE:-docker}
    volumes:
      - ./logs:/app/logs
      - ./config:/app/config:ro
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - coupon-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "5"

  # Nginx Reverse Proxy
  nginx:
    image: nginx:alpine
    container_name: coupon-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - nginx-cache:/var/cache/nginx
    depends_on:
      - coupon-service
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - coupon-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

volumes:
  postgres-data:
    driver: local
  redis-data:
    driver: local
  zookeeper-data:
    driver: local
  zookeeper-logs:
    driver: local
  kafka-data:
    driver: local
  nginx-cache:
    driver: local

networks:
  coupon-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16
```

## 환경별 구성

### 개발 환경 (docker-compose.dev.yml)

```yaml
version: '3.8'

services:
  coupon-service:
    build:
      context: .
      dockerfile: Dockerfile.dev
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_JPA_SHOW_SQL: "true"
      LOGGING_LEVEL_COM_TEAMBIND: DEBUG
      JAVA_OPTS: >
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=50.0
        -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    volumes:
      - ./src:/app/src:ro
      - ./build:/app/build
      - ~/.gradle:/home/gradle/.gradle
    ports:
      - "8080:8080"
      - "5005:5005"

  # 개발 도구들
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: coupon-kafka-ui
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    depends_on:
      - kafka
    networks:
      - coupon-network

  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: coupon-pgadmin
    ports:
      - "5050:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@coupon.local
      PGADMIN_DEFAULT_PASSWORD: admin
    volumes:
      - pgadmin-data:/var/lib/pgadmin
    networks:
      - coupon-network

  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: coupon-redis-commander
    ports:
      - "8091:8081"
    environment:
      REDIS_HOSTS: local:redis:6379:0:${REDIS_PASSWORD}
    depends_on:
      - redis
    networks:
      - coupon-network

volumes:
  pgadmin-data:
```

### 테스트 환경 (docker-compose.test.yml)

```yaml
version: '3.8'

services:
  coupon-service:
    environment:
      SPRING_PROFILES_ACTIVE: test
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/coupon_test_db
      LOGGING_LEVEL_ROOT: WARN
      LOGGING_LEVEL_COM_TEAMBIND: INFO
    command: ["sh", "-c", "./gradlew test"]
    volumes:
      - ./build/test-results:/app/build/test-results
      - ./build/reports:/app/build/reports

  postgres:
    environment:
      POSTGRES_DB: coupon_test_db
    tmpfs:
      - /var/lib/postgresql/data

  redis:
    tmpfs:
      - /data
```

### 프로덕션 환경 (docker-compose.prod.yml)

```yaml
version: '3.8'

services:
  coupon-service:
    image: ${DOCKER_REGISTRY}/coupon-service:${VERSION}
    deploy:
      replicas: 3
      update_config:
        parallelism: 1
        delay: 10s
        failure_action: rollback
      restart_policy:
        condition: any
        delay: 5s
        max_attempts: 3
        window: 120s
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
    environment:
      SPRING_PROFILES_ACTIVE: prod
      JAVA_OPTS: >
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=100
        -XX:+UseStringDeduplication
    secrets:
      - db_password
      - redis_password
      - jwt_secret

  postgres:
    deploy:
      placement:
        constraints:
          - node.role == manager
    volumes:
      - postgres-prod-data:/var/lib/postgresql/data
      - postgres-prod-backup:/backup
    configs:
      - source: postgres_config
        target: /etc/postgresql/postgresql.conf

  redis:
    deploy:
      replicas: 1
    command: >
      redis-server
      --requirepass ${REDIS_PASSWORD}
      --maxmemory 2gb
      --maxmemory-policy volatile-lru
      --save 900 1
      --save 300 10
      --save 60 10000

  nginx:
    deploy:
      replicas: 2
      placement:
        preferences:
          - spread: node.id
    configs:
      - source: nginx_config
        target: /etc/nginx/nginx.conf
    secrets:
      - source: ssl_cert
        target: /etc/nginx/ssl/cert.pem
      - source: ssl_key
        target: /etc/nginx/ssl/key.pem

volumes:
  postgres-prod-data:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /data/postgres
  postgres-prod-backup:
    driver: local
    driver_opts:
      type: none
      o: bind
      device: /backup/postgres

configs:
  postgres_config:
    file: ./postgres/postgresql.conf
  nginx_config:
    file: ./nginx/nginx.prod.conf

secrets:
  db_password:
    external: true
  redis_password:
    external: true
  jwt_secret:
    external: true
  ssl_cert:
    external: true
  ssl_key:
    external: true
```

## 실행 명령

### 기본 실행

```bash
# 환경 변수 파일 생성
cp .env.example .env

# 서비스 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 서비스 중지
docker-compose down

# 볼륨 포함 삭제
docker-compose down -v
```

### 환경별 실행

```bash
# 개발 환경
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# 테스트 환경
docker-compose -f docker-compose.yml -f docker-compose.test.yml up --abort-on-container-exit

# 프로덕션 환경
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## 스케일링

```bash
# 서비스 스케일 업
docker-compose up -d --scale coupon-service=3

# 특정 서비스만 재시작
docker-compose restart coupon-service

# Rolling update
docker-compose up -d --no-deps --build coupon-service
```

## 모니터링

### Prometheus 추가

```yaml
# docker-compose.monitoring.yml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: coupon-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    networks:
      - coupon-network

  grafana:
    image: grafana/grafana:latest
    container_name: coupon-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources:ro
    depends_on:
      - prometheus
    networks:
      - coupon-network

volumes:
  prometheus-data:
  grafana-data:
```

## 백업 및 복구

### 백업 스크립트

```bash
#!/bin/bash
# backup.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backup"

# PostgreSQL 백업
docker exec coupon-postgres pg_dump -U coupon_user coupon_db | gzip > $BACKUP_DIR/postgres_$DATE.sql.gz

# Redis 백업
docker exec coupon-redis redis-cli --auth $REDIS_PASSWORD BGSAVE
docker cp coupon-redis:/data/dump.rdb $BACKUP_DIR/redis_$DATE.rdb

# 볼륨 백업
docker run --rm -v coupon_postgres-data:/data -v $BACKUP_DIR:/backup alpine tar czf /backup/postgres-volume_$DATE.tar.gz /data

echo "Backup completed: $DATE"
```

### 복구 스크립트

```bash
#!/bin/bash
# restore.sh

BACKUP_DATE=$1
BACKUP_DIR="/backup"

# PostgreSQL 복구
gunzip -c $BACKUP_DIR/postgres_$BACKUP_DATE.sql.gz | docker exec -i coupon-postgres psql -U coupon_user coupon_db

# Redis 복구
docker cp $BACKUP_DIR/redis_$BACKUP_DATE.rdb coupon-redis:/data/dump.rdb
docker restart coupon-redis

echo "Restore completed from: $BACKUP_DATE"
```

## 문제 해결

### 일반적인 문제

| 문제 | 해결 방법 |
|------|-----------|
| 컨테이너 시작 실패 | `docker-compose logs <service-name>` 로그 확인 |
| 포트 충돌 | `.env` 파일에서 포트 변경 |
| 메모리 부족 | Docker Desktop 메모리 할당 증가 |
| 네트워크 연결 실패 | `docker network inspect coupon-network` 확인 |

### 디버깅 명령

```bash
# 컨테이너 내부 접속
docker exec -it coupon-service /bin/sh

# 네트워크 테스트
docker exec coupon-service ping postgres

# 리소스 사용량 확인
docker stats

# 컨테이너 검사
docker inspect coupon-service
```
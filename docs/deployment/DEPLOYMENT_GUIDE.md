# 배포 가이드

## 개요

쿠폰 서비스의 프로덕션 환경 배포를 위한 상세 가이드입니다. Docker 컨테이너 기반 배포와 Kubernetes 오케스트레이션을 지원합니다.

## 시스템 요구사항

### 하드웨어
- CPU: 4 Core 이상
- Memory: 8GB 이상
- Storage: 50GB 이상 SSD

### 소프트웨어
- OS: Ubuntu 20.04 LTS 또는 CentOS 8
- Docker: 20.10 이상
- Docker Compose: 2.0 이상
- Java: 21 (빌드 시)
- Gradle: 8.7 (빌드 시)

## 빌드 프로세스

### 1. 소스 코드 준비

```bash
# 저장소 클론
git clone https://github.com/teambind/coupon-service.git
cd coupon-service

# 환경별 브랜치 체크아웃
git checkout main  # production
# git checkout develop  # development
# git checkout staging  # staging
```

### 2. 애플리케이션 빌드

```bash
# Gradle 빌드
./gradlew clean build

# 테스트 스킵 빌드
./gradlew clean build -x test

# 빌드 결과 확인
ls -la build/libs/
# coupon-service-1.0.0.jar
```

### 3. Docker 이미지 빌드

```bash
# Docker 이미지 빌드
docker build -t coupon-service:1.0.0 .

# 빌드 확인
docker images | grep coupon-service
```

#### Dockerfile
```dockerfile
FROM eclipse-temurin:21-jre-alpine

# 보안을 위한 non-root 사용자 생성
RUN addgroup -g 1000 spring && \
    adduser -D -s /bin/sh -u 1000 -G spring spring

# 애플리케이션 디렉토리
WORKDIR /app

# JAR 파일 복사
COPY --chown=spring:spring build/libs/coupon-service-*.jar app.jar

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# 사용자 전환
USER spring:spring

# JVM 옵션과 함께 실행
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]

EXPOSE 8080
```

## 환경 설정

### 1. 환경 변수 설정

```bash
# .env.production 파일 생성
cat > .env.production << EOF
# Application
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080

# Database
DB_HOST=postgres
DB_PORT=5432
DB_NAME=coupon_db
DB_USERNAME=coupon_user
DB_PASSWORD=\${DB_PASSWORD}

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=\${REDIS_PASSWORD}

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092

# JWT
JWT_SECRET=\${JWT_SECRET}
JWT_EXPIRATION=3600000

# Monitoring
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus
EOF
```

### 2. 시크릿 관리

```bash
# Kubernetes Secret 생성
kubectl create secret generic coupon-service-secrets \
    --from-literal=db-password='yourDbPassword' \
    --from-literal=redis-password='yourRedisPassword' \
    --from-literal=jwt-secret='yourJwtSecret'

# Docker Swarm Secret
echo "yourDbPassword" | docker secret create db_password -
echo "yourRedisPassword" | docker secret create redis_password -
echo "yourJwtSecret" | docker secret create jwt_secret -
```

## Docker Compose 배포

### 1. docker-compose.yml

```yaml
version: '3.8'

services:
  coupon-service:
    image: coupon-service:1.0.0
    container_name: coupon-service
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: production
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: coupon_db
      REDIS_HOST: redis
      REDIS_PORT: 6379
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_started
    networks:
      - coupon-network
    restart: unless-stopped

  postgres:
    image: postgres:15-alpine
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: coupon_db
      POSTGRES_USER: coupon_user
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init-scripts:/docker-entrypoint-initdb.d
    secrets:
      - db_password
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U coupon_user"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - coupon-network

  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"
    command: redis-server --requirepass /run/secrets/redis_password
    volumes:
      - redis-data:/data
    secrets:
      - redis_password
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - coupon-network

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    container_name: kafka
    ports:
      - "29092:29092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    depends_on:
      - zookeeper
    networks:
      - coupon-network

  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - coupon-network

  nginx:
    image: nginx:alpine
    container_name: nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    depends_on:
      - coupon-service
    networks:
      - coupon-network

volumes:
  postgres-data:
  redis-data:

networks:
  coupon-network:
    driver: bridge

secrets:
  db_password:
    external: true
  redis_password:
    external: true
```

### 2. 배포 실행

```bash
# Docker Compose 실행
docker-compose -f docker-compose.yml up -d

# 로그 확인
docker-compose logs -f coupon-service

# 상태 확인
docker-compose ps
```

## Kubernetes 배포

### 1. Deployment 설정

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: coupon-service
  labels:
    app: coupon-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: coupon-service
  template:
    metadata:
      labels:
        app: coupon-service
    spec:
      containers:
      - name: coupon-service
        image: coupon-service:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: coupon-service-secrets
              key: db-password
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: coupon-service-secrets
              key: redis-password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
```

### 2. Service 설정

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: coupon-service
spec:
  selector:
    app: coupon-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

### 3. HPA 설정

```yaml
# k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: coupon-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: coupon-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### 4. 배포 실행

```bash
# 네임스페이스 생성
kubectl create namespace coupon-service

# 배포 적용
kubectl apply -f k8s/ -n coupon-service

# 배포 상태 확인
kubectl get all -n coupon-service

# 롤링 업데이트
kubectl set image deployment/coupon-service \
    coupon-service=coupon-service:1.0.1 \
    -n coupon-service

# 롤백
kubectl rollout undo deployment/coupon-service -n coupon-service
```

## 무중단 배포

### Blue-Green 배포

```bash
# Blue 환경 (현재 운영)
docker-compose -f docker-compose-blue.yml up -d

# Green 환경 준비
docker-compose -f docker-compose-green.yml up -d

# 헬스 체크
./health-check.sh green

# 트래픽 전환
./switch-traffic.sh green

# Blue 환경 종료
docker-compose -f docker-compose-blue.yml down
```

### Rolling Update

```bash
# Kubernetes Rolling Update
kubectl set image deployment/coupon-service \
    coupon-service=coupon-service:new-version \
    --record

# 업데이트 상태 모니터링
kubectl rollout status deployment/coupon-service

# 롤백 (필요시)
kubectl rollout undo deployment/coupon-service
```

## 헬스 체크

### 헬스 체크 스크립트

```bash
#!/bin/bash
# health-check.sh

SERVICE_URL=${1:-"http://localhost:8080"}
MAX_ATTEMPTS=30
ATTEMPT=0

echo "Checking health of $SERVICE_URL"

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
        "$SERVICE_URL/actuator/health")

    if [ "$HTTP_STATUS" -eq 200 ]; then
        echo "Service is healthy"
        exit 0
    fi

    ATTEMPT=$((ATTEMPT + 1))
    echo "Attempt $ATTEMPT failed with status $HTTP_STATUS"
    sleep 2
done

echo "Health check failed after $MAX_ATTEMPTS attempts"
exit 1
```

## 로그 수집

### ELK Stack 연동

```yaml
# filebeat.yml
filebeat.inputs:
- type: container
  paths:
    - '/var/lib/docker/containers/*/*.log'
  processors:
    - add_docker_metadata:
        host: "unix:///var/run/docker.sock"
  fields:
    service: coupon-service
    environment: production

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "coupon-service-%{+yyyy.MM.dd}"
```

## 백업 및 복구

### 데이터베이스 백업

```bash
# 백업 스크립트
#!/bin/bash
BACKUP_DIR="/backup/postgres"
DATE=$(date +%Y%m%d_%H%M%S)

# PostgreSQL 백업
docker exec postgres pg_dump -U coupon_user coupon_db \
    > "$BACKUP_DIR/coupon_db_$DATE.sql"

# S3 업로드
aws s3 cp "$BACKUP_DIR/coupon_db_$DATE.sql" \
    s3://backup-bucket/postgres/

# 오래된 백업 삭제 (30일 이상)
find $BACKUP_DIR -name "*.sql" -mtime +30 -delete
```

### 복구 절차

```bash
# 데이터베이스 복구
docker exec -i postgres psql -U coupon_user coupon_db \
    < /backup/coupon_db_20240101_120000.sql

# Redis 복구
docker exec -i redis redis-cli --rdb /backup/redis_backup.rdb
```

## 모니터링 설정

### Prometheus 설정

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'coupon-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['coupon-service:8080']
```

### Grafana 대시보드

- JVM 메트릭 대시보드
- 비즈니스 메트릭 대시보드
- 인프라 메트릭 대시보드

## 트러블슈팅

### 일반적인 문제 해결

| 문제 | 원인 | 해결 방법 |
|------|------|-----------|
| 서비스 시작 실패 | 데이터베이스 연결 실패 | 데이터베이스 상태 및 연결 정보 확인 |
| 메모리 부족 | JVM 힙 설정 부족 | JVM 옵션 조정 (-Xmx 증가) |
| 느린 응답 | 캐시 미스 | Redis 연결 및 캐시 설정 확인 |
| 메시지 처리 실패 | Kafka 연결 문제 | Kafka 브로커 상태 확인 |

### 로그 확인

```bash
# 애플리케이션 로그
docker logs -f coupon-service --tail 100

# 시스템 로그
journalctl -u docker -f

# Kubernetes 로그
kubectl logs -f deployment/coupon-service
```
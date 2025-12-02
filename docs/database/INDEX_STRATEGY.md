# 인덱스 전략

## 개요

쿠폰 서비스의 성능 최적화를 위한 데이터베이스 인덱스 설계 전략입니다. 쿼리 패턴 분석을 기반으로 최적의 인덱스를 구성하여 응답 시간을 단축하고 처리량을 향상시킵니다.

## 인덱스 설계 원칙

### 1. 선택도 (Selectivity)
- 높은 선택도를 가진 컬럼을 우선 인덱싱
- 카디널리티가 높은 컬럼을 선택

### 2. 쿼리 패턴
- 자주 사용되는 WHERE, JOIN, ORDER BY 절 분석
- 실행 계획 기반 최적화

### 3. 인덱스 비용
- 쓰기 성능과 읽기 성능의 균형
- 저장 공간 고려

## 테이블별 인덱스 전략

### coupon_policies 테이블

#### 기본 인덱스
```sql
-- Primary Key
CREATE UNIQUE INDEX pk_coupon_policies ON coupon_policies(id);

-- 쿠폰 코드 유니크 인덱스
CREATE UNIQUE INDEX uk_coupon_code ON coupon_policies(coupon_code);
```

#### 비즈니스 인덱스
```sql
-- 활성 정책 조회 최적화
CREATE INDEX idx_active_policies ON coupon_policies(is_active, issue_end_date)
WHERE is_active = TRUE;

-- 발급 가능 정책 조회
CREATE INDEX idx_issuable_policies ON coupon_policies(
    is_active,
    issue_start_date,
    issue_end_date,
    current_issue_count,
    max_issue_count
) WHERE is_active = TRUE;

-- 정책 타입별 조회
CREATE INDEX idx_policy_type ON coupon_policies(issue_type, is_active)
WHERE is_active = TRUE;
```

#### 쿼리 최적화 예시
```sql
-- Before: Full Table Scan (Cost: 1000)
SELECT * FROM coupon_policies
WHERE is_active = TRUE
  AND issue_start_date <= NOW()
  AND issue_end_date >= NOW();

-- After: Index Scan (Cost: 10)
-- Uses: idx_issuable_policies
```

### coupon_issues 테이블

#### 기본 인덱스
```sql
-- Primary Key
CREATE UNIQUE INDEX pk_coupon_issues ON coupon_issues(id);

-- 쿠폰 번호 유니크 인덱스
CREATE UNIQUE INDEX uk_coupon_number ON coupon_issues(coupon_number);

-- Foreign Key 인덱스
CREATE INDEX fk_policy_id ON coupon_issues(policy_id);
```

#### 비즈니스 인덱스
```sql
-- 사용자별 쿠폰 조회
CREATE INDEX idx_user_coupons ON coupon_issues(user_id, status, expiry_date);

-- 정책별 쿠폰 통계
CREATE INDEX idx_policy_statistics ON coupon_issues(policy_id, status)
INCLUDE (created_at, used_at);

-- 만료 처리 대상 조회
CREATE INDEX idx_expiry_processing ON coupon_issues(expiry_date, status)
WHERE status = 'ISSUED';

-- 예약 ID 조회 (부분 인덱스)
CREATE INDEX idx_reservation_lookup ON coupon_issues(reservation_id)
WHERE reservation_id IS NOT NULL;

-- 중복 발급 방지
CREATE UNIQUE INDEX idx_unique_user_policy ON coupon_issues(policy_id, user_id)
WHERE status NOT IN ('CANCELLED');
```

#### 복합 인덱스 전략
```sql
-- 사용자 쿠폰 목록 조회 최적화
CREATE INDEX idx_user_coupon_list ON coupon_issues(
    user_id,
    status,
    expiry_date DESC,
    created_at DESC
);

-- 통계 집계 최적화
CREATE INDEX idx_statistics_aggregation ON coupon_issues(
    policy_id,
    status,
    date_trunc('day', created_at)
);
```

### coupon_reservations 테이블

#### 기본 인덱스
```sql
-- Primary Key
CREATE UNIQUE INDEX pk_coupon_reservations ON coupon_reservations(reservation_id);

-- Foreign Key 인덱스
CREATE INDEX fk_coupon_id ON coupon_reservations(coupon_id);
```

#### 비즈니스 인덱스
```sql
-- 만료 예약 조회
CREATE INDEX idx_expired_reservations ON coupon_reservations(expires_at)
WHERE status = 'PENDING';

-- 주문별 예약 조회
CREATE INDEX idx_order_reservations ON coupon_reservations(order_id, status);

-- 사용자별 예약 이력
CREATE INDEX idx_user_reservation_history ON coupon_reservations(
    user_id,
    status,
    created_at DESC
);
```

## 파티션 인덱스

### 시계열 데이터 파티셔닝
```sql
-- 월별 파티션 테이블 생성
CREATE TABLE coupon_issues_2024_01 PARTITION OF coupon_issues
FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- 각 파티션에 로컬 인덱스 생성
CREATE INDEX idx_coupon_issues_2024_01_user
ON coupon_issues_2024_01(user_id, status);

CREATE INDEX idx_coupon_issues_2024_01_policy
ON coupon_issues_2024_01(policy_id, status);
```

## 인덱스 성능 분석

### 실행 계획 분석
```sql
-- 인덱스 사용 확인
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM coupon_issues
WHERE user_id = 12345
  AND status = 'ISSUED'
ORDER BY expiry_date;

-- 결과
Index Scan using idx_user_coupons on coupon_issues
  Index Cond: ((user_id = 12345) AND (status = 'ISSUED'::text))
  Planning Time: 0.150 ms
  Execution Time: 0.235 ms
```

### 인덱스 사용률 모니터링
```sql
-- 인덱스 사용 통계
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;

-- 미사용 인덱스 찾기
SELECT
    indexrelid::regclass AS index,
    relid::regclass AS table,
    idx_scan,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE idx_scan < 100
  AND pg_relation_size(indexrelid) > 1000000;
```

## 인덱스 유지보수

### 인덱스 리빌드
```sql
-- 인덱스 팽창 확인
SELECT
    c.relname AS index_name,
    pg_size_pretty(pg_relation_size(c.oid)) AS index_size,
    CASE WHEN pg_stat_get_live_tuples(c.oid) = 0 THEN 0
         ELSE round(100 * pg_stat_get_dead_tuples(c.oid)::numeric /
              pg_stat_get_live_tuples(c.oid), 2)
    END AS dead_tuple_ratio
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'i'
  AND n.nspname = 'public'
ORDER BY pg_relation_size(c.oid) DESC;

-- 인덱스 리빌드 (CONCURRENTLY 옵션으로 무중단 실행)
REINDEX INDEX CONCURRENTLY idx_user_coupons;
```

### VACUUM 전략
```sql
-- 자동 VACUUM 설정
ALTER TABLE coupon_issues SET (
    autovacuum_vacuum_scale_factor = 0.01,
    autovacuum_analyze_scale_factor = 0.05
);

-- 수동 VACUUM
VACUUM (VERBOSE, ANALYZE) coupon_issues;
```

## 쿼리 최적화 사례

### Case 1: 사용자 쿠폰 목록 조회

#### 최적화 전
```sql
-- Cost: 5234, Time: 523ms
SELECT ci.*, cp.*
FROM coupon_issues ci
JOIN coupon_policies cp ON ci.policy_id = cp.id
WHERE ci.user_id = 12345
  AND ci.status = 'ISSUED'
  AND ci.expiry_date > NOW()
ORDER BY ci.created_at DESC
LIMIT 20;
```

#### 최적화 후
```sql
-- Cost: 45, Time: 4ms
-- 커버링 인덱스 활용
WITH user_coupons AS (
    SELECT id, policy_id, coupon_number, expiry_date, created_at
    FROM coupon_issues
    WHERE user_id = 12345
      AND status = 'ISSUED'
      AND expiry_date > NOW()
    ORDER BY created_at DESC
    LIMIT 20
)
SELECT uc.*, cp.*
FROM user_coupons uc
JOIN coupon_policies cp ON uc.policy_id = cp.id;
```

### Case 2: 통계 집계

#### 최적화 전
```sql
-- Cost: 12456, Time: 1245ms
SELECT
    policy_id,
    COUNT(*) as total,
    COUNT(*) FILTER (WHERE status = 'USED') as used,
    COUNT(*) FILTER (WHERE status = 'ISSUED') as issued
FROM coupon_issues
GROUP BY policy_id;
```

#### 최적화 후
```sql
-- Cost: 234, Time: 23ms
-- Materialized View 활용
SELECT * FROM mv_coupon_statistics;
```

## 인덱스 모니터링 대시보드

### 주요 메트릭
```sql
-- 인덱스 히트율
SELECT
    sum(idx_blks_hit) / NULLIF(sum(idx_blks_hit + idx_blks_read), 0) * 100 AS index_hit_ratio
FROM pg_statio_user_indexes;

-- 테이블별 인덱스 효율성
SELECT
    schemaname,
    tablename,
    100 * idx_scan / NULLIF(seq_scan + idx_scan, 0) AS index_usage_ratio
FROM pg_stat_user_tables
ORDER BY index_usage_ratio ASC;

-- 인덱스 크기 분포
SELECT
    tablename,
    COUNT(*) AS index_count,
    pg_size_pretty(SUM(pg_relation_size(indexrelid))) AS total_index_size
FROM pg_stat_user_indexes
GROUP BY tablename
ORDER BY SUM(pg_relation_size(indexrelid)) DESC;
```

## 권장 사항

### DO
1. 선택도가 높은 컬럼을 인덱스로 생성
2. 복합 인덱스의 컬럼 순서 최적화
3. 부분 인덱스 활용으로 인덱스 크기 최소화
4. 정기적인 인덱스 사용률 모니터링
5. VACUUM과 REINDEX 정기 수행

### DON'T
1. 모든 컬럼에 인덱스 생성 지양
2. 낮은 카디널리티 컬럼 단독 인덱싱 회피
3. 과도한 복합 인덱스 생성 자제
4. 쓰기가 빈번한 컬럼의 불필요한 인덱싱 회피
5. 인덱스 팽창 방치
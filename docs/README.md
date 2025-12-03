# 쿠폰 서비스 프로젝트 문서

## 문서 구조

### 1. [프로젝트 개요](PROJECT_OVERVIEW.md)
- 프로젝트 목적 및 배경
- 주요 기능
- 기술 스택

### 2. [아키텍처 설계](./architecture/)
- [시스템 아키텍처](architecture/SYSTEM_ARCHITECTURE.md)
- [헥사고날 아키텍처](architecture/HEXAGONAL_ARCHITECTURE.md)
- [기술적 의사결정](architecture/TECHNICAL_DECISIONS.md)
- [트레이드오프 분석](architecture/TRADE_OFFS.md)

### 3. [API 명세](./api/)
- [REST API 명세서](api/REST_API_SPECIFICATION.md)
- [이벤트 명세](api/EVENT_SPECIFICATION.md)
- [에러 코드](api/ERROR_CODES.md)

### 4. [기능 명세](./features/)
- [쿠폰 정책 관리](features/COUPON_POLICY.md)
- [쿠폰 발급](features/COUPON_ISSUE.md)
- [쿠폰 사용](features/COUPON_USE.md)
- [통계 및 모니터링](features/STATISTICS.md)

### 5. [데이터베이스](./database/)
- [ERD 및 스키마](database/SCHEMA.md)
- [인덱스 전략](database/INDEX_STRATEGY.md)

### 6. [배포 및 운영](./deployment/)
- [배포 가이드](deployment/DEPLOYMENT_GUIDE.md)
- [모니터링 전략](deployment/MONITORING.md)

## Quick Start

1. [로컬 환경 설정](deployment/LOCAL_SETUP.md)
2. [Docker Compose 실행](deployment/DOCKER_COMPOSE.md)
3. [API 테스트](./api/API_TESTING.md)

## 프로젝트 현황

- **버전**: 2.0.0
- **상태**: Production Ready
- **마지막 업데이트**: 2024-12-03

## 최근 변경사항 (v2.0.0 - 2024.12.03)

### 주요 개선사항
- **Kafka 메시지 처리 안정성 개선**: ACK 전략 수정으로 데이터 손실 방지
- **멱등성 보장**: 중복 이벤트 처리 방지 로직 추가
- **성능 최적화**: Kafka 동시 처리 3→10 스레드, 배치 처리 도입
- **타임아웃 로직 개선**: reservationId 보존으로 늦은 이벤트 처리 가능
- **PostgreSQL 마이그레이션**: H2에서 PostgreSQL로 테스트 환경 전환

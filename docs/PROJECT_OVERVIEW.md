# 프로젝트 개요

## 프로젝트 소개

쿠폰 서비스는 이커머스 플랫폼을 위한 독립적인 쿠폰 관리 마이크로서비스입니다. 쿠폰 생성, 발급, 사용, 통계 등 쿠폰 라이프사이클 전반을 관리합니다.

## 프로젝트 목적

### 비즈니스 목적
- 마케팅 프로모션을 위한 유연한 쿠폰 시스템 제공
- 고객 획득 및 유지를 위한 타겟팅 쿠폰 발급
- 실시간 쿠폰 사용 현황 모니터링

### 기술적 목적
- 대용량 트래픽 처리 가능한 확장성 있는 시스템
- 동시성 제어를 통한 쿠폰 중복 발급 방지
- 이벤트 기반 아키텍처를 통한 느슨한 결합

## 핵심 기능

### 쿠폰 정책 관리
- 다양한 할인 타입 지원 (정액, 정률)
- 발급 조건 설정 (기간, 수량, 대상)
- 쿠폰 코드 자동 생성

### 쿠폰 발급
- CODE 타입: 사용자가 쿠폰 코드 입력
- DIRECT 타입: 관리자가 직접 발급
- 중복 발급 방지 메커니즘

### 쿠폰 사용
- 결제 시스템과의 연동
- 예약 및 사용 처리
- 롤백 메커니즘

### 통계 및 모니터링
- 실시간 발급/사용 통계
- 쿠폰별 성과 분석
- 사용자별 쿠폰 현황

## 기술 스택

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.2.5
- **Build Tool**: Gradle 8.7

### Database
- **Primary**: PostgreSQL 15
- **Cache**: Redis 7
- **Message Queue**: Apache Kafka

### Architecture
- **Design Pattern**: Hexagonal Architecture
- **API Style**: RESTful API
- **Messaging**: Event-Driven Architecture

### Infrastructure
- **Container**: Docker
- **Orchestration**: Docker Compose
- **CI/CD**: GitHub Actions

## 시스템 요구사항

### 성능 요구사항
- 초당 1,000건 이상의 쿠폰 발급 처리
- API 응답 시간 200ms 이내
- 99.9% 가용성

### 확장성 요구사항
- 수평적 확장 가능한 구조
- 무중단 배포 지원
- 멀티 인스턴스 동시 운영

### 보안 요구사항
- 쿠폰 코드 암호화
- API 인증 및 권한 관리
- 감사 로그 기록

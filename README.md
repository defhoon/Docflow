# DocFlow

Document Review Workflow Management System

문서 등록 → 검토 요청 → 피드백 → 수정 → 재검토 → 승인 과정을 관리하는 시스템이다. 요구사항과 MVP 범위는 [`spec.md`](./spec.md)를 따른다.

> **현재 단계 (6주차):** 프로젝트 초기화와 개발 환경 구성만 완료되었다. 비즈니스 기능(문서·검토·피드백 등)과 인증은 아직 구현되지 않았다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | Next.js 16.4, React 19.3, TypeScript 5.9, Tailwind CSS 4, Vitest |
| Backend | Java 21, Spring Boot 4.1.1, Spring Data JPA, Flyway, Gradle 9.8 (Groovy DSL) |
| Database | PostgreSQL 16 |

자세한 버전과 선택 이유는 [`docs/adr/0001-technology-stack.md`](./docs/adr/0001-technology-stack.md)를 참고한다.

## 사전 준비

- JDK 21
- Node.js 22, npm
- Docker (Docker Compose v2) — 로컬 PostgreSQL 실행용

## 로컬 실행

### 1. 환경변수 설정

```bash
cp .env.example .env
# .env의 POSTGRES_PASSWORD, DB_PASSWORD를 같은 값으로 변경
```

| 변수 | 사용처 | 설명 |
| --- | --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | docker-compose | PostgreSQL 컨테이너 설정 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Backend | JDBC 연결 정보 (`DB_PASSWORD`는 필수, 기본값 없음) |
| `BACKEND_URL` | Frontend | Next.js rewrite 대상 (기본 `http://localhost:8080`) |

`.env`는 Git에 포함하지 않는다.

### 2. PostgreSQL 실행

```bash
docker compose up -d
docker compose ps        # STATUS가 healthy인지 확인
```

### 3. Backend 실행

```bash
cd backend
./gradlew bootRun        # 루트 .env를 자동으로 읽는다
curl http://localhost:8080/actuator/health   # {"status":"UP", ...}
```

### 4. Frontend 실행

```bash
cd frontend
npm install
npm run dev              # http://localhost:3000
```

브라우저에서 `http://localhost:3000`을 열면 Backend·Database 연결 상태(UP / DOWN / 연결 오류)가 표시된다.

## 테스트·검증 명령어

```bash
# Backend — PostgreSQL이 실행 중이어야 한다 (docker compose up -d)
cd backend && ./gradlew build          # 컴파일 + 테스트

# Frontend
cd frontend && npm run lint && npm run test && npm run build
```

GitHub Actions(`.github/workflows/ci.yml`)가 push·PR마다 같은 검증을 수행한다.

## 문서

- [`spec.md`](./spec.md): 프로젝트 명세 (Draft v0.2)
- [`CLAUDE.md`](./CLAUDE.md): Claude Code 개발 지침
- [`docs/architecture.md`](./docs/architecture.md): 아키텍처 개요
- [`docs/adr/`](./docs/adr): 아키텍처 결정 기록
- [`specs/`](./specs): 기능별 상세 SPEC (7주차부터 작성)

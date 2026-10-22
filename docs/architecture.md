# DocFlow 아키텍처 개요

> 기준: 6주차 프로젝트 초기화 (2026-10-14). 요구사항과 범위는 [`spec.md`](../spec.md) Draft v0.2를 따른다.
> 결정 근거는 [`docs/adr/`](./adr)를 참고한다.

## 1. 시스템 구성

```text
┌──────────────┐  /backend/health   ┌────────────────────┐   JDBC    ┌───────────────┐
│ Browser      │ ─────────────────▶ │ Next.js 16 (:3000) │ ────────▶ │ Spring Boot   │ ──▶ PostgreSQL 16
│              │                    │ App Router         │ rewrite   │ 4.1 (:8080)   │     (:5432)
└──────────────┘                    └────────────────────┘           └───────────────┘
```

- **Frontend**: Next.js 16 + React 19 + TypeScript + Tailwind CSS 4
- **Backend**: Java 25 + Spring Boot 4.1 (Spring MVC, Spring Data JPA, Flyway, Actuator)
- **Database**: PostgreSQL 16 (로컬은 Docker Compose)
- 추가 인프라(Redis, Kafka, Message Queue, Microservices)와 AI 서비스(Python/FastAPI)는 **포함하지 않는다.** AI는 Post-MVP다.

## 2. 저장소 구조

```text
docflow/
├── .github/workflows/ci.yml   # Backend build/test, Frontend lint/test/build
├── backend/                   # Spring Boot (Gradle Groovy DSL)
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/docflow/DocflowApplication.java
│       ├── main/resources/application.yml
│       ├── main/resources/db/migration/V1__baseline.sql
│       └── test/java/com/docflow/   # 연결·Flyway·Actuator 노출 테스트
├── frontend/                  # Next.js (npm)
│   └── src/
│       ├── app/                        # 라우팅·레이아웃
│       └── features/backend-health/    # 개발용 Backend 연결 상태 화면
├── docs/
│   ├── architecture.md
│   └── adr/0001~0004
├── specs/README.md            # 기능별 상세 SPEC 작성 안내 (SPEC은 다음 주차 작성)
├── docker-compose.yml         # PostgreSQL 16
├── .env.example               # 환경변수 예시 (.env는 Git 제외)
├── spec.md
└── CLAUDE.md
```

## 3. Backend 계층 규칙 (기능 구현 시 적용)

패키지는 Domain 중심, 도메인 내부는 계층으로 나눈다([ADR-0002](./adr/0002-package-structure.md)).

| 계층 | 책임 | 금지 사항 |
| --- | --- | --- |
| Controller | HTTP 요청·응답, 입력 형식 검증 | Repository 직접 호출, Entity 반환 |
| Service | 권한 검증 → 상태 전이 검증 → 비즈니스 로직, `@Transactional` 경계 | 다른 도메인 Repository 직접 접근(지양) |
| Domain | Entity, 상태 enum과 허용 전이 규칙 | — |
| Repository | Spring Data JPA 영속성 | 비즈니스 규칙 |
| DTO | Request/Response (Java record) | — |

상태 전이 규칙(`spec.md` §7)은 Backend에서만 강제한다. Frontend는 표시와 사용성 보조만 한다.

## 4. Frontend 구조 규칙

- `app/`: 라우팅과 레이아웃만 둔다.
- `features/<feature>/`: 기능별 API 호출(`api.ts`), 타입(`types.ts`), 컴포넌트(`components/`), 테스트를 함께 둔다.
- 화면은 최소한 Loading / Empty / Error / Success 상태를 구분한다(`spec.md` §11). 현재 연결 확인 화면은 Loading / Success(UP) / DOWN / Error를 구분한다.
- Backend 호출은 Next.js rewrites를 통해 동일 출처로 한다. 현재 등록된 경로는 `/backend/health`뿐이다.

## 5. 데이터와 스키마

- 스키마는 Flyway migration으로만 변경한다(`ddl-auto=validate`).
- 현재는 `V1__baseline.sql`(비즈니스 테이블 없음)만 있다. **도메인 Entity와 테이블은 아직 없으므로 JPA–스키마 일치 검증도 아직 이뤄지지 않았다.**
- Entity ID 전략은 BIGINT IDENTITY를 잠정 채택했다(Proposed, 첫 도메인 SPEC에서 확정).

## 6. 보안 관련 현재 상태

- Spring Security와 인증은 아직 없다. 인증 방향은 Session 기반이다(Proposed, ADR-0001 Q1).
- **현재 Backend는 인증 없이 열려 있으므로 개발 환경에서만 실행한다.**
- Actuator는 `health` 전체 상태만 노출하고 상세 정보는 숨긴다(ADR-0003 §4).
- 비밀정보는 `.env`(Git 제외)로만 주입한다.

## 7. 다음 단계에서 추가될 요소 (미생성)

| 시점 | 추가 요소 |
| --- | --- |
| 첫 도메인 SPEC (SPEC-01 Project 생성 예상) | `global/error`, `global/common`, `project/` 패키지, `V2` migration, `/api/:path*` rewrite |
| 인증 SPEC | Spring Security, Session/CSRF 설정, `user/` 패키지 |
| SPEC-03/04 Document·Version | 파일 저장소 인터페이스(로컬 파일시스템) |

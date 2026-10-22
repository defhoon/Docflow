# DocFlow 아키텍처 개요

> 기준: 6주차 프로젝트 초기화 (2026-10-14). 요구사항과 범위는 [`spec.md`](../spec.md) Draft v0.2를 따른다.
> 결정 근거는 [`docs/adr/`](./adr)를 참고한다.

## 1. 시스템 구성

```text
Browser ──(/api/*, /backend/health)──▶ Next.js 16 (:3000) ──(rewrite)──▶ Spring Boot 4.1 (:8080) ──(JDBC)──▶ PostgreSQL 16 (:5432)
        ◀── JSESSIONID, XSRF-TOKEN 쿠키는 localhost:3000에 저장 ──
```

- **Frontend**: Next.js 16 + React 19 + TypeScript + Tailwind CSS 4
- **Backend**: Java 25 + Spring Boot 4.1 (Spring MVC, Spring Data JPA, Flyway, Actuator)
- **Database**: PostgreSQL 16 (로컬은 Docker Compose)
- 추가 인프라(Redis, Kafka, Message Queue, Microservices)와 AI 서비스(Python/FastAPI)는 **포함하지 않는다.** AI는 Post-MVP다.

## 2. 저장소 구조

```text
docflow/
├── .github/
│   ├── workflows/ci.yml       # Backend build/test, Frontend lint/test/build
│   └── pull_request_template.md
├── backend/                   # Spring Boot (Gradle Groovy DSL)
│   └── src/main/
│       ├── java/com/docflow/
│       │   ├── global/        # config(Security, JPA Auditing), error(공통 오류 응답), security(401/403 JSON), common(BaseTimeEntity)
│       │   ├── user/          # domain(User), repository, service(UserAccountService)
│       │   └── auth/          # controller, service, security(AuthenticatedUser, UserDetailsService), dto, dev(local Seeder)
│       └── resources/
│           ├── application.yml
│           └── db/migration/  # V1__baseline, V2__create_users
├── frontend/                  # Next.js (npm)
│   └── src/
│       ├── app/               # /, /login, layout(AuthProvider)
│       ├── lib/api-client.ts  # 동일 출처 fetch, CSRF 헤더·재시도, 오류 파싱
│       └── features/
│           ├── auth/          # AuthProvider, api, LoginForm, RequireAuth, LogoutButton
│           └── backend-health/
├── docs/
│   ├── architecture.md
│   └── adr/0001~0005
├── specs/                     # README.md, 00-auth.md (SPEC-AUTH)
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
- Backend 호출은 Next.js rewrites를 통해 동일 출처로 한다. 등록된 경로는 `/api/:path*`(API)와 `/backend/health`(개발용 연결 확인)다.
- API 호출은 `lib/api-client.ts`를 사용한다. 변경 요청에 CSRF 헤더를 붙이고, `403 CSRF_INVALID`일 때만 토큰 갱신 후 1회 재시도한다.
- 인증 상태는 `AuthProvider`가 `GET /api/auth/me` 응답으로만 판단하며, 브라우저 저장소에 사용자 정보나 토큰을 저장하지 않는다.

## 5. 데이터와 스키마

- 스키마는 Flyway migration으로만 변경한다(`ddl-auto=validate`).
- `V1__baseline.sql`(기준점), `V2__create_users.sql`(SPEC-AUTH `users`)이 있다. `User` Entity가 추가되어 `ddl-auto=validate`가 실제 스키마를 검증한다.
- Entity ID는 `BIGINT GENERATED ALWAYS AS IDENTITY`로 확정했다(ADR-0005).

## 6. 보안 관련 현재 상태

- Spring Security 서버 세션 인증을 사용한다(SPEC-AUTH, [ADR-0005](./adr/0005-session-authentication.md)). JWT는 사용하지 않는다.
- 인가 규칙: `/actuator/health`, `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/logout`만 인증 없이 허용하고, 나머지 `/api/**`는 로그인이 필요하며, 그 외 경로는 모두 거부한다.
- CSRF: `csrf.spa()` (쿠키 `XSRF-TOKEN` → 헤더 `X-XSRF-TOKEN`). 로그인 성공 시 세션 ID 변경과 CSRF 토큰 교체를 수행하고 SecurityContext를 세션에 명시적으로 저장한다.
- 세션: 서버 메모리, 미사용 30분 만료, 쿠키 `HttpOnly`·`SameSite=Lax`, `Secure`는 `SESSION_COOKIE_SECURE`로 제어.
- Actuator는 `health` 전체 상태만 노출하고 상세 정보는 숨긴다(ADR-0003 §4).
- 비밀정보는 `.env`(Git 제외)로만 주입한다. 개발 계정 비밀번호(`DEV_SEED_PASSWORD`)도 같다.
- MVP 한계: `/me` 이외의 보호 API는 요청마다 계정 활성 여부를 다시 확인하지 않는다. 로그인 실패 횟수 제한이 없다.

## 7. 다음 단계에서 추가될 요소 (미생성)

| 시점 | 추가 요소 |
| --- | --- |
| SPEC-01 Project 생성 | `project/` 패키지, `V3` 이후 migration, 생성자 FK(`users.id`) |
| SPEC-02 Project Member | 프로젝트 단위 권한 검증 |
| SPEC-03/04 Document·Version | 파일 저장소 인터페이스(로컬 파일시스템) |

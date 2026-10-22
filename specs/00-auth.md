# SPEC-AUTH: 세션 기반 사용자 인증

- 상태: **구현·검증 완료 — PR 검토 대기** (2026-10-22, CI 및 Windows 로컬 검증 완료, 결과는 §17)
- Issue: [#4](https://github.com/defhoon/Docflow/issues/4)
- 브랜치: `feat/4/auth`
- 관련 문서: [`spec.md`](../spec.md) §4.1, §11, §12, §16 / [ADR-0005](../docs/adr/0005-session-authentication.md) / [`CLAUDE.md`](../CLAUDE.md) §3
- 작성일: 2026-10-22

## 1. 배경 & 목적

### 해결하려는 문제

`spec.md` §4.1은 "사용자 인증"을 MVP In Scope로 정의한다. 이후 모든 기능 SPEC(SPEC-01~13)은 다음을 전제로 한다.

- "누가 요청했는가"를 서버가 신뢰할 수 있는 방식으로 판단한다.
- 그 사용자를 기준으로 프로젝트 구성원 여부와 권한을 검증한다(`spec.md` §11.4, §12).

현재 Backend에는 인증 수단이 없어, 어떤 요청이든 익명으로 처리된다.

### 사용자가 할 수 있게 되는 것

- 이메일과 비밀번호로 로그인하고, 로그아웃할 수 있다.
- 로그인한 상태에서 새로고침하거나 다시 접속해도 세션이 유효하면 로그인 상태가 유지된다.
- 로그인하지 않은 사용자는 보호된 화면과 API에 접근할 수 없다.

### 설계 원칙

- 인증된 사용자 정보는 **서버 세션(SecurityContext)에서만** 결정한다. 요청 본문, 쿼리, 헤더의 사용자 식별값은 신뢰하지 않는다.
- 세션 인증만 사용한다. JWT, OAuth2 Resource Server, Remember-me와 혼용하지 않는다.
- 테스트 통과를 위해 보안 기능(CSRF, 인증 필터 등)을 비활성화하지 않는다.

## 2. 범위

### In Scope

- 이메일·비밀번호 로그인 (`POST /api/auth/login`)
- 로그아웃 (`POST /api/auth/logout`)
- 현재 사용자 조회 (`GET /api/auth/me`)
- CSRF 토큰 발급·갱신 (`GET /api/auth/csrf`)
- Spring Security 세션 인증, CSRF 보호, 인가 규칙, 401/403 JSON 응답
- `users` 테이블(Flyway V2)과 비밀번호 해시 저장
- `local` 프로파일 전용 개발 계정 Seeder (비밀번호는 Git에 저장하지 않음)
- 공통 오류 응답 형식(`ErrorResponse`)과 오류 코드
- Frontend
  - `/api` rewrite, 공통 API 클라이언트(CSRF 헤더, 오류 파싱)
  - `/login` 화면, 인증 상태 처리, 로그아웃
  - 로그인 후 `/` 화면: 사용자 정보, 로그아웃 버튼, 기존 Backend 연결 상태

### Out of Scope

| 항목 | 비고 |
| --- | --- |
| 회원가입 | 계정은 개발 Seeder로 준비 |
| 비밀번호 변경·재설정, 이메일 인증 | |
| 소셜 로그인, JWT, Remember-me | 세션 인증만 사용 |
| 로그인 실패 횟수 제한, 계정 잠금, Rate limiting | MVP 제외 (§11 위험 R5) |
| 전역 Role(관리자 등) | 프로젝트 단위 역할은 SPEC-02 범위 |
| 로그인·로그아웃 AuditLog | SPEC-13에서 검토 |
| 세션 외부 저장소(Spring Session JDBC, Redis) | 서버 메모리 세션 사용 |
| 동시 로그인 수 제한 | |
| 프로젝트 생성 등 비즈니스 기능 | SPEC-01 이후 |

## 3. 확정된 결정

| 항목 | 결정 |
| --- | --- |
| 인증 방식 | Spring Security 서버 세션 (`spec.md` §16.1 잠정 결정을 확정) |
| 로그인 식별자 | 이메일 (앞뒤 공백 제거, 소문자 정규화) |
| 세션 저장소 | 서버 메모리 (Servlet 컨테이너 기본). 서버 재시작 시 모든 세션 소멸 |
| 세션 만료 | 마지막 요청 후 30분 미사용 시 만료. 절대 만료 시간 없음 |
| 전역 Role | 없음. 인증된 사용자는 동일한 권한(`authenticated`) |
| 계정 준비 | 회원가입 없음. `local` 프로파일 전용 Seeder. 비밀번호는 Git에 저장하지 않음 |
| Entity ID | `BIGINT GENERATED ALWAYS AS IDENTITY` (`spec.md` §16.1 잠정 결정을 확정) |
| 로그인 후 화면 | `/`에 사용자 정보, 로그아웃, Backend 연결 상태 표시 |
| 로그인 실패 제한 | MVP 제외 |
| 인증 AuditLog | SPEC-13에서 검토 |
| 미인증 로그아웃 | 세션이 없어도 `204`. CSRF 검증은 유지. 세션이 있으면 무효화 (§12 Q1) |
| 개발 계정 | `author@docflow.local`, `reviewer@docflow.local`. 비밀번호는 `DEV_SEED_PASSWORD`로 주입 (§12 Q2) |
| `local` 프로파일 활성화 | 기본: `.\gradlew.bat bootRun --args="--spring.profiles.active=local"`, 대안: `SPRING_PROFILES_ACTIVE` 환경변수 (§12 Q3) |

## 4. 정상 흐름

### 4.1 로그인

1. Frontend가 앱 시작 시(또는 로그인 화면 진입 시) `GET /api/auth/csrf`를 호출한다. 응답과 함께 CSRF 토큰 쿠키 `XSRF-TOKEN`이 발급된다.
2. 사용자가 이메일과 비밀번호를 입력해 제출한다.
3. Frontend가 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 담아 `POST /api/auth/login`을 호출한다.
4. Backend가 CSRF 토큰을 검증한다.
5. Backend가 입력 형식을 검증한다.
6. `AuthenticationManager`(`DaoAuthenticationProvider` + `UserDetailsService` + `PasswordEncoder`)로 자격 증명을 확인한다.
7. 인증에 성공하면 다음을 **명시적으로** 수행한다.
   1. 세션 고정 방어: 세션 ID를 변경한다(`ChangeSessionIdAuthenticationStrategy`).
   2. CSRF 토큰을 폐기하고 새 토큰을 준비한다(`CsrfAuthenticationStrategy`). 기존 `XSRF-TOKEN` 쿠키는 삭제된다.
   3. 새 `SecurityContext`에 인증 정보를 담아 `SecurityContextHolderStrategy`에 설정한다.
   4. `SecurityContextRepository`(`HttpSessionSecurityContextRepository`)로 세션에 저장한다. Spring Security 6 이후 커스텀 로그인 처리에서는 자동 저장되지 않으므로 반드시 명시적으로 저장한다.
8. `200`과 사용자 정보를 응답한다. 새 세션 쿠키가 발급된다.
9. Frontend는 `GET /api/auth/csrf`를 다시 호출해 새 CSRF 토큰을 받는다.
10. Frontend는 `next` 파라미터(내부 경로만 허용) 또는 `/`로 이동한다.

### 4.2 현재 사용자 조회

1. Frontend가 앱 시작 시와 보호 화면 진입 시 `GET /api/auth/me`를 호출한다.
2. Backend가 세션의 `SecurityContext`에서 `AuthenticatedUser`를 꺼낸다.
3. DB에서 사용자를 다시 조회해 최신 정보를 응답한다(§5.4).
4. 세션이 없거나 만료되었으면 `401 AUTH_REQUIRED`를 응답한다.

### 4.3 로그아웃

1. Frontend가 CSRF 헤더와 함께 `POST /api/auth/logout`을 호출한다.
2. Spring Security의 Logout 처리가 다음을 수행한다.
   - 세션 무효화
   - SecurityContext 제거
   - CSRF 토큰 제거
   - 세션 쿠키 삭제
3. `204`를 응답한다.
4. Frontend는 인증 상태를 `anonymous`로 바꾸고, `GET /api/auth/csrf`로 새 토큰을 받은 뒤 `/login`으로 이동한다.

## 5. API 계약

### 5.1 공통

- Base path: `/api/auth`
- 요청·응답 형식: `application/json; charset=UTF-8`
- 오류 응답은 아래 형식을 따른다. 스택 트레이스, 예외 클래스명, SQL, 내부 경로는 포함하지 않는다.

```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 올바르지 않습니다."
}
```

- 입력 검증 오류(`400`)일 때만 `fieldErrors`를 포함한다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [{ "field": "email", "message": "이메일 형식이 올바르지 않습니다." }]
}
```

`fieldErrors`에는 거부된 입력값(`rejectedValue`)을 넣지 않는다. 비밀번호가 응답에 반사되지 않게 하기 위함이다.

### 5.2 엔드포인트

| Method / Path | 인증 | CSRF | 요청 | 성공 응답 | 실패 응답 |
| --- | --- | --- | --- | --- | --- |
| `GET /api/auth/csrf` | 불필요 | 불필요 | — | `204`, `XSRF-TOKEN` 쿠키 발급·갱신 | — |
| `POST /api/auth/login` | 불필요 | **필요** | `LoginRequest` | `200 AuthUserResponse`, 새 세션 쿠키 | `400 VALIDATION_FAILED`, `401 AUTH_INVALID_CREDENTIALS`, `403 CSRF_INVALID` |
| `POST /api/auth/logout` | 불필요 (세션이 있으면 무효화) | **필요** | — | `204`, 세션·CSRF 쿠키 삭제. 세션이 없어도 `204` | `403 CSRF_INVALID` |
| `GET /api/auth/me` | 필요 | 불필요 | — | `200 AuthUserResponse` | `401 AUTH_REQUIRED` |

### 5.3 요청·응답 본문

```text
LoginRequest (record)
  email     string  필수, 이메일 형식, 최대 255자
  password  string  필수, 공백만으로 구성 불가, 최대 128자
  * toString()은 password를 마스킹한다 (로그 노출 방지)

AuthUserResponse (record)
  id           number   users.id
  email        string
  displayName  string
```

- 응답에는 `passwordHash`, `enabled`, `createdAt` 등 내부 필드를 넣지 않는다.
- Entity(`User`)를 직접 반환하지 않는다.

### 5.4 세부 규칙

- **이메일 정규화:** 로그인과 계정 생성 모두 `trim()` 후 `toLowerCase(Locale.ROOT)`로 정규화해 저장·조회한다.
- **계정 존재 여부 숨김:** "존재하지 않는 이메일", "비밀번호 불일치", "비활성 계정(`enabled=false`)"은 모두 같은 상태 코드(`401`), 같은 `code`, 같은 `message`로 응답한다.
  - 존재하지 않는 이메일에 대해서도 `DaoAuthenticationProvider`의 타이밍 공격 완화 동작(더미 해시 비교)을 유지한다.
- **로그인 중 재로그인:** 다른 계정으로 다시 로그인하면 세션 ID가 바뀌고 새 사용자로 교체된다.
- **`/me` 최신성:** DB에서 사용자를 다시 조회한다. 사용자가 없거나 `enabled=false`이면 `401 AUTH_REQUIRED`로 응답하고 세션을 무효화한다.
- **MVP 한계 — 다른 보호 API의 비활성 계정 재검증 없음:** `/me` 이외의 보호 API는 세션의 인증 정보만 확인하고, 요청마다 DB에서 계정 활성 여부를 다시 확인하지 않는다. 로그인 중 비활성화된 계정은 `/me`를 호출하거나 세션이 만료될 때까지 다른 보호 API를 호출할 수 있다(§16 R6).
- **`GET /api/auth/csrf`:** 요청에서 `CsrfToken`을 실제로 읽어 지연 로딩된 토큰을 쿠키로 기록하게 한다. 응답 본문은 없다.

## 6. 보안 정책

### 6.1 Spring Security 구성

| 항목 | 설정 |
| --- | --- |
| 기준 버전 | Spring Boot 4.1.1이 관리하는 **Spring Security 7.1.1**의 실제 API (예: `DaoAuthenticationProvider(UserDetailsService)` 생성자, `csrf.spa()`) |
| 인증 방식 | 커스텀 JSON 로그인 Controller + `AuthenticationManager` |
| 비활성화 | `formLogin`, `httpBasic`, 기본 로그인 페이지, Spring 기본 생성 사용자 |
| 비밀번호 해시 | `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (기본 bcrypt, `{bcrypt}` 접두어) |
| 세션 생성 정책 | 필요할 때 생성(`IF_REQUIRED`, 기본값) |
| 세션 고정 방어 | 로그인 성공 시 세션 ID 변경 (§4.1-7) |
| 401 응답 | 커스텀 `AuthenticationEntryPoint`: 리다이렉트 없이 `401 AUTH_REQUIRED` JSON |
| 403 응답 | 커스텀 `AccessDeniedHandler`: CSRF 예외(`CsrfException`)는 `403 CSRF_INVALID`, 그 외는 `403 ACCESS_DENIED` |
| 로그아웃 | Spring Security `LogoutConfigurer`, URL `/api/auth/logout`(POST), 성공 시 `204` (`HttpStatusReturningLogoutSuccessHandler`) |

### 6.2 인가 규칙 (위에서부터 우선 적용)

| 경로 | 규칙 |
| --- | --- |
| `GET /actuator/health`, `GET /actuator/health/**` | `permitAll` |
| `GET /api/auth/csrf` | `permitAll` |
| `POST /api/auth/login` | `permitAll` (CSRF 검증은 적용) |
| `POST /api/auth/logout` | `permitAll` (CSRF 검증은 적용. Logout 필터가 인가 전에 처리) |
| `/api/**` | `authenticated` |
| 그 외 모든 경로 (`/actuator/**` 나머지 포함) | `denyAll` |

- `denyAll` 경로의 응답: 익명 사용자는 `401`, 인증된 사용자는 `403`.
- `/actuator/health`는 지금처럼 전체 상태만 응답한다(`spec.md` §11.6). Actuator 노출 설정(`exposure.include=health`)은 변경하지 않는다.

### 6.3 세션 쿠키

| 속성 | 값 | 설정 위치 |
| --- | --- | --- |
| 이름 | `JSESSIONID` (기본값) | — |
| `HttpOnly` | `true` | `server.servlet.session.cookie.http-only` |
| `SameSite` | `Lax` | `server.servlet.session.cookie.same-site` |
| `Secure` | 환경변수 `SESSION_COOKIE_SECURE` (기본 `false`, 운영 `true`) | `server.servlet.session.cookie.secure` |
| `Path` | `/` | 기본값 |
| 만료 | 브라우저 세션 쿠키(Max-Age 없음). 서버 세션은 30분 미사용 시 만료 | `server.servlet.session.timeout=30m` |
| 추적 방식 | 쿠키만 사용 (URL `;jsessionid` 금지) | `server.servlet.session.tracking-modes=cookie` |

- `Secure` 기본값을 `false`로 두는 이유: 로컬 개발은 `http://localhost`라 `true`면 브라우저가 쿠키를 저장하지 않는다. 운영 배포 시 `true` 설정을 배포 체크리스트에 포함한다.

### 6.4 CSRF

Spring Security 7의 `http.csrf(csrf -> csrf.spa())`를 사용한다. Spring Boot 4.1.1이 관리하는 Spring Security 7.1.1 소스로 2026-10-22에 동작을 확인하였다.

| 항목 | 내용 |
| --- | --- |
| 토큰 저장소 | `CookieCsrfTokenRepository.withHttpOnlyFalse()`. 쿠키 `XSRF-TOKEN`은 JS가 읽을 수 있음 |
| 요청 헤더 | `X-XSRF-TOKEN` |
| 토큰 해석 | 헤더로 보낸 값은 쿠키 원본 값 그대로 비교(`SpaCsrfTokenRequestHandler`) |
| 검증 대상 | `GET`, `HEAD`, `TRACE`, `OPTIONS`를 제외한 모든 요청. **login, logout 포함** |
| 토큰 발급 | 토큰은 지연 로딩되므로, 쿠키는 토큰을 실제로 읽는 요청에서만 기록됨 → `GET /api/auth/csrf`가 발급을 담당 |
| 로그인 후 | `CsrfAuthenticationStrategy`가 기존 토큰을 삭제(쿠키 Max-Age=0) → Frontend가 `GET /api/auth/csrf` 재호출해 새 토큰을 받음 |
| 토큰 검증 방식 | 쿠키 저장소는 서버에 토큰을 보관하지 않고 "쿠키 값 = 헤더 값"을 비교(double-submit)한다. 따라서 "토큰 교체"는 브라우저의 기존 쿠키를 삭제하고 새 값을 발급하는 것을 의미하며, 서버가 이전 값을 별도로 블랙리스트 처리하지는 않는다 |
| 로그아웃 후 | `CsrfLogoutHandler`가 토큰 삭제 → Frontend가 `GET /api/auth/csrf` 재호출 |
| CSRF 쿠키 속성 | `Path=/`, `SameSite=Lax`, `Secure`는 세션 쿠키와 같은 설정을 따름 |
| CORS | 설정하지 않음 (Next.js rewrite로 동일 출처) |

### 6.5 민감 정보 보호

- 비밀번호 원문은 저장·로그·응답 어디에도 남기지 않는다. `LoginRequest.toString()`은 비밀번호를 마스킹한다.
- `AuthenticatedUser`는 로그인 완료 후 비밀번호(해시 포함)를 보관하지 않는다(`eraseCredentials`). 세션에는 `id`, `email`, `displayName`만 저장된다.
- 로그에 비밀번호, 비밀번호 해시, 세션 ID, CSRF 토큰, 요청 본문 전체를 기록하지 않는다.
- 로그인 실패 로그에는 실패 사실만 남긴다. 이메일은 기록하지 않거나 마스킹한다.
- 오류 응답에 스택 트레이스와 예외 메시지를 포함하지 않는다(`server.error.include-stacktrace=never`, `include-message=never` 유지).
- `fieldErrors`에 거부된 입력값을 넣지 않는다.
- Seeder 비밀번호는 환경변수로만 받으며 로그에 출력하지 않는다.

## 7. 데이터 모델

### 7.1 `V2__create_users.sql`

```sql
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_users_email ON users (lower(email));
```

| 결정 | 이유 |
| --- | --- |
| 테이블명 `users` | `user`는 PostgreSQL 예약어 |
| `BIGINT IDENTITY` | `spec.md` §16.1 잠정 결정을 확정 |
| `lower(email)` 유니크 인덱스 | 애플리케이션 정규화(§5.4)와 함께 대소문자 중복 계정을 DB 수준에서도 차단 |
| Role 컬럼 없음 | 전역 Role 없음. 프로젝트 역할은 SPEC-02 |
| `enabled` | 계정 비활성화 대비. 관리 화면은 범위 밖 |
| 삭제 관련 컬럼 없음 | 삭제 기능 MVP 포함 여부 미결정(`spec.md` §16.2) |

### 7.2 Entity와 스키마 검증

- `User` Entity는 `ddl-auto=validate`로 V2 스키마와 비교된다. 이 프로젝트에서 처음으로 실제 도메인 스키마 검증이 이루어진다.
- `created_at`, `updated_at`은 `global/common/BaseTimeEntity`와 JPA Auditing으로 관리한다.

## 8. 개발 계정 Seeder

- 클래스: `com.docflow.auth.dev.DevUserSeeder` (`ApplicationRunner`)
- **`@Profile("local")`에서만 Bean으로 등록**된다. 기본 프로파일, 테스트, CI에서는 존재하지 않는다.
- 비밀번호는 환경변수 `DEV_SEED_PASSWORD`에서만 읽는다. `.env.example`에는 키 이름과 안내만 두고 값은 비워 둔다.
- `DEV_SEED_PASSWORD`가 비어 있으면 계정을 만들지 않고 경고 로그만 남긴다. 애플리케이션 기동은 계속한다.
- 이미 같은 이메일의 계정이 있으면 건너뛴다. 기존 계정의 비밀번호는 바꾸지 않는다.
- 생성 계정 (§12 Q2):

  | 이메일 | 표시 이름 |
  | --- | --- |
  | `author@docflow.local` | Author |
  | `reviewer@docflow.local` | Reviewer |

  두 계정의 구분은 테스트 시나리오용이며 전역 Role이 아니다. 권한 차이는 없다.
- 비밀번호와 비밀번호 해시는 로그에 출력하지 않는다. 로그에는 생성·건너뜀 여부와 이메일만 남긴다.
- `local` 프로파일 활성화 (§12 Q3):
  - 기본: `.\gradlew.bat bootRun --args="--spring.profiles.active=local"` (macOS/Linux: `./gradlew bootRun --args='--spring.profiles.active=local'`)
  - 대안: PowerShell `$env:SPRING_PROFILES_ACTIVE = "local"` 후 `.\gradlew.bat bootRun`

## 9. Frontend

### 9.1 구조

```text
frontend/src
├── lib/api-client.ts                 # same-origin fetch, CSRF 헤더, ErrorResponse 파싱
├── features/auth/
│   ├── api.ts                        # fetchCsrf, login, logout, fetchCurrentUser
│   ├── types.ts                      # AuthUser, AuthStatus
│   ├── redirect.ts                   # next 파라미터 검증 (open redirect 방지)
│   ├── AuthProvider.tsx              # 인증 상태 (loading / authenticated / anonymous / error)
│   └── components/LoginForm.tsx, LogoutButton.tsx, RequireAuth.tsx, CurrentUserPanel.tsx (+ *.test.tsx)
├── app/login/page.tsx
└── app/page.tsx                      # 로그인 후 홈: 사용자 정보, 로그아웃, BackendHealthStatus
```

- `next.config.ts`에 `/api/:path*` → `${BACKEND_URL}/api/:path*` rewrite를 추가한다. 기존 `/backend/health` rewrite는 유지한다.

### 9.2 인증 상태 처리

- 인증 상태는 서버 응답(`GET /api/auth/me`)으로만 판단한다. localStorage·sessionStorage에 사용자 정보나 토큰을 저장하지 않는다.
- `RequireAuth`의 상태별 처리:
  - `loading`: 로딩 표시
  - `anonymous`: `/login?next={현재 경로}`로 이동 (세션 만료로 판단되면 `&reason=expired` 추가)
  - `authenticated`: 화면 렌더링
  - `error`: `/me` 확인 중 네트워크·서버 오류. 오류 문구와 "다시 시도" 버튼 표시 (자동 재시도 없음)
- 로그인 상태에서 브라우저 창이 다시 활성화(`focus`)되면 `/me`로 세션 유효 여부를 다시 확인한다. 장시간 미사용 후 세션 만료를 감지하기 위함이다.
- `/login`에 이미 로그인한 사용자가 접근하면 `/`로 이동한다.
- `next` 파라미터는 `/`로 시작하고 `//`로 시작하지 않는 내부 경로만 허용한다. 그 외 값은 `/`로 대체한다(open redirect 방지).
- 보호 API가 `401`을 반환하면 상태를 `anonymous`로 바꾸고 "세션이 만료되었습니다. 다시 로그인해 주세요." 안내와 함께 로그인 화면으로 이동한다.
- Next.js 16 `proxy.ts`(구 middleware)는 이번 범위에서 사용하지 않는다. 화면 보호는 사용성 목적이며, 실제 보안은 Backend가 담당한다.

### 9.3 로그인 화면 상태

| 상태 | 표시 |
| --- | --- |
| Loading (제출 중) | 버튼 비활성화, 진행 표시 |
| Validation Error | 필드별 오류 (클라이언트 검증 + `400` `fieldErrors`) |
| 인증 실패 (`401`) | "이메일 또는 비밀번호가 올바르지 않습니다." (계정 존재 여부를 드러내지 않음) |
| CSRF 실패 (재시도 후에도) | "요청을 처리할 수 없습니다. 페이지를 새로고침해 주세요." |
| Network / Server Error | "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." |
| Success | `next` 또는 `/`로 이동 |

### 9.4 CSRF 오류 복구와 재시도 제한

- 자동 재시도는 **서버가 `403`과 `code=CSRF_INVALID`로 명확히 거부한 요청에만** 적용한다.
  1. `GET /api/auth/csrf`로 토큰을 갱신한다.
  2. 원래 요청을 **최대 1회** 재시도한다.
- 재시도해도 `CSRF_INVALID`이면 더 이상 재시도하지 않고 오류를 표시한다.
- 다음은 **자동 재시도하지 않는다.**
  - `CSRF_INVALID`가 아닌 `403` (`ACCESS_DENIED` 등)
  - 네트워크 오류 (요청이 서버에 도달했는지 알 수 없어 중복 실행 위험이 있음)
  - `5xx` 응답
  - 그 밖의 모든 `4xx`
- 재시도가 안전한 이유: CSRF 검증은 Controller 실행 전 필터에서 거부되므로, 거부된 요청은 서버 상태를 바꾸지 않는다.
- 동시에 여러 요청이 CSRF 오류를 받으면 토큰 갱신 요청은 하나만 보내고 결과를 공유한다.

## 10. 예시와 실패 시나리오

| 상황 | Backend 응답 | Frontend |
| --- | --- | --- |
| 정상 이메일·비밀번호 + CSRF | `200`, 새 세션 쿠키 | `/` 또는 `next`로 이동 |
| 이메일 대소문자·앞뒤 공백 차이 (`" Author@X.com "`) | 정규화 후 정상 처리 | — |
| 이메일 형식 오류, 빈 비밀번호 | `400 VALIDATION_FAILED` | 필드 오류 |
| 존재하지 않는 이메일 | `401 AUTH_INVALID_CREDENTIALS` | 공통 실패 문구 |
| 비밀번호 불일치 | `401 AUTH_INVALID_CREDENTIALS` | 공통 실패 문구 |
| 비활성 계정 | `401 AUTH_INVALID_CREDENTIALS` | 공통 실패 문구 |
| CSRF 헤더 없음·불일치 | `403 CSRF_INVALID` | 토큰 갱신 후 1회 재시도 → 실패 시 오류 |
| 세션 없이 `/api/auth/me` 또는 보호 API | `401 AUTH_REQUIRED` (리다이렉트 아님) | `/login?next=...` |
| 30분 미사용 후 요청 | `401 AUTH_REQUIRED` | 세션 만료 안내 후 로그인 화면 |
| 로그아웃 후 이전 세션 쿠키로 요청 | `401 AUTH_REQUIRED` | 로그인 화면 |
| 세션 없이 로그아웃 (CSRF 정상) | `204` | 로그인 화면 |
| 세션 없이 로그아웃 (CSRF 없음) | `403 CSRF_INVALID` | 토큰 갱신 후 1회 재시도 |
| 로그인 중 계정 비활성화 | 보호 API는 세션 만료까지 허용, `/me`는 `401` (§5.4) | 로그인 화면 |
| 서버 재시작 | 메모리 세션 소멸 → `401` | 로그인 화면 |
| 익명 사용자가 `/actuator/env` | `401` (SecurityFilterChain) | — |
| 인증 사용자가 `/actuator/env` | `403` (`denyAll`) | — |
| 권한 없는 리소스 (SPEC-01 이후) | `403 ACCESS_DENIED` | 권한 오류 표시 |
| Backend 중단·DB 장애 | 연결 실패 또는 `5xx` | Network/Server Error 상태 |

## 11. Acceptance Criteria

### Backend

- **AC-AUTH-01** 등록된 활성 사용자가 올바른 이메일·비밀번호와 유효한 CSRF 토큰으로 로그인하면 `200`과 `id`, `email`, `displayName`을 받고, 이후 같은 세션으로 `GET /api/auth/me`가 `200`을 반환한다.
- **AC-AUTH-02** 로그인 성공 전후의 세션 ID가 다르다(세션 고정 방어).
- **AC-AUTH-03** 존재하지 않는 이메일, 잘못된 비밀번호, 비활성 계정의 로그인은 모두 같은 `401` 응답 본문을 반환하고 세션에 인증 정보가 저장되지 않는다.
- **AC-AUTH-04** 이메일 형식 오류, 빈 비밀번호, 길이 초과는 `400 VALIDATION_FAILED`를 반환하며, 응답에 입력한 비밀번호가 포함되지 않는다.
- **AC-AUTH-05** CSRF 토큰 없이 또는 잘못된 토큰으로 `POST /api/auth/login`, `POST /api/auth/logout`을 호출하면 `403 CSRF_INVALID`를 반환한다.
- **AC-AUTH-06** `GET /api/auth/csrf`는 인증 없이 `XSRF-TOKEN` 쿠키를 발급한다. 로그인·로그아웃 응답은 `XSRF-TOKEN` 쿠키를 삭제(`Max-Age=0`)하며, 이후 `GET /api/auth/csrf`가 이전과 다른 새 토큰을 발급한다.
- **AC-AUTH-07** 세션 없이 `GET /api/auth/me` 또는 인증이 필요한 `/api/**`를 호출하면 리다이렉트가 아닌 `401 AUTH_REQUIRED` JSON을 반환한다.
- **AC-AUTH-08** 로그아웃 후 같은 세션 쿠키로 `GET /api/auth/me`를 호출하면 `401`을 반환한다. 세션 없이 유효한 CSRF 토큰으로 로그아웃하면 `204`를 반환한다.
- **AC-AUTH-09** 무효화되었거나 만료된 세션으로 요청하면 `401`을 반환한다.
- **AC-AUTH-10** 어떤 응답에도 `passwordHash` 또는 비밀번호 원문이 포함되지 않는다.
- **AC-AUTH-11** `users` 테이블이 Flyway V2로 생성되고, `ddl-auto=validate` 상태에서 애플리케이션이 기동한다. 대소문자만 다른 이메일은 DB에서 중복 저장이 거부된다.
- **AC-AUTH-12** `GET /actuator/health`는 인증 없이 `200`과 전체 상태만 반환한다.
- **AC-AUTH-13** Actuator 노출 범위는 `health`뿐이다(구성 검증). 익명 사용자의 `/actuator/env` 요청은 `401`, 인증 사용자의 요청은 `403`이다.
- **AC-AUTH-14** `DevUserSeeder`는 `local` 프로파일에서만 등록되며, 테스트(기본 프로파일)에서는 Bean이 존재하지 않는다.
- **AC-AUTH-15** 로그인 요청 처리 중 비밀번호 원문이 애플리케이션 로그에 출력되지 않는다.

- **AC-AUTH-23** `local` 프로파일에서 `DEV_SEED_PASSWORD`가 설정되면 Seeder가 `author@docflow.local`, `reviewer@docflow.local`을 생성한다. 다시 실행해도 중복 생성하지 않고 기존 비밀번호를 바꾸지 않으며, 비밀번호와 해시를 로그에 출력하지 않는다.
- **AC-AUTH-24** `/me` 호출 시 계정이 비활성화되어 있으면 `401`을 반환하고 세션을 무효화한다.

### Frontend

- **AC-AUTH-16** 로그인 화면은 제출 중, 입력 오류, 인증 실패, 네트워크·서버 오류, 성공 상태를 구분해 표시한다.
- **AC-AUTH-17** 변경 요청(`POST` 등)에는 `X-XSRF-TOKEN` 헤더가 포함되고, `403 CSRF_INVALID` 응답 시에만 토큰 갱신 후 최대 1회 재시도한다. 그 밖의 `403`, 네트워크 오류, `5xx`는 자동 재시도하지 않는다.
- **AC-AUTH-18** 미인증 사용자가 `/`에 접근하면 `/login?next=/`로 이동하고, 로그인 후 원래 경로로 돌아간다. 외부 URL `next`는 무시된다.
- **AC-AUTH-19** 로그인 후 `/`에 사용자 표시 이름·이메일, 로그아웃 버튼, Backend 연결 상태가 표시된다.
- **AC-AUTH-20** 로그아웃하면 `/login`으로 이동하고, 이후 `/` 접근 시 다시 로그인 화면으로 이동한다.
- **AC-AUTH-21** 브라우저 저장소(localStorage, sessionStorage)에 사용자 정보나 토큰을 저장하지 않는다.

### 통합 (Next.js rewrite)

- **AC-AUTH-22** 브라우저가 `http://localhost:3000`만 사용하는 상태에서 `JSESSIONID`와 `XSRF-TOKEN` 쿠키가 `localhost:3000`에 저장되고, 로그인 → 새로고침 → `/me` 유지 → 로그아웃이 동작한다.

## 12. 결정 사항 (2026-10-22 확정)

| ID | 질문 | 결정 |
| --- | --- | --- |
| Q1 | 세션 없이 `POST /api/auth/logout`을 호출하면? | `204`. 미인증 사용자도 접근 가능(`permitAll`), CSRF 검증 유지, 세션이 있으면 무효화 |
| Q2 | 개발 Seeder 계정 구성 | `author@docflow.local`, `reviewer@docflow.local`. 비밀번호는 `DEV_SEED_PASSWORD`. 전역 Role 없음 |
| Q3 | `local` 프로파일 활성화 | 기본 `--args="--spring.profiles.active=local"`, 대안 `SPRING_PROFILES_ACTIVE` |

현재 미결정 사항은 없다. 구현 중 범위 변경이나 중요한 설계 문제가 생기면 사용자에게 보고한 뒤 이 문서를 먼저 갱신한다.

## 13. 테스트 계획

### 13.1 Backend 단위 테스트

- 이메일 정규화 (공백, 대소문자)
- `LoginRequest.toString()` 비밀번호 마스킹
- `AuthenticatedUser` 생성 시 비밀번호 미보관
- `AccessDeniedHandler`의 CSRF·권한 오류 코드 구분

### 13.2 Backend 통합 테스트

`@SpringBootTest` + MockMvc + 실제 PostgreSQL을 사용한다. `spring-security-test`의 `csrf()`, `user()` 지원을 쓴다.

| 테스트 | AC |
| --- | --- |
| 로그인 성공 → `/me` 200 | 01 |
| 로그인 전후 세션 ID 변경 | 02 |
| 없는 이메일·비밀번호 불일치·비활성 계정 → 같은 401 본문, 세션 인증 정보 없음 | 03 |
| 입력 검증 400, 응답에 비밀번호 미포함 | 04, 10 |
| CSRF 없음·불일치 → 403 `CSRF_INVALID` (login, logout) | 05 |
| `/csrf` 쿠키 발급, 로그인·로그아웃 응답의 `XSRF-TOKEN` 삭제(`Max-Age=0`), 재발급 토큰이 이전과 다름 | 06 |
| 쿠키 → `X-XSRF-TOKEN` 헤더 흐름으로 실제 로그인 (테스트 지원 기능 `csrf()` 없이) | 05, 06 |
| 세션 없이 `/me`·보호 API → 401 JSON, 302 아님 | 07 |
| 로그아웃 후 `/me` 401, 세션 없이 로그아웃 204, CSRF 없는 로그아웃 403 | 08, 05 |
| 로그인 후 세션에 `SPRING_SECURITY_CONTEXT`가 저장됨 (SecurityContext 명시적 저장) | 01 |
| `/me` 비활성 계정 → 401, 세션 무효화 | 24 |
| 무효화된 세션 → 401 | 09 |
| Flyway V2 적용, 이메일 대소문자 중복 거부 | 11 |
| `/actuator/health` 익명 200 | 12 |
| Actuator 노출 구성 = `health`만 (노출 여부), `/actuator/env` 익명 401·인증 403 (Security 처리) | 13 |
| 기본 프로파일에 `DevUserSeeder` Bean 없음 | 14 |
| `local` 프로파일 Seeder: 2개 계정 생성, 재실행 시 중복·비밀번호 변경 없음, 로그에 비밀번호 없음 | 23 |
| 로그인 시 로그 출력에 비밀번호 원문 없음 (`OutputCaptureExtension`) | 15 |

**기존 테스트 조정 원칙**
- `ActuatorExposureTest`: 지금은 익명 요청이 `/actuator/env`에서 `404`라고 가정한다. Security 도입 후에는 **두 관점으로 나눈다.**
  1. **노출 여부:** Actuator 구성(노출된 Web 엔드포인트 목록)이 `health`만 포함하는지 확인한다. 사용할 Bean은 구현 시 확인한다.
  2. **Security 처리:** 익명 `401`, 인증 사용자 `403`을 확인한다.
- 보안 설정을 끄거나 `@WithMockUser`로 우회해 기존 기대값(`404`)을 맞추지 않는다.
- `DocflowApplicationTests`, `DatabaseConnectionTest`는 변경 없이 통과해야 한다.
- 테스트 계정은 각 테스트가 직접 생성·정리한다. Seeder에 의존하지 않는다.
- `spring-security-test`의 `csrf()`는 같은 테스트 컨텍스트에서 공유되는 `CsrfFilter`의 토큰 저장소를 테스트용(세션 기반) 저장소로 교체한다. 따라서 실제 `XSRF-TOKEN` 쿠키 흐름은 별도 클래스(`CsrfCookieFlowIntegrationTest`)에서 `csrf()` 없이 검증하고, 각 테스트 전에 애플리케이션의 실제 쿠키 저장소로 되돌린다. 보안 설정은 변경하지 않는다.

### 13.3 Frontend 테스트 (Vitest)

- `LoginForm`: 제출 중, 400, 401, 네트워크 오류, 성공 이동
- `api-client`: CSRF 헤더 부착, `ErrorResponse` 파싱, `CSRF_INVALID` 1회 재시도·두 번째 실패 시 중단, `ACCESS_DENIED`·네트워크 오류·`5xx` 재시도 안 함
- `RequireAuth`: loading / anonymous 이동 / authenticated
- `next` 파라미터 검증 (내부 경로만 허용)
- 기존 `BackendHealthStatus` 테스트 5건 유지

### 13.4 Next.js rewrite 경유 쿠키 검증 (AC-AUTH-22)

자동화 테스트(Vitest)는 브라우저 쿠키와 실제 프록시를 검증하지 못한다. Windows 로컬에서 **사용자가** 수동으로 검증한다.

1. `docker compose up -d`, `.\gradlew.bat bootRun`(local 프로파일), `npm run dev`
2. 브라우저 개발자 도구(Application → Cookies)에서 `http://localhost:3000`을 확인한다.
   - `/login` 진입 후 `XSRF-TOKEN` 쿠키 존재 (`HttpOnly` 아님, `Path=/`)
   - 로그인 후 `JSESSIONID` 쿠키 존재 (`HttpOnly`, `SameSite=Lax`), 로그인 전과 값이 다름
   - 로그인 후 `XSRF-TOKEN`이 새 값으로 갱신됨
3. Network 탭에서 `POST /api/auth/login`에 `X-XSRF-TOKEN` 헤더가 있는지 확인한다.
4. 새로고침 후에도 `/`에 사용자 정보가 유지되는지 확인한다.
5. 로그아웃 후 `JSESSIONID`가 삭제·무효화되고 `/`에서 `/login`으로 이동하는지 확인한다.
6. (선택) PowerShell `curl.exe`와 쿠키 파일로 프록시 경유 흐름을 확인한다: `csrf` → `login` → `me` → `logout` → `me`(401).
7. 세션 만료 확인: 검증 목적으로만 `server.servlet.session.timeout`을 짧게 지정해 실행한다(커밋하지 않음). 만료 후 요청 시 로그인 화면으로 이동하는지 확인한다.

### 13.5 검증 환경과 주체

| 검증 | 실행 주체 | 비고 |
| --- | --- | --- |
| Backend 단위·통합 테스트 (JDK 25) | GitHub Actions CI | 클라우드에는 JDK 25가 없어 실행 불가 |
| Frontend lint·test·build | Claude Code 클라우드, CI | |
| Backend 테스트, 수동 E2E (§13.4) | 사용자 (Windows 로컬) | 결과는 사용자 보고 기준으로 기록 |

## 14. 완료 기준

- AC-AUTH-01 ~ 24 충족
- 기존·신규 테스트가 보안 설정을 끄지 않은 상태에서 통과 (CI Backend·Frontend job success)
- Windows 로컬 수동 검증(§13.4) 통과 — 사용자 보고
- 문서 갱신: README(계정 준비, local 프로파일 실행 방법), `.env.example`(`DEV_SEED_PASSWORD`, `SESSION_COOKIE_SECURE` 키)
- 구현과 이 SPEC이 다르면 SPEC을 먼저 갱신

## 15. 구현 순서 (승인 후)

1. 의존성 추가(`spring-boot-starter-security`, 테스트용 Security Starter), `global/error`, `SecurityConfig`, `ActuatorExposureTest` 조정
2. V2 migration, `BaseTimeEntity`, `User`, `UserRepository`, `UserDetailsService`
3. `AuthController`·`AuthService`(csrf, login, me) + Logout 설정 + 통합 테스트
4. `DevUserSeeder`, `.env.example`, README
5. Frontend: rewrite, `api-client`, `features/auth`, `/login`, `/` → Vitest
6. CI 확인 → Windows 로컬 검증(사용자) → PR (`Closes #4`)

## 16. 위험 요소

| ID | 위험 | 대응 |
| --- | --- | --- |
| R1 | Spring Security 7 / Boot 4 API가 기존 자료와 다름 | `csrf.spa()`는 7.1.1 소스로 확인 완료. 나머지 API도 구현 시 소스·테스트로 확인 |
| R2 | Next.js rewrite가 `Set-Cookie`·CSRF 쿠키를 제대로 전달하지 않을 가능성 | §13.4 수동 검증. 문제 발생 시 대안(Route Handler 프록시 등)을 제안하고 승인 후 변경 |
| R3 | 로컬 http에서 `Secure` 쿠키 미저장 | `SESSION_COOKIE_SECURE` 환경변수로 분리, 운영 `true` |
| R4 | 메모리 세션: 재시작 시 전원 로그아웃 | MVP 수용, 문서 명시 |
| R5 | 무차별 로그인 시도 제한 없음 | MVP 제외, 후속 과제 |
| R6 | 비활성화된 계정의 기존 세션이 `/me` 이외의 보호 API에서 만료 전까지 유효 (**MVP 한계**) | `/me`에서 재확인하고 세션 무효화(§5.4). 보호 API 전체 재확인은 후속 과제 |
| R7 | 클라우드에 JDK 25가 없어 Backend 테스트를 직접 실행할 수 없음 | CI와 Windows 로컬로 검증하고 검증 주체를 구분해 보고 |
| R8 | Security 도입으로 기존 테스트 기대값이 바뀜 | §13.2 원칙대로 조정. 보안 설정을 끄지 않음 |

## 17. 구현 및 검증 결과 (2026-10-22)

검증 주체를 세 가지로 구분한다. 실제로 실행하지 않은 항목은 "미검증" 또는 "실행 불가"로 표시한다.

- **Claude Code 클라우드**: Claude Code가 클라우드 환경에서 직접 실행한 결과. JDK 25가 없어 Backend는 실행하지 못했다.
- **GitHub Actions CI**: Claude Code가 CI 실행 결과와 로그로 확인한 결과 (Temurin JDK 25, PostgreSQL 16 service).
- **Windows 로컬 (사용자)**: **사용자가 Windows 11에서 직접 실행하고 보고한 결과**. Claude Code는 Windows 환경에서 실행하지 않았다. 환경: Java 25.0.5.0.2, Spring Boot 4.1.1, PostgreSQL 16(Docker Compose), Next.js 16.4.0.

### 17.1 빌드·자동화 테스트

| 항목 | Claude Code 클라우드 | GitHub Actions CI | Windows 로컬 (사용자) |
| --- | --- | --- | --- |
| Backend build·test (JDK 25, 테스트 소스 기준 35건) | 실행 불가 (JDK 25 없음) | 통과 (run 39766901843, 커밋 `6e6f6fa` 외 이후 커밋) | 통과 (`.\gradlew.bat clean build`) |
| Frontend lint | 통과 | 통과 | 통과 |
| Frontend test (Vitest 6개 파일, 39건) | 통과 | 통과 | 통과 |
| Frontend build | 통과 | 통과 | 통과 |

- CI 성공 로그는 Backend 테스트 건수를 출력하지 않는다. "35건"은 테스트 소스의 `@Test` 수이며, 이 테스트들을 포함한 `:test` 태스크가 성공했다.

### 17.2 실행·통합 검증 (Windows 로컬, 사용자 실행·보고)

| 항목 | 결과 | 관련 AC |
| --- | --- | --- |
| `local` 프로파일로 Spring Boot 실행 | 통과 | — |
| Flyway V1·V2 적용 | 통과 | 11 |
| 개발 계정(Author·Reviewer) 생성 | 통과 | 23 |
| 서버 재시작 시 개발 계정 중복 생성 방지 | 통과 | 23 |
| 미인증 상태에서 `/` 접근 시 `/login?next=%2F`로 이동 | 통과 | 18 |
| Author 계정 로그인, 사용자 정보·Backend UP 표시 | 통과 | 01, 19 |
| 새로고침 후 로그인 상태 유지 | 통과 | 22 |
| 잘못된 비밀번호 입력 시 오류 메시지 | 통과 | 03, 16 |
| 로그아웃 후 로그인 화면 이동, 보호 페이지 접근 차단 | 통과 | 08, 20 |
| Reviewer 계정 로그인 | 통과 | 01, 23 |
| 로그인 요청의 `X-XSRF-TOKEN` 헤더 | 통과 | 17, 22 |
| `JSESSIONID`·`XSRF-TOKEN` 쿠키 속성 | 통과 | 06, 22 |
| 세션 만료 (검증용 1분 timeout, 설정은 커밋하지 않음) | 통과 | 09 |

- 클라우드에서는 위 항목을 실행하지 못했다(JDK 25·Docker 없음). CI에서는 동일 동작을 MockMvc 통합 테스트로 검증했으며(§13.2), 실제 브라우저와 Next.js rewrite를 거치는 동작은 Windows 로컬 검증으로만 확인되었다.
- 사용자 보고에 포함되지 않은 세부 항목(예: 로그인 후 `XSRF-TOKEN` 값이 실제 브라우저에서 바뀌었는지)은 자동화 테스트(`CsrfCookieFlowIntegrationTest`)로만 검증되었다.

### 17.3 구현 중 발견하여 수정한 사항

- `LoginRequest`: 앞뒤 공백이 있는 이메일이 `@Email` 검증에서 거부되어, 검증 전에 공백을 제거하도록 수정 (§5.4와 일치)
- 테스트 격리: `csrf()` 테스트 지원 기능이 공유 `CsrfFilter` 저장소를 교체하는 문제를 별도 테스트 클래스로 분리해 해결 (§13.2). 애플리케이션 동작과 보안 설정은 변경하지 않았다.
- AC-AUTH-06은 쿠키 기반 CSRF(double-submit) 동작에 맞게 설계 단계에서 수정되었다(§6.4).

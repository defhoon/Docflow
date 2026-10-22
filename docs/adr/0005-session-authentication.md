# ADR-0005: Spring Security 세션 기반 인증

- 상태: **Accepted** (설계 확정. 구현은 Issue #4에서 별도 승인 후 진행)
- 날짜: 2026-10-22
- 대체 대상: [ADR-0001](./0001-technology-stack.md)의 잠정 결정 Q1(Session 인증), Q9(Entity ID BIGINT IDENTITY)를 확정한다.
- 관련 문서: [`specs/00-auth.md`](../../specs/00-auth.md) (SPEC-AUTH), [`spec.md`](../../spec.md) §16.1, Issue [#4](https://github.com/defhoon/Docflow/issues/4)

## 배경

`spec.md` §4.1의 MVP "사용자 인증"을 구현해야 한다. 이후 모든 기능 SPEC은 "서버가 신뢰할 수 있는 현재 사용자"를 기준으로 권한을 검증한다.

6주차에는 Session 기반 인증을 잠정 결정(ADR-0001 Q1)만 하고 Spring Security를 도입하지 않았다. 브라우저는 Next.js(`localhost:3000`)만 호출하고, Next.js rewrite가 Backend(`localhost:8080`)로 프록시하는 동일 출처 구조다(ADR-0003).

## 검토한 대안

### 인증 방식

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| **서버 세션 + HttpOnly 쿠키 (채택)** | 서버가 세션을 무효화할 수 있음(로그아웃 즉시 반영). 토큰이 JS에 노출되지 않음. 동일 출처 웹 앱 하나에 적합 | CSRF 보호 필요. 서버 상태 보관 |
| JWT (Access/Refresh) | 서버 무상태, 다중 클라이언트에 유리 | 즉시 무효화가 어려움. 저장 위치(XSS·CSRF) 문제. 모바일은 Out of Scope라 장점이 작음 |
| 세션 + JWT 혼용 | — | 인증 경로가 둘이 되어 복잡도·취약점 증가. **채택하지 않음** |

### 로그인 처리 방식

| 대안 | 결정 |
| --- | --- |
| **JSON 로그인 Controller + `AuthenticationManager` (채택)** | JSON API 계약과 일치. 세션 ID 변경, CSRF 토큰 재발급, SecurityContext 저장을 명시적으로 수행 |
| `formLogin()` | 기본이 form-urlencoded와 리다이렉트. JSON 응답으로 바꾸려면 핸들러를 여러 개 교체해야 해서 제외 |

### 세션 저장소

| 대안 | 결정 |
| --- | --- |
| **서버 메모리 (Servlet 컨테이너 기본, 채택)** | 추가 의존성·테이블 없음. 단일 인스턴스 MVP에 충분. 재시작 시 전원 로그아웃(수용) |
| Spring Session JDBC (PostgreSQL) | 재시작 후에도 세션 유지. 의존성과 테이블 추가. 필요성 미확인 |
| Redis | `CLAUDE.md` §3-7에 따라 근거 없이 도입하지 않음 |

### CSRF

| 대안 | 결정 |
| --- | --- |
| **Spring Security 7 `csrf.spa()` (채택)** | 쿠키 `XSRF-TOKEN` → 헤더 `X-XSRF-TOKEN`. SPA 표준 방식이며 Spring Security 7.0부터 제공 |
| CSRF 비활성화 | 쿠키 세션 인증에서는 CSRF 공격에 노출되므로 **채택하지 않음** |
| 세션 저장 토큰 + 별도 발급 API 직접 구현 | `spa()`와 같은 기능을 직접 만들게 되어 제외 |

## 결정

- Spring Security 세션 인증만 사용한다. JWT, OAuth2 Resource Server, Remember-me는 사용하지 않는다.
- 로그인은 이메일·비밀번호. 비밀번호는 `DelegatingPasswordEncoder`(bcrypt)로 해시한다.
- 로그인 성공 시 세션 ID 변경, CSRF 토큰 재발급, `SecurityContextRepository`를 통한 SecurityContext 명시적 저장을 수행한다.
- 세션은 서버 메모리, 미사용 30분 만료. 세션 쿠키는 `HttpOnly`, `SameSite=Lax`, `Secure`는 환경변수로 제어한다(로컬 `false`, 운영 `true`).
- CSRF는 `csrf.spa()`를 쓰고, login·logout을 포함한 모든 변경 요청에 적용한다.
- 미인증은 `401`, 권한·CSRF 오류는 `403`을 JSON으로 응답하고 리다이렉트하지 않는다.
- 인가: `/actuator/health`·`GET /api/auth/csrf`·`POST /api/auth/login`만 `permitAll`, 나머지 `/api/**`는 `authenticated`, 그 외는 `denyAll`.
- 전역 Role은 두지 않는다. 프로젝트 단위 권한은 SPEC-02에서 설계한다.
- Entity ID는 `BIGINT GENERATED ALWAYS AS IDENTITY`로 확정한다.
- 개발 계정은 `local` 프로파일 전용 Seeder로 만들고, 비밀번호는 환경변수로만 받는다.

상세 API 계약, 쿠키·CSRF 흐름, 실패 시나리오, Acceptance Criteria는 [`specs/00-auth.md`](../../specs/00-auth.md)를 따른다.

## 이유

- 브라우저 웹 앱 하나가 동일 출처로 Backend를 호출하는 구조에서는 서버 세션이 가장 단순하고, 로그아웃·계정 비활성화를 서버에서 바로 반영할 수 있다.
- 인증 경로를 하나로 유지해야 이후 기능 SPEC에서 권한 검증 기준이 명확하다.
- MVP는 단일 Backend 인스턴스이므로 메모리 세션으로 충분하다. 다중 인스턴스가 필요해지면 Spring Session JDBC를 먼저 검토한다.

## 영향

- 의존성 추가: `spring-boot-starter-security`와 테스트용 Security Starter (구현 단계).
- Security 도입 후 기존 `ActuatorExposureTest`의 익명 `/actuator/env` 기대값이 `404`에서 `401`로 바뀐다. 테스트는 "Actuator 노출 구성"과 "SecurityFilterChain 처리"를 분리해 검증한다. 보안 설정을 끄지 않는다.
- Frontend에 `/api/:path*` rewrite와 CSRF 헤더를 처리하는 공통 API 클라이언트가 추가된다.
- 서버 재시작 시 모든 사용자가 로그아웃된다.
- 운영 배포 시 `SESSION_COOKIE_SECURE=true` 설정이 필요하다.
- 로그인 실패 제한(무차별 대입 방어)은 MVP에서 제외되어 후속 과제로 남는다.

## 검증 방법

- Backend 통합 테스트(실제 PostgreSQL)로 SPEC-AUTH AC-AUTH-01~15를 검증한다. GitHub Actions(JDK 25)에서 실행한다.
- Frontend Vitest로 AC-AUTH-16~21을 검증한다.
- Next.js rewrite 경유 쿠키·CSRF 동작(AC-AUTH-22)은 Windows 로컬에서 사용자가 수동으로 검증한다.

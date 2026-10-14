# ADR-0003: 개발 환경 및 Database 연결 구성

- 상태: **Accepted**
- 날짜: 2026-10-14
- 관련 문서: [`CLAUDE.md`](../../CLAUDE.md) §3-14, §7

## 배경

Backend·Frontend·PostgreSQL을 로컬과 Claude Code 클라우드(웹) 환경에서 일관되게 실행하고 검증할 수 있어야 한다. 비밀정보는 Git에 포함하지 않는다. 클라우드 환경에서는 Docker daemon을 쓸 수 없다(2026-10-14 확인).

## 검토한 대안

| 항목 | 채택 | 검토한 대안 |
| --- | --- | --- |
| 로컬 실행 | PostgreSQL만 Docker Compose, 앱은 호스트에서 실행 | 전체 Compose: hot reload·디버깅 불편 |
| 설정 주입 | 환경변수 + 루트 `.env`(Git 제외) | 프로파일별 yml에 값 기록: 비밀정보 유출 위험 |
| 프로파일 | 단일 `application.yml` (값은 환경변수) | `local`/`test` 프로파일 분리: 현재는 값 차이만 있어 불필요 |
| 테스트 DB | 실제 PostgreSQL (로컬 Compose / CI service / 클라우드 직접 실행) | H2: SQL 방언 차이로 Flyway SQL 공유 불가. Testcontainers: 클라우드에 Docker가 없어 실행 불가, 의존성 증가 |
| Frontend → Backend | Next.js `rewrites` 프록시 (동일 출처) | Backend CORS 허용 + 직접 호출 |
| 헬스 체크 | Actuator `health`만 노출, 상세 숨김 | 별도 `/api/health` Controller 작성 |

## 결정

### 1. 실행 구성

```text
[브라우저] → Next.js (호스트, :3000)
               └─ rewrite /backend/health → Spring Boot (호스트, :8080) /actuator/health
                                              └─ JDBC → PostgreSQL 16 (Docker Compose, :5432)
```

- `docker-compose.yml`은 `postgres:16` 서비스 하나만 정의한다(named volume, `pg_isready` healthcheck).
- Backend와 Frontend는 호스트에서 `./gradlew bootRun`, `npm run dev`로 실행한다.

### 2. 환경변수와 비밀정보

- 루트 `.env.example`(커밋)을 복사해 `.env`(Git 제외)를 만든다.
- `docker compose`는 루트 `.env`를 자동으로 읽는다. `POSTGRES_PASSWORD`가 없으면 실행을 거부한다(`:?`).
- Backend는 `spring.config.import: optional:file:../.env[.properties]`로 `backend/`에서 실행할 때 루트 `.env`를 읽는다. 실제 OS 환경변수(CI 등)가 있으면 그 값이 우선한다.
- `DB_PASSWORD`는 기본값이 없다. 값이 없으면 기동·테스트가 실패하도록 해서, 비밀번호가 코드에 들어가지 않게 한다.
- CI의 `POSTGRES_PASSWORD: docflow-ci`는 일회성 CI 컨테이너용 값이며 실제 비밀정보가 아니다.

### 3. Database 스키마 관리

- Flyway를 쓰고 `spring.jpa.hibernate.ddl-auto=validate`로 둔다. Hibernate가 스키마를 변경하지 않는다.
- `V1__baseline.sql`은 비즈니스 테이블 없이 `SELECT 1`만 실행하는 Baseline이다. Flyway 연결과 `flyway_schema_history` 기록을 검증하는 기준점이다.
- 도메인 테이블은 기능 SPEC 구현 시 `V2__...`부터 추가한다.
- **주의:** 지금은 Entity가 없으므로 `ddl-auto=validate`가 실제로 검증하는 도메인 스키마도 없다. 이 설정은 첫 Entity가 추가된 뒤에야 의미 있는 검증이 된다.

### 4. Actuator 노출 정책

- HTTP로는 `health`만 노출한다(`management.endpoints.web.exposure.include=health`).
- `show-details=never`, `show-components=never`로 두어 `{"status":"UP"}` 수준의 전체 상태만 응답한다. DB 종류, 디스크 경로 등 상세 정보는 숨긴다. `/actuator/health/db` 같은 구성 요소 경로도 404를 반환한다.
- Frontend 프록시도 `/backend/health` → `/actuator/health` 한 경로만 연결한다.
- 그 외 응답 필드: 응답에는 `"groups":["liveness","readiness"]`(probe 그룹 이름)가 포함된다. 내부 정보는 아니지만, 필요하면 `management.endpoint.health.probes.enabled=false`로 숨길 수 있다.

### 5. DB 장애 시 응답 시간

- `spring.datasource.hikari.connection-timeout=5000`(기본 30초)으로 설정했다.
- 검증 중, DB를 중단하면 health 응답이 30초 지연되었다. 같은 30초에 Next.js 프록시가 먼저 연결을 끊어 화면에 `DOWN` 대신 연결 오류가 표시되었다. 5초로 줄인 뒤에는 `503 DOWN`이 약 5초 만에 반환되어 화면에 정상적으로 표시된다.

### 6. 테스트·CI

- Backend 테스트는 실제 PostgreSQL에 연결한다(context load, JDBC 연결, Flyway 이력, Actuator 노출 범위).
- GitHub Actions(`.github/workflows/ci.yml`)
  - `backend` job: `postgres:16` service + `./gradlew build`
  - `frontend` job: Node 22, `npm ci` → `lint` → `test` → `build`

## 이유

- 클라우드(Docker 없음), 로컬(Docker Compose), CI(service container) 세 환경 모두에서 **같은 PostgreSQL 16과 같은 설정 키**로 검증할 수 있다.
- 동일 출처 프록시는 이후 Session Cookie 인증(ADR-0001 Q1, Proposed)과도 잘 맞는다.

## 영향

- Backend 테스트를 실행하려면 PostgreSQL이 실행 중이어야 한다(`docker compose up -d`). README에 명시했다.
- `../.env` 상대 경로는 `backend/` 디렉터리에서 실행할 때만 적용된다. IDE에서 실행할 때는 working directory를 `backend/`로 두거나 환경변수를 직접 지정한다.
- 기능 API가 추가되면 `/api/:path*` rewrite를 추가한다. 인증 SPEC에서는 CSRF·Cookie 정책과 함께 재검토한다.

## 검증 방법

| 환경 | 방법 |
| --- | --- |
| 클라우드 | PostgreSQL 16 서버 직접 실행 → `./gradlew build`, `bootRun` + `curl /actuator/health`, Playwright로 화면 상태 확인 |
| 로컬 | `docker compose up -d` → `docker compose ps`(healthy) → `./gradlew build` → `bootRun` + `npm run dev` |
| CI | GitHub Actions `CI` 워크플로 결과 |

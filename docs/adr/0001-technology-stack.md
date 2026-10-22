# ADR-0001: 기술 스택 및 버전

- 상태: **Accepted** (단, 아래 "잠정 결정" 항목은 **Proposed**)
- 후속 변경: Java 버전 결정(Java 21)은 [ADR-0004](./0004-java-25-upgrade.md)(2026-10-22, Java 25)로 대체되었다. 아래 본문은 6주차 당시 결정 기록으로 보존한다.
- 후속 변경: 잠정 결정 Q1(Session 인증), Q9(Entity ID BIGINT IDENTITY)는 [ADR-0005](./0005-session-authentication.md)(2026-10-22)에서 확정되었다.
- 날짜: 2026-10-14
- 관련 문서: [`spec.md`](../../spec.md) §11 기술 컨텍스트, [`CLAUDE.md`](../../CLAUDE.md) §5

## 배경

`spec.md` Draft v0.1은 예정 Stack(Next.js·React·TypeScript / Java·Spring Boot·Spring Data JPA / PostgreSQL)만 정의하고 정확한 버전은 "프로젝트 초기 설정 이후 확정"으로 남겨 두었다. 6주차 프로젝트 초기화에 앞서 사용할 버전과 의존성을 확정한다.

## 검토한 대안

| 항목 | 채택 | 검토한 대안 | 비고 |
| --- | --- | --- | --- |
| Java | 21 (LTS) | 25 (최신 LTS) | 클라우드 개발 환경에 21이 설치되어 바로 검증 가능 |
| Spring Boot | 4.1.x | 3.5.x, 4.0.x | 신규 프로젝트이므로 최신 안정 Minor 사용 |
| 빌드 도구 | Gradle Groovy DSL | Gradle Kotlin DSL, Maven | `spec.md` §15가 `./gradlew test`를 전제 |
| Lombok | 사용 안 함 | 사용 | DTO는 Java `record`로 충분, 의존성 최소화 |
| 스키마 관리 | Flyway + `ddl-auto=validate` | Liquibase, `ddl-auto=update` | 변경 이력을 SQL로 추적, 운영 위험 회피 |
| Spring Security | 이번 단계 제외 | 초기화 시 포함 | 인증 방식 상세 설계 전 도입 시 연결 검증만 복잡해짐 |
| PostgreSQL | 16 | 17, 18 | 클라우드 검증 환경(16.15)과 버전 일치 |
| Node.js / 패키지 매니저 | 22 LTS / npm | 24 LTS / pnpm | `spec.md` §15 명령어와 일치 |
| Next.js | 16.x (App Router) | Pages Router | App Router가 현재 기본 방식 |
| 스타일링 | Tailwind CSS 4 | CSS Modules | `create-next-app` 기본 옵션 |
| Frontend 테스트 | Vitest + Testing Library | Jest | 설정이 가볍고 Next.js 공식 가이드 존재 |
| Lint | ESLint CLI (`eslint`) | `next lint` | Next.js 16에서 `next lint` 제거 |

## 결정 — 실제 설치·확인된 버전

### Backend

| 항목 | 버전 | 확인 방법 |
| --- | --- | --- |
| Java | 21 (Gradle Toolchain), 클라우드: OpenJDK 21.0.12 | `java -version` |
| Spring Boot | **4.1.1** | Maven Central `spring-boot` / Gradle Plugin Portal 메타데이터에서 4.1.x 최신 GA 확인 (아래 참고) |
| Gradle Wrapper | 9.8.1 | `./gradlew --version` |
| io.spring.dependency-management | 1.1.7 | Gradle Plugin Portal |
| Spring Framework | 7.0.9 | `./gradlew dependencies` (Boot BOM 관리) |
| Spring Data JPA | 4.1.1 | 〃 |
| Hibernate ORM | 7.4.5.Final | 〃 |
| Flyway | 12.4.0 (+ `flyway-database-postgresql`) | 〃 |
| PostgreSQL JDBC | 42.7.13 | 〃 |
| HikariCP | 7.0.2 | 〃 |
| Embedded Tomcat | 11.0.24 | 〃 |

의존성(Spring Boot 4의 모듈화된 Starter 이름 사용):

- `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
- `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql`(runtime)
- 테스트: `spring-boot-starter-{webmvc,data-jpa,flyway,actuator}-test`

> **Spring Initializr 확인 불가:** Claude Code 클라우드 환경의 네트워크 정책이 `start.spring.io` 접속을 차단(HTTP 403)하여 Initializr에서 직접 확인하지 못했다. 대신 Maven Central(`org.springframework.boot:spring-boot` maven-metadata)과 Gradle Plugin Portal에서 4.1.x의 최신 GA가 **4.1.1**임을 확인하였다(2026-10-14 기준). 로컬에서 Initializr 기본값과 일치하는지 한 번 더 확인할 것을 권장한다.

### Frontend

| 항목 | 버전 |
| --- | --- |
| Node.js | 22 (클라우드: v22.22.0), `engines.node >=22` |
| Next.js / eslint-config-next | 16.4.0 |
| React / React DOM | 19.3.0 |
| TypeScript | 5.9.3 |
| Tailwind CSS | 4.3.3 (`@tailwindcss/turbopack`) |
| ESLint | 9.39.5 |
| Vitest | 5.0.4 (+ jsdom 29.1.1, @testing-library/react 16.3.3, @testing-library/jest-dom 7.0.1) |

- 프로젝트는 `create-next-app@16.4.0`(TypeScript, ESLint, Tailwind, App Router, `src/`, `@/*` alias)으로 생성했다.
- TypeScript는 npm `latest`가 7.x이지만 `create-next-app`이 지정한 `^5`(5.9.3)를 유지한다. Next.js 16과의 호환성이 검증된 범위를 우선한다.

### Database

- PostgreSQL **16**: 로컬은 Docker 이미지 `postgres:16`, CI는 GitHub Actions service `postgres:16`을 쓴다. 클라우드 검증은 PostgreSQL 16.15 서버를 직접 실행해서 했다.

### 잠정 결정 (Proposed — 기능 SPEC 구현 전 재검토)

| ID | 결정 | 재검토 시점 |
| --- | --- | --- |
| Q1 | **Session 기반 인증**을 기본 방향으로 한다. 6주차에는 Spring Security·인증을 구현하지 않는다. CSRF, Session Cookie, 권한 검증은 인증 SPEC에서 상세 설계한다. | 인증 SPEC 작성 시 |
| Q2 | 파일 저장소는 MVP에서 **로컬 파일시스템**을 기본 방향으로 한다. 저장소 인터페이스와 업로드 기능은 해당 SPEC에서 구현한다. | SPEC-03/04 작성 시 |
| Q9 | Entity ID는 **BIGINT IDENTITY**를 잠정 채택한다. 이번 단계에서는 Entity·도메인 테이블을 만들지 않는다. | 첫 도메인 SPEC 구현 시 |

## 이유

- 최신 안정 버전을 쓰되, 클라우드 검증 환경(Java 21, Node 22, PostgreSQL 16)과 일치하는 버전을 우선하여 "실제 실행으로 검증 가능한 상태"를 확보한다.
- 필요성이 확인되지 않은 인프라(Redis, Kafka, Message Queue, Microservices)와 라이브러리(Lombok, 상태관리·폼·UI 라이브러리)는 추가하지 않는다.

## 영향

- Spring Boot 4는 Jackson 3(`tools.jackson`), Jakarta EE 11, 모듈화된 Starter를 사용한다. Boot 3 기준 예제 코드와 패키지명이 다를 수 있다.
- `spring-boot-starter-validation`은 승인된 의존성이지만 이번 단계에서는 사용하는 코드가 없다. 첫 Request DTO 검증부터 사용한다.
- npm audit 결과: 운영 의존성(`--omit=dev`)은 취약점 0건이다. 개발 의존성(`eslint-config-next` → `fast-glob` → `micromatch` → `braces`)에 high 5건이 보고되었다. `npm audit fix --force`는 `eslint-config-next@14`로 다운그레이드하므로 적용하지 않았고, 업스트림 수정을 기다린다.

## 검증 방법

- `cd backend && ./gradlew build` (테스트 포함)
- `cd frontend && npm ci && npm run lint && npm run test && npm run build`
- GitHub Actions `CI` 워크플로(`.github/workflows/ci.yml`)

# DocFlow Specification

Version: Draft v0.4
Status: Development Environment Initialized
Last Updated: 2026-10-22

> v0.2 변경 요약: 6주차 초기화 결과에 맞춰 §11(확정 Stack·구조·개발 규칙·환경변수·Actuator 정책), §15(실제 명령어·검증 결과), §16(잠정 결정 3건 분리), §17(ADR-0001~0003 결정 추가)을 갱신하였다. §18의 v0.1 안내 문장을 삭제하였다. §1~§10, §12~§14는 v0.1과 동일하다.

> v0.3 변경 요약: Backend Java 버전을 21에서 25 LTS로 변경하였다(ADR-0004). §11.1·§11.2, §15(JDK 조건, §15.2 Java 25 검증 결과), §17을 갱신하였다. Java 21 기준 기록(§15.1, §17 2026-10-14)은 보존한다.

> v0.4 변경 요약: SPEC-AUTH(사용자 인증) 상세 SPEC([`specs/00-auth.md`](./specs/00-auth.md))을 추가하고 §9 SPEC 표에 등록하였다. §16.1에서 Session 인증과 Entity ID(BIGINT IDENTITY)를 확정하였다(ADR-0005). §11.1·§11.2·§11.6, §17을 갱신하였다. 인증 기능은 아직 구현되지 않았다.

## 1. 프로젝트 개요

### 1.1 프로젝트명

DocFlow

### 1.2 프로젝트 목적

DocFlow는 문서를 직접 작성하거나 공동 편집하는 도구가 아니라, 문서 작성 이후 발생하는

문서 등록 → 검토 요청 → 피드백 → 수정 → 재검토 → 승인

과정을 하나의 Workflow에서 관리하기 위한 시스템이다.

사용자가 문서의 현재 검토 상태와 피드백 처리 여부를 확인하고, 문서가 어떤 과정을 거쳐 최종 승인되었는지를 추적할 수 있도록 하는 것을 목표로 한다.

## 2. 문제 정의 및 사용자 조사 근거

### 2.1 인터뷰에서 확인된 주요 결과

총 5명의 문서 협업 경험자를 대상으로 사용자 인터뷰를 진행하였다.

| ID | 인터뷰 분석 결과 | 근거 |
| --- | --- | --- |
| F-01 | 피드백 또는 수정사항 반영 후 해당 내용을 다른 구성원에게 직접 전달하는 행동이 공통적으로 나타남 | 5/5 |
| F-02 | 버전 비교 경험은 일부 사용자에게만 나타났으며, 파일 기반 협업 사용자에게서 비교 작업의 번거로움이 확인됨 | 1/5 |
| F-03 | 변경 주체 및 변경 내용에 대한 추적 필요성이 명시적으로 나타남 | 1/5 |
| F-04 | 개별 작업 후 결과물을 다시 통합하는 과정에서 추가 작업이 발생하는 사례가 확인됨 | 응답자 1, 5 |

F-01의 경우 직접 전달이라는 행동은 모든 응답자에게 확인되었지만, 모든 사용자가 해당 방식 자체를 불편하다고 응답한 것은 아니다.

따라서 이를 확정된 사용자 문제로 일반화하기보다 MVP에서 검증할 핵심 문제 가설로 활용한다.

### 2.2 핵심 문제 가설

문서 검토 과정에서 피드백과 수정사항의 반영 결과가 문서의 검토 과정과 구조적으로 연결되지 않고 사용자의 직접적인 의사소통에 의존하기 때문에, 피드백의 처리 상태와 검토 진행 과정을 일관된 방식으로 관리하기 어렵다.

MVP 구현 이후 사용자 테스트를 통해 해당 문제와 해결 방식의 적절성을 추가 검증한다.

## 3. 요구사항 추적

인터뷰 결과에서 기능이 어떻게 도출되었는지를 추적할 수 있도록 다음 구조를 사용한다.

Interview Evidence → Finding → Requirement → Feature SPEC → Acceptance Criteria

| 분석 결과 | 도출된 요구사항 | 기능 SPEC | 출처 |
| --- | --- | --- | --- |
| F-01 피드백 반영 사실을 직접 전달 | 피드백을 검토 과정 안에서 기록 | Feedback 등록 | User Interview |
| F-01 피드백 반영 사실을 직접 전달 | 피드백 처리 상태 확인 | Feedback 상태 관리 | User Interview 기반 가설 |
| F-01 | 검토 과정 구조화 | Review Workflow | Solution Hypothesis |
| F-02, F-04 | 기존 문서를 유지하며 수정본 관리 | Document Version | User Interview |
| F-03 | 누가 언제 어떤 작업을 했는지 확인 | Audit Log | User Interview |
| F-02 + 프로젝트 요구 | 버전 간 주요 변경사항 확인 | Version Analysis | Interview + Project Requirement |
| 버전 변경사항 파악 지원 | AI 변경사항 요약 | AI Version Summary | Advisor Requirement + Project Research |

인터뷰에서 직접 도출된 요구사항과 시스템 설계 또는 연구 목적으로 추가된 요구사항을 구분하여 관리한다.

## 4. 목표와 비목표

### 4.1 In Scope — MVP

- 사용자 인증
- 프로젝트 생성 및 구성원 관리
- 문서 등록
- 문서 버전 관리
- 검토자 지정 및 검토 요청
- 피드백 등록
- 피드백 반영 상태 관리
- 수정 요청
- 재검토
- 문서 승인
- 문서 Review 상태 관리
- 주요 변경 이력 기록 및 조회
- 전체 Review Workflow End-to-End 동작

### 4.2 Post-MVP

- AI 기반 버전 변경사항 요약
- 상세 버전 Diff
- 검토 기한
- 알림 및 리마인드
- Dashboard
- 검색
- RAG 기반 문서 질의응답
- AI Review Agent

### 4.3 Out of Scope

이번 MVP에서는 다음 기능을 구현하지 않는다.

- Google Docs 수준의 실시간 공동 편집
- Word/PPT 등 Office 문서 자체 편집
- 여러 사용자가 만든 문서의 자동 병합
- 실시간 채팅
- 모바일 애플리케이션
- 복잡한 조직/부서 단위 권한 체계
- RAG 및 AI Agent
- 실제 필요성이 확인되지 않은 Redis, Kafka 등의 추가 인프라

DocFlow는 기존 문서 작성 도구를 대체하기보다 기존 도구에서 작성된 문서의 검토 및 승인 Workflow 관리에 집중한다.

## 5. 핵심 사용자

### Author

문서를 등록·수정하고 검토를 요청하는 사용자.

주요 행동:

- 문서 등록
- 새로운 버전 등록
- Reviewer 지정 및 검토 요청
- Feedback 확인
- Feedback 반영
- 재검토 요청

### Reviewer

검토 요청을 받은 문서를 확인하고 피드백 및 검토 결과를 제공하는 사용자.

주요 행동:

- 검토 문서 조회
- Feedback 작성
- 반영 결과 확인
- 수정 요청
- 최종 승인

MVP에서 Author와 Reviewer를 고정된 Project Role로 관리할지 Review 단위 역할로 관리할지는 아직 결정하지 않는다.

## 6. 핵심 Workflow

### 6.1 정상 Workflow

```text
문서 등록
    ↓
DRAFT
    ↓
검토 요청
    ↓
IN_REVIEW
    ↓
검토
    ↓
┌───────────────────┐
│                   │
승인              수정 요청
│                   │
↓                   ↓
APPROVED      CHANGES_REQUESTED
                    ↓
                문서 수정
                    ↓
              새 Version 등록
                    ↓
                재검토 요청
                    ↓
                RE_REVIEW
                    ↓
           승인 또는 추가 수정
```

재검토 과정에서 추가 수정이 필요한 경우 다음 과정이 반복될 수 있다.

RE_REVIEW

→ CHANGES_REQUESTED

→ 새 Version

→ RE_REVIEW

## 7. 상태 정의

### 7.1 Document Status

| 상태 | 의미 |
| --- | --- |
| DRAFT | 문서 작성 또는 수정 중 |
| IN_REVIEW | 최초 검토 중 |
| CHANGES_REQUESTED | Reviewer가 수정을 요청한 상태 |
| RE_REVIEW | 수정본에 대한 재검토 중 |
| APPROVED | 최종 승인 완료 |

허용되지 않은 상태 전이는 서버에서 거부한다.

예를 들어 별도의 검토 과정 없이

DRAFT → APPROVED

상태로 변경하는 것은 허용하지 않는다.

### 7.2 Feedback Status

| 상태 | 의미 |
| --- | --- |
| OPEN | 아직 반영되지 않은 Feedback |
| APPLIED | Author가 수정사항을 반영했다고 표시 |
| CONFIRMED | Reviewer가 반영 결과를 확인 |

기본 상태 전이:

OPEN → APPLIED → CONFIRMED

MVP에서는 OPEN → CONFIRMED와 같은 상태 건너뛰기를 허용하지 않는 것을 초기 정책으로 한다.

## 8. 핵심 User Story

### US-01 검토 요청

Author로서 특정 문서 버전의 검토자를 지정하고 검토를 요청하고 싶다. 이를 통해 누가 어떤 문서를 검토해야 하는지 확인할 수 있다.

### US-02 피드백 등록

Reviewer로서 검토 과정에서 발견한 수정 요청이나 의견을 기록하고 싶다. 이를 통해 검토 의견을 문서 Workflow 안에서 관리할 수 있다.

### US-03 피드백 반영

Author로서 어떤 Feedback을 수정에 반영했는지 표시하고 싶다. 이를 통해 Reviewer가 별도의 전달 없이 반영 여부를 확인할 수 있다.

### US-04 피드백 확인

Reviewer로서 Feedback이 실제로 반영되었는지 확인하고 싶다.

### US-05 문서 버전 관리

Author로서 수정된 문서를 새로운 버전으로 등록하면서 이전 버전을 유지하고 싶다.

### US-06 변경 이력 확인

프로젝트 구성원으로서 누가 언제 주요 작업을 수행했는지 확인하고 싶다.

## 9. 기능 SPEC 구성

큰 기능을 하나의 SPEC으로 구현하지 않고 다음과 같이 작은 기능 단위로 분리한다.

| SPEC | 기능 | MVP |
| --- | --- | --- |
| SPEC-AUTH | 사용자 인증 (로그인·로그아웃·현재 사용자 조회) | O |
| SPEC-01 | Project 생성 | O |
| SPEC-02 | Project Member 관리 | O |
| SPEC-03 | Document 등록 | O |
| SPEC-04 | Document Version 등록 | O |
| SPEC-05 | Document Version 조회 | O |
| SPEC-06 | Review 요청 | O |
| SPEC-07 | Feedback 등록 | O |
| SPEC-08 | Feedback 반영 (OPEN → APPLIED) | O |
| SPEC-09 | Feedback 확인 (APPLIED → CONFIRMED) | O |
| SPEC-10 | 수정 요청 | O |
| SPEC-11 | 재검토 | O |
| SPEC-12 | 승인 | O |
| SPEC-13 | Audit Log | O |
| SPEC-14 | AI Version Summary | Post-MVP |

각 기능은 실제 개발 전에 별도의 specs/*.md로 작성한다.

## 10. 대표 기능 상세 SPEC

v0.1에서는 전체 기능의 상세 SPEC을 모두 확정하지 않고, 실제 개발 시 기능별로 상세화한다.

현재 대표 기능으로 Review 요청의 초기 SPEC을 정의한다.

### SPEC-06 Review 요청

#### 10.1 배경 & 목적

해결하려는 문제

문서 검토 요청이 외부 메신저 등의 채널에서 이루어질 경우 시스템에서 누가 어떤 문서 버전을 검토해야 하는지 관리하기 어렵다.

사용자가 할 수 있게 되는 것

Author가 특정 DocumentVersion과 Reviewer를 지정하여 Review를 생성할 수 있다.

#### 10.2 범위

In Scope

- 검토 대상 DocumentVersion 선택
- Reviewer 선택
- Review 생성
- 요청자, Reviewer, 대상 Version, 요청 시간 기록
- Review 생성 후 Document 상태 변경
- Reviewer의 할당된 Review 조회

Out of Scope

- 다중 Reviewer
- Review 기한
- Email/Push 알림
- Review 자동 할당
- 순차/병렬 Review
- AI Reviewer

#### 10.3 정상 흐름

1. Author가 검토할 DocumentVersion을 선택한다.
2. Reviewer를 선택한다.
3. 검토 요청을 제출한다.
4. 시스템이 요청자의 프로젝트 접근 권한을 확인한다.
5. 시스템이 Reviewer의 프로젝트 참여 여부를 확인한다.
6. 대상 DocumentVersion의 존재 여부를 확인한다.
7. Review를 생성한다.
8. Document 상태를 IN_REVIEW로 변경한다.
9. Reviewer가 할당된 Review를 조회할 수 있다.

#### 10.4 예시

| 상황 | 기대 결과 |
| --- | --- |
| 정상 Author + Version + Reviewer | Review 생성 |
| Reviewer가 프로젝트 구성원이 아님 | 요청 거부 |
| 존재하지 않는 Version | 요청 거부 |
| 정상 Review 생성 | Document → IN_REVIEW |
| Review 생성 과정 일부 실패 | 데이터 전체 Rollback |

#### 10.5 실패 시나리오

- 프로젝트 접근 권한 없음
- Reviewer가 프로젝트 구성원이 아님
- 존재하지 않는 DocumentVersion
- Review 저장 실패
- Document 상태 변경 실패
- 동일 요청 중복 제출
- 네트워크 실패

#### 10.6 Acceptance Criteria

- 권한이 있는 Author가 존재하는 DocumentVersion에 Review를 생성할 수 있다.
- Reviewer는 동일 프로젝트의 구성원이어야 한다.
- Review에는 요청자, Reviewer, 대상 Version, 요청 시간이 저장된다.
- Review 생성 성공 후 Document 상태는 IN_REVIEW가 된다.
- Review 생성과 Document 상태 변경 중 하나라도 실패하면 전체 변경사항이 저장되지 않는다.
- 프로젝트 구성원이 아닌 사용자는 Review를 생성할 수 없다.
- 존재하지 않는 DocumentVersion에는 Review를 생성할 수 없다.
- Reviewer는 자신에게 할당된 Review를 조회할 수 있다.
- 관련 Backend 자동화 테스트가 통과한다.

## 11. 기술 컨텍스트

### 11.1 확정 Stack

6주차 프로젝트 초기화에서 다음 버전을 설치·검증하였다. 선택 이유와 검토한 대안은 [ADR-0001](./docs/adr/0001-technology-stack.md)을 따른다.

2026-10-22에 Java 버전을 21에서 25 LTS로 변경하였다([ADR-0004](./docs/adr/0004-java-25-upgrade.md)). 다른 버전은 변경하지 않았다.

| 구분 | 기술 및 버전 |
| --- | --- |
| Frontend | Node.js 22, npm, Next.js 16.4.0 (App Router), React 19.3.0, TypeScript 5.9.3, Tailwind CSS 4.3.3, ESLint 9 (ESLint CLI), Vitest 5.0.4 |
| Backend | Java 25 (Gradle Toolchain), Spring Boot 4.1.1, Gradle 9.8.1 (Groovy DSL), Spring Data JPA 4.1.1, Hibernate ORM 7.4.5, Flyway 12.4.0, PostgreSQL JDBC 42.7.13 |
| Database | PostgreSQL 16 |
| CI | GitHub Actions |

- Spring Boot 4.1.1은 Spring Initializr가 아니라 Maven Central 메타데이터로 4.1.x 최신 안정 버전임을 확인하였다. 클라우드 개발 환경의 네트워크 정책이 `start.spring.io`를 차단하였기 때문이다.
- Spring Security 세션 인증을 도입하였다(SPEC-AUTH, [`specs/00-auth.md`](./specs/00-auth.md), [ADR-0005](./docs/adr/0005-session-authentication.md)). 의존성: `spring-boot-starter-security`(Spring Security 7.1.1), 테스트용 `spring-boot-starter-security-test`.

#### AI — Post-MVP

- Python
- FastAPI
- LLM API

AI Stack은 Post-MVP 예정이며 아직 설치하지 않았다.

### 11.2 Repository 구조

```text
docflow/
├── .github/workflows/ci.yml   # Backend build/test, Frontend lint/test/build
├── backend/                   # Spring Boot (Gradle)
├── frontend/                  # Next.js (npm)
├── docs/
│   ├── architecture.md
│   └── adr/                   # 0001-technology-stack ~ 0005-session-authentication
├── specs/
│   ├── README.md              # 작성 안내
│   ├── 00-auth.md             # SPEC-AUTH (Issue #4)
│   ├── 01-project-create.md   # (작성 예정)
│   ├── ...
│   └── 14-ai-version-summary.md   # (작성 예정, Post-MVP)
├── docker-compose.yml         # 로컬 PostgreSQL 16
├── .env.example               # 환경변수 예시 (.env는 Git 제외)
├── .gitattributes             # Gradle Wrapper·배치 파일 줄바꿈 규칙
├── CLAUDE.md
└── spec.md
```

### 11.3 패키지 구조

상세 결정은 [ADR-0002](./docs/adr/0002-package-structure.md)를 따른다.

Backend는 Domain 중심 패키지 구조를 사용하고, 도메인 내부는 계층으로 분리한다.

```text
com.docflow
├── global/          # 횡단 관심사 (config, error, common) — 필요해질 때 생성
└── <domain>/        # user, project, document, review, feedback, audit — 기능 SPEC 구현 시 생성
    ├── controller/
    ├── service/
    ├── repository/
    ├── domain/
    └── dto/
```

Frontend는 Next.js App Router와 features 중심 구조를 사용한다.

```text
frontend/src
├── app/                  # 라우팅·레이아웃
└── features/<feature>/   # 기능별 api.ts, types.ts, components/, 테스트
```

아직 구현하지 않은 기능의 빈 패키지·디렉터리는 생성하지 않는다.

### 11.4 기본 개발 규칙

- Controller는 HTTP 요청/응답을 담당한다.
- 핵심 비즈니스 규칙은 Service에서 처리한다.
- Controller가 Repository를 직접 호출하지 않는다.
- Entity를 API Response로 직접 반환하지 않는다.
- 권한 검증을 수행한 뒤 비즈니스 로직을 실행한다.
- 상태 전이는 Server에서 검증한다.
- 서로 일관성을 유지해야 하는 변경은 Transaction으로 처리한다.
- Frontend는 최소한 Loading / Empty / Error / Success 상태를 구분한다.
- 다른 도메인의 Repository 직접 참조는 지양하되 모든 접근을 Service 경유로 강제하지는 않는다. 도메인 간 순환 의존은 만들지 않는다.
- Database 스키마는 Flyway migration으로만 변경한다(`spring.jpa.hibernate.ddl-auto=validate`).

### 11.5 환경변수·비밀정보 관리

상세 결정은 [ADR-0003](./docs/adr/0003-development-environment.md)을 따른다.

- 저장소 루트의 `.env.example`만 Git에 포함한다. 실제 값은 `.env`에 두며 `.env`는 Git에서 제외한다.
- 비밀번호, API Key 등 비밀정보를 코드·설정 파일에 기록하지 않는다.

| 변수 | 사용처 | 비고 |
| --- | --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | `docker-compose.yml` | `POSTGRES_PASSWORD`가 없으면 Compose 실행 거부 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Backend | `DB_PASSWORD`는 기본값 없음. 값이 없으면 기동·테스트 실패 |
| `BACKEND_URL` | Frontend (Next.js rewrites) | 기본값 `http://localhost:8080` |

- Backend는 `backend/`에서 실행하면 루트 `.env`를 읽는다. OS 환경변수가 있으면 환경변수가 우선한다.
- GitHub Actions의 DB 비밀번호는 일회성 CI 컨테이너용 값이며 실제 비밀정보가 아니다.

### 11.6 Actuator 노출 정책

- HTTP로는 `health` 엔드포인트만 노출한다.
- `show-details`와 `show-components`를 `never`로 두어 전체 상태(UP/DOWN)만 응답한다. DB 종류, 디스크 경로 등 상세 정보는 노출하지 않는다.
- 다른 Actuator 엔드포인트와 `/actuator/health/db` 같은 구성 요소 경로는 404를 반환한다.
- Frontend 프록시는 `/backend/health` → `/actuator/health` 한 경로만 연결한다.
- Spring Security 도입 시 이 정책을 재검토한다. SPEC-AUTH 설계에서는 `/actuator/health`만 `permitAll`로 두고, 나머지 Actuator 경로는 `denyAll`(익명 `401`, 인증 사용자 `403`)로 처리한다. 노출 설정(`exposure.include=health`)은 변경하지 않는다(`specs/00-auth.md` §6.2).

## 12. 공통 실패 및 예외 상황

기능별 SPEC 작성 시 다음 상황 중 해당되는 항목을 반드시 검토한다.

- 인증되지 않은 사용자
- 프로젝트 구성원이 아닌 사용자
- 권한이 없는 사용자
- 존재하지 않는 Resource
- 잘못된 입력
- 잘못된 상태 전이
- 중복 요청
- 동시 상태 변경
- Transaction 실패
- Network 실패
- 파일 업로드 실패

AI 기능에서는 추가로 다음을 검토한다.

- AI API Timeout
- AI API 오류
- 분석 실패
- 재시도
- AI 분석 실패와 핵심 Workflow의 분리

## 13. MVP Acceptance Criteria

다음 조건을 모두 만족했을 때 1차 MVP가 완료된 것으로 판단한다.

- 사용자가 Project를 생성할 수 있다.
- Project에 Document를 등록할 수 있다.
- 최초 등록 시 DocumentVersion이 생성된다.
- 기존 버전을 유지하면서 새로운 Version을 등록할 수 있다.
- Reviewer를 지정하여 Review를 요청할 수 있다.
- Reviewer가 Feedback을 등록할 수 있다.
- 새로운 Feedback의 초기 상태가 OPEN이다.
- Author가 Feedback을 OPEN → APPLIED로 변경할 수 있다.
- Reviewer가 Feedback을 APPLIED → CONFIRMED로 변경할 수 있다.
- Reviewer가 수정을 요청할 수 있다.
- 수정된 문서를 새로운 Version으로 등록할 수 있다.
- 새로운 Version을 대상으로 재검토할 수 있다.
- Reviewer가 문서를 승인할 수 있다.
- 승인된 Document의 상태가 APPROVED이다.
- 권한이 없는 사용자의 요청이 거부된다.
- 허용되지 않은 상태 전이가 거부된다.
- 주요 Workflow 이벤트가 AuditLog에 기록된다.
- 핵심 Workflow를 처음부터 끝까지 수행할 수 있다.

최종 End-to-End Scenario:

```text
Document 등록
→ Review 요청
→ Feedback 등록
→ 수정 요청
→ 새 Version 등록
→ Feedback APPLIED
→ 재검토
→ Feedback CONFIRMED
→ 승인
→ APPROVED
```

## 14. 기능별 Definition of Done

하나의 기능은 다음 조건을 모두 만족했을 때 Done으로 판단한다.

- 기능별 SPEC이 작성되어 있다.
- In Scope 항목이 모두 구현되어 있다.
- Acceptance Criteria가 모두 충족된다.
- 정상 흐름에 대한 테스트가 존재한다.
- 주요 실패 시나리오가 처리되어 있다.
- 권한 검증이 완료되어 있다.
- 허용되지 않은 상태 전이가 차단된다.
- 필요한 Frontend 상태가 구현되어 있다.
- Backend와 Frontend가 필요한 경우 실제 연동되어 있다.
- 자동화 테스트가 통과한다.
- 수동 검증 절차가 통과한다.
- 실제 구현과 SPEC이 일치한다.

Acceptance Criteria가 하나라도 충족되지 않은 기능은 완료된 것으로 처리하지 않는다.

## 15. 검증 방법

### Backend

```bash
cd backend
./gradlew build    # 컴파일 + 테스트
./gradlew test     # 테스트만
```

Backend 빌드·테스트에는 JDK 25가 필요하다(Gradle Toolchain). Gradle이 JDK를 자동으로 다운로드하지 않으므로 직접 설치한다.

Backend 테스트는 실행 중인 PostgreSQL 16이 필요하다. H2 등 내장 DB는 사용하지 않는다.

- 로컬: `docker compose up -d`로 PostgreSQL을 실행한 뒤 테스트한다.
- CI: GitHub Actions의 `postgres:16` service container를 사용한다.
- DB 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 주입한다(§11.5).

각 핵심 비즈니스 기능은 최소한 다음 경우를 검증한다.

1. 정상 요청
2. 권한 실패
3. 비정상 입력
4. 존재하지 않는 Resource
5. 허용되지 않은 상태 전이

### Frontend

```bash
cd frontend
npm ci             # 또는 npm install
npm run lint       # ESLint CLI
npm run test       # vitest run
npm run build
npm run dev        # 개발 서버 (http://localhost:3000)
```

주요 화면은 최소한 다음 상태를 확인한다.

- Loading
- Empty
- Success
- Validation Error
- Permission Error
- Network / Server Error

### CI

`.github/workflows/ci.yml`이 push와 Pull Request마다 다음을 실행한다.

- `backend` job: `postgres:16` service + `./gradlew build`
- `frontend` job: Node.js 22, `npm ci` → `npm run lint` → `npm run test` → `npm run build`

### 15.1 6주차 초기화 검증 결과 (2026-10-14)

검증 환경을 구분하여 기록한다. 실제로 실행하지 않은 항목은 "미검증"으로 표시한다.

| 항목 | 클라우드 (Claude Code) | GitHub Actions CI | 로컬 |
| --- | --- | --- | --- |
| Backend build/test (5건) | 통과 (PostgreSQL 16.15 직접 실행) | 통과 | 미검증 |
| Flyway `V1__baseline` 적용 | 통과 | 통과 (테스트로 확인) | 미검증 |
| `bootRun` 후 health UP / DB 중단 시 DOWN | 통과 | — | 미검증 |
| Actuator 상세 정보·기타 엔드포인트 비노출 | 통과 | 통과 (테스트로 확인) | 미검증 |
| Frontend lint / test (5건) / build | 통과 | 통과 | 미검증 |
| 연결 확인 화면 상태 (UP / DOWN / 연결 오류) | 통과 (Playwright) | — | 미검증 |
| `docker compose up` (PostgreSQL 16) | 실행 불가 (Docker daemon 없음) | — | 미검증 |
| 도메인 스키마 검증 (`ddl-auto=validate`) | 해당 없음 (Entity 없음) | 해당 없음 | 해당 없음 |

- 현재 Entity가 없으므로 `ddl-auto=validate`가 검증하는 도메인 스키마도 없다. 첫 Entity 추가 시점부터 의미 있는 검증이 된다.
- 로컬 검증 결과가 확인되면 이 표를 갱신한다.

### 15.2 Java 25 전환 검증 결과 (2026-10-22)

§15.1은 Java 21 기준 결과이며 그대로 보존한다. 아래는 Java 25 전환(ADR-0004) 이후 결과이다. 실제로 실행하지 않은 항목은 "미검증"으로 표시한다.

검증 주체를 구분한다.

- **클라우드**와 **GitHub Actions CI** 열: Claude Code가 직접 실행했거나 CI 로그로 확인한 결과
- **Windows 로컬** 열: **사용자가 직접 실행하고 보고한 결과**. Claude Code는 Windows 환경에서 실행하지 않았다.

| 항목 | 클라우드 (Claude Code) | GitHub Actions CI | Windows 로컬 (사용자 실행·보고) |
| --- | --- | --- | --- |
| JDK 25 확인 | 실행 불가 (JDK 21만 존재, JDK 25 다운로드 차단) | 통과 (Temurin 25.0.4+1) | 통과 (Oracle JDK 25.0.5.0.2, `java -version`) |
| Gradle 9.8.1 / Launcher·Daemon JVM | — | 통과 (Gradle 9.8.1) | 통과 (`gradlew.bat --version`, Launcher·Daemon 모두 Java 25) |
| Backend build/test (5건) on JDK 25 | 실행 불가 | 통과 (`./gradlew build`, `:test` 성공) | 통과 (`gradlew.bat clean build` BUILD SUCCESSFUL) |
| `docker compose config --quiet` | 형식 검증만 통과 (6주차) | — | 통과 |
| `docker compose up -d` / `ps` (PostgreSQL 16) | 실행 불가 (Docker daemon 없음) | — | 통과 (컨테이너 `healthy`) |
| `bootRun` 후 `/actuator/health` | 실행 불가 (JDK 25 없음) | — | 통과 (`{"groups":["liveness","readiness"],"status":"UP"}`) |
| Frontend `npm ci` / lint / test / build | 통과 (Java와 무관, Node 22) | 통과 | 통과 (Vitest 5건 통과, 프로덕션 빌드 성공) |
| 연결 확인 화면: 정상 연결 (UP) | 실행 불가 | — | 통과 ("Backend 정상 (UP)" 표시) |
| 연결 확인 화면: PostgreSQL 중지 → 재시작 | 실행 불가 | — | 통과 (DOWN 표시 → UP 복구) |
| 연결 확인 화면: Backend 종료 → 재시작 | 실행 불가 | — | 통과 (연결 오류 표시 → UP 복구) |

- Windows 로컬 환경(사용자 보고): Windows 11, Oracle JDK 25.0.5.0.2, Gradle 9.8.1, PostgreSQL 16(Docker Compose), Node.js 22.
- JDK 배포판이 다르다. CI는 Eclipse Temurin, Windows 로컬은 Oracle JDK이다. 두 환경 모두 Java 25에서 Backend 빌드·테스트가 통과하였다.
- `backend/`에서 `bootRun` 실행 시 루트 `.env` 자동 로딩은 2026-10-22 클라우드에서 **JDK 21**로 확인하였다(`DB_*` 환경변수 없이 health UP). 이 동작은 `application.yml`의 `spring.config.import` 설정에 의한 것이며 Java 버전과 무관하다. Windows 로컬 보고에는 `.env` 로딩 방식에 대한 별도 확인 내용이 없다.
- GitHub Actions 결과: PR #3 CI run 39744463956 (2026-10-22, 커밋 `1c17257`).
- Java 25 전환에 대해 클라우드에서 직접 실행하지 못한 Backend 항목은 CI와 사용자의 Windows 로컬 검증으로 확인되었다.

## 16. 열린 질문

원칙: 결정되지 않은 항목은 구현 과정에서 임의로 가정하지 않고 SPEC을 먼저 수정한 후 구현한다.

### 16.1 사전 결정 사항

다음 사항은 6주차에 잠정 결정(Proposed)한 뒤, 해당 기능 SPEC에서 재검토하여 확정한다.

| 질문 | 결정 | 상태 | 근거 |
| --- | --- | --- | --- |
| 인증 방식은 Session/JWT 중 어떤 방식을 사용할 것인가? | Spring Security 서버 세션 인증. JWT와 혼용하지 않는다. CSRF, Session Cookie, 인가 규칙은 SPEC-AUTH에서 정의한다. | **확정** (2026-10-22, SPEC-AUTH) | [ADR-0005](./docs/adr/0005-session-authentication.md), [`specs/00-auth.md`](./specs/00-auth.md) |
| 파일 저장소는 어떤 방식으로 구성할 것인가? | MVP에서는 로컬 파일시스템. 저장소 인터페이스(`FileStorage`)와 업로드 기능은 SPEC-03에서 구현한다. | **확정** (2026-10-27, SPEC-03) | [ADR-0001](./docs/adr/0001-technology-stack.md) Q2, [`specs/03-document-create.md`](./specs/03-document-create.md) §5.4 |
| Entity ID 전략은 무엇으로 할 것인가? | `BIGINT GENERATED ALWAYS AS IDENTITY` | **확정** (2026-10-22, SPEC-AUTH) | [ADR-0005](./docs/adr/0005-session-authentication.md) |

### 16.2 미결정

다음 사항은 아직 결정되지 않았으며, 구현 전에 결정한다.

- Author와 Reviewer를 Project의 고정 Role로 관리할 것인가, Review 단위 역할로 관리할 것인가?
- 하나의 Review에 Reviewer를 1명만 허용할 것인가?
- 여러 Reviewer가 존재한다면 승인 조건은 어떻게 정의할 것인가?
- Feedback을 APPLIED로 변경하려면 반드시 새로운 DocumentVersion이 존재해야 하는가?
- Feedback이 어떤 DocumentVersion에 반영되었는지를 저장할 것인가?
- Feedback을 문서 전체 단위로 작성할 것인가, 특정 페이지/위치와 연결할 것인가?
- 승인된 Document에 새로운 Version을 추가할 수 있는가?
- 동일 Version에 중복 Review 요청을 허용할 것인가?
- Document 파일 형식은 어디까지 지원할 것인가?
- AuditLog의 기록 범위를 어디까지 설정할 것인가?
- 삭제 기능을 MVP에 포함할 것인가?

## 17. 결정 기록

#### 2026-10-07 — Document Review Workflow에 집중

DocFlow를 Google Docs와 같은 문서 작성/공동편집 도구로 개발하지 않고 기존 문서의 검토 및 승인 Workflow를 관리하는 시스템으로 정의하였다.

이유: 사용자 인터뷰에서 실시간 공동편집 환경에서는 버전 관련 문제가 상대적으로 적게 나타났으며, 프로젝트의 핵심 문제를 검토와 피드백 과정에 집중하기 위함이다.

#### 2026-10-07 — 기능 단위 SPEC 사용

하나의 큰 개발 명세 대신 각 기능을 작은 SPEC으로 분리한다.

이유: 기능별 Scope와 Acceptance Criteria를 독립적으로 정의하고 검증하기 위함이다.

#### 2026-10-07 — AI 기능 Post-MVP 배치

AI Version Summary는 프로젝트의 주요 확장 기능으로 유지하지만 초기 MVP에서는 제외한다.

이유: AI 기능보다 기본 Review Workflow가 실제 사용자 문제를 해결하는지 먼저 검증하기 위함이다.

#### 2026-10-07 — Feedback 상태 분리

Feedback 상태를 OPEN → APPLIED → CONFIRMED로 정의하였다.

이유: 작성자가 “수정했다”고 표시하는 행위와 Reviewer가 “실제로 반영되었다”고 확인하는 행위를 구분하기 위함이다.

#### 2026-10-14 — 기술 스택 및 버전 확정

Java 21 / Spring Boot 4.1.1 / Gradle(Groovy DSL), Next.js 16.4 / React 19.3 / TypeScript / Tailwind CSS, PostgreSQL 16을 사용한다. Lombok과 Spring Security는 초기 프로젝트에 포함하지 않는다. 인증(Session), 파일 저장소(로컬 파일시스템), Entity ID(BIGINT IDENTITY)는 잠정 결정으로 둔다.

이유: 최신 안정 버전을 사용하되 클라우드 개발 환경에서 실제로 검증 가능한 버전을 우선하고, 필요성이 확인되지 않은 의존성은 추가하지 않기 위함이다.

상세: [ADR-0001](./docs/adr/0001-technology-stack.md)

#### 2026-10-14 — Domain 중심 패키지 구조

Backend는 Domain 중심 패키지와 도메인 내부 계층 분리를, Frontend는 App Router와 features 중심 구조를 사용한다. 도메인 간 Repository 직접 참조는 지양하되 엄격히 강제하지 않는다.

이유: 기능 SPEC 단위 개발과 AI Agent 작업 지시에서 변경 범위를 패키지 단위로 모으기 위함이다.

상세: [ADR-0002](./docs/adr/0002-package-structure.md)

#### 2026-10-14 — 개발 환경 및 Database 연결 구성

로컬에서는 PostgreSQL만 Docker Compose로 실행하고 Backend·Frontend는 호스트에서 실행한다. 설정은 환경변수와 `.env`(Git 제외)로 주입하고, 스키마는 Flyway로 관리하며, Actuator는 health 전체 상태만 노출한다. DB 장애 시 빠르게 실패하도록 DB 연결 대기 시간을 5초로 설정하였다.

이유: 클라우드·로컬·CI에서 같은 PostgreSQL 16과 같은 설정 키로 검증하고, 비밀정보와 내부 정보 노출을 막기 위함이다.

상세: [ADR-0003](./docs/adr/0003-development-environment.md)

#### 2026-10-22 — Java 25 LTS로 업그레이드

Backend의 Java 버전을 21에서 25 LTS로 변경한다. Spring Boot 4.1.1, Gradle 9.8.1, Frontend 버전은 유지한다. JDK 25는 개발자가 직접 설치하며, Gradle 자동 다운로드(foojay) 의존성은 추가하지 않는다. Windows 줄바꿈 문제 방지를 위해 `.gitattributes`를 추가한다.

이유: 7주차 기능 구현 전, 비즈니스 코드가 없는 시점에 최신 LTS로 전환하여 전환 비용을 최소화하기 위함이다.

상세: [ADR-0004](./docs/adr/0004-java-25-upgrade.md)

#### 2026-10-22 — Spring Security 세션 기반 인증 (SPEC-AUTH)

인증은 Spring Security 서버 세션(메모리 저장, 미사용 30분 만료)으로 하고 JWT와 혼용하지 않는다. 이메일·비밀번호 로그인, CSRF(`csrf.spa()`), 세션 고정 방어, 401/403 JSON 응답을 적용한다. 회원가입과 전역 Role은 두지 않고, 개발 계정은 `local` 프로파일 전용 Seeder로 준비한다. Entity ID는 BIGINT IDENTITY로 확정한다.

이유: 동일 출처 웹 앱 하나에서는 서버 세션이 가장 단순하고, 로그아웃·계정 비활성화를 서버에서 즉시 반영할 수 있기 때문이다.

상세: [ADR-0005](./docs/adr/0005-session-authentication.md), [`specs/00-auth.md`](./specs/00-auth.md)

## 18. 향후 SPEC 발전 방식

개발은 다음 과정을 반복한다.

```text
Requirement
→ Feature SPEC 작성
→ Acceptance Criteria 확정
→ 구현
→ Test
→ Acceptance Criteria 검증
→ 발견된 문제 및 예외 상황 반영
→ SPEC 수정
```

각 기능을 개발하기 직전에 해당 기능의 specs/*.md를 구체화하고, 구현 과정에서 발견된 새로운 Edge Case와 기술적 의사결정을 다시 SPEC에 반영한다.

이를 통해 SPEC과 실제 구현 결과가 지속적으로 일치하도록 관리한다.

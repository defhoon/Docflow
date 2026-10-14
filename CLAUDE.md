# CLAUDE.md — DocFlow 개발 지침

## 1. 프로젝트 개요

DocFlow는 문서 작성 이후 발생하는 **문서 등록 → 검토 요청 → 피드백 → 수정 → 재검토 → 승인** 과정을 관리하는 Document Review Workflow 시스템이다.

DocFlow는 Google Docs와 같은 실시간 공동 편집 도구가 아니다. 프로젝트의 요구사항, MVP 범위, 상태 전이 및 완료 기준은 저장소 루트의 [`spec.md`](./spec.md)를 기준으로 한다.

## 2. 문서의 역할과 우선순위

- `spec.md`: 프로젝트 목표, 범위, 핵심 Workflow, 공통 정책 및 MVP Acceptance Criteria
- `specs/*.md`: 개별 기능의 상세 동작, 예외 처리 및 Acceptance Criteria (작성된 경우)
- `CLAUDE.md`: Claude Code의 작업 절차, 코드 작성 원칙 및 보고 방식
- `docs/`: 아키텍처 설명, 결정 기록 및 연구 자료 (생성된 경우)

작업을 시작하기 전에 `spec.md`와 해당 기능의 상세 SPEC을 읽는다. 문서 간 충돌이나 미결정 사항이 발견되면 **임의로 해석하지 말고 사용자에게 확인한다.**

## 3. 기본 작업 규칙

1. `spec.md`의 **In Scope / Post-MVP / Out of Scope** 구분을 준수한다.
2. 기능 구현은 가능한 한 하나의 기능 SPEC 단위로 진행한다.
3. 구현 전에 기존 코드, 관련 명세, 제약 조건 및 Open Questions를 확인한다.
4. 요구사항이 불명확하거나 미확정 정책이 구현에 영향을 준다면 추측하지 않고 질문한다.
5. **사용자의 승인 전에는 코드나 설정 파일을 생성·수정하지 않는다.** 먼저 계획, 대안, 변경 대상 파일을 제안한다.
6. 새로운 의존성이나 아키텍처 변경이 필요하면 도입 이유, 대안 및 영향을 설명한다.
7. 실제 필요성이 확인되지 않은 Redis, Kafka, Microservices 등의 인프라를 임의로 도입하지 않는다.
8. Controller는 HTTP 요청·응답을 담당하고, 핵심 비즈니스 규칙은 Service에서 처리한다.
9. Controller가 Repository를 직접 호출하지 않으며, Entity를 API 응답으로 직접 반환하지 않는다.
10. 권한 검증과 허용 가능한 상태 전이 검증은 Backend에서 수행한다.
11. 일관성이 필요한 데이터 변경은 Transaction으로 처리한다.
12. Acceptance Criteria를 기준으로 정상·실패 시나리오를 테스트한다.
13. 실행하지 않은 테스트를 성공했다고 보고하지 않는다. 실행 불가 시 이유를 명시한다.
14. 비밀번호, API Key, 실제 `.env` 파일 등의 비밀정보를 Git에 포함하지 않는다.

## 4. 개발 작업 절차

1. **명세 확인:** `spec.md` 및 관련 `specs/*.md` 읽기
2. **현황 파악:** 관련 코드, 의존성, 설정, 테스트 확인
3. **질문 정리:** Open Questions와 모호한 정책 식별
4. **계획 제안:** 구현 범위, 접근 방식, 대안, 변경 예정 파일, 검증 방법 제시
5. **승인 대기:** 사용자가 승인하기 전에는 파일을 변경하지 않음
6. **구현:** 승인된 범위만 작업
7. **검증:** 관련 빌드·테스트·수동 확인 수행
8. **결과 보고:** 변경 파일, 실행 명령어, 실제 결과, 실패·미해결 사항 정리
9. **문서 갱신 제안:** 실제 구현과 SPEC 사이의 차이 또는 새로 결정된 사항을 보고

## 5. 기술 스택

6주차 프로젝트 초기화에서 확정·설치한 버전은 다음과 같다. 상세 버전, 선택 이유 및 대안은 `spec.md` §11과 [`docs/adr/0001-technology-stack.md`](./docs/adr/0001-technology-stack.md)를 기준으로 한다.

- **Frontend:** Node.js 22, npm, Next.js 16.4 (App Router), React 19.3, TypeScript 5.9, Tailwind CSS 4, ESLint CLI, Vitest
- **Backend:** Java 21, Spring Boot 4.1.1, Gradle 9.8 (Groovy DSL), Spring Data JPA, Flyway
- **Database:** PostgreSQL 16 (로컬은 Docker Compose)
- **AI (Post-MVP):** Python, FastAPI, LLM API — 아직 설치하지 않음

- 패키지 구조는 `spec.md` §11.3과 [`docs/adr/0002-package-structure.md`](./docs/adr/0002-package-structure.md)를 따른다.
- 실행·검증 명령어는 `spec.md` §15와 `README.md`를 따른다.
- 버전 변경이나 새 의존성 추가는 이유와 대안을 제안하고 사용자 승인 후 ADR에 기록한다.
- 인증(Session), 파일 저장소(로컬 파일시스템), Entity ID(BIGINT IDENTITY)는 **잠정 결정(Proposed)** 이다. 해당 기능 구현 전에 재검토한다.

## 6. 현재 개발 단계 — 6주차

현재 목표는 **AI Agent 활용 개발환경 구축 및 `spec.md` 기반 프로젝트 기본 아키텍처 생성**이다.

### 이번 단계의 작업 범위

- Frontend / Backend / Database 기본 구조 제안 및 설계 검토
- Claude Code 웹과 GitHub 저장소를 이용한 개발 흐름 구축
- 승인된 설계에 따른 프로젝트 초기화
- 기본 빌드, 실행 및 PostgreSQL 연결 검증
- README 및 아키텍처 결정 기록 작성
- 검증 결과를 바탕으로 `spec.md` Draft v0.2 수정 사항 제안

### 이번 단계에서 구현하지 않을 것

- 문서 등록, 검토 요청, 피드백 관리 등 전체 비즈니스 기능
- AI Version Summary, RAG, AI Review Agent
- 실시간 공동 편집, Office 문서 자체 편집, 자동 문서 병합
- 근거 없이 추가하는 Redis, Kafka, Microservices 등

**첫 번째 요청에서는 아키텍처 제안만 수행하고 파일을 생성하거나 수정하지 않는다.** 이후 사용자의 명시적 승인에 따라 초기 프로젝트를 생성한다.

## 7. 검증 및 완료 보고 원칙

- 빌드·테스트 성공 여부는 실제 실행 결과를 기준으로 판단한다.
- 클라우드 환경에서 검증한 항목과 로컬 환경에서 검증한 항목을 구분한다.
- 웹 환경의 제약으로 Docker, PostgreSQL 또는 로컬 실행을 확인하지 못했다면 미검증 상태로 보고한다.
- 구현 완료 여부는 관련 SPEC의 Acceptance Criteria를 기준으로 판단한다.
- 테스트 실패, 환경 문제, 남은 Open Questions를 숨기지 않는다.

## 8. 응답 및 코드 작성 규칙

- 사용자에게 제공하는 설명, 설계안, 작업 보고는 **한국어**로 작성한다.
- 코드의 변수명, 함수명, 클래스명, API 경로 등은 일관된 **영어** 명명 규칙을 사용한다.
- 중요한 기술적 결정에는 **선택 이유와 고려한 대안**을 함께 설명한다.
- 프로젝트에 존재하지 않는 파일, 명령어, 테스트 결과를 존재하는 것처럼 설명하지 않는다.
- 불확실한 내용은 추측으로 확정하지 않고 **미결정 사항**으로 표시한다.

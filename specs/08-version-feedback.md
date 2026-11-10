# SPEC-08 버전별 Feedback 작성·조회

- Issue: #25 · 브랜치: `feat/25/version-feedback` (선행 `feat/24`)
- 상태: Draft v0.1 (2026-11-10) — Backend 구현·자동 테스트 완료, 피드백 패널은 #28
- 대체: v0.4 SPEC-07 Feedback 등록 (SPEC-08·09 상태 전환은 폐기, `spec.md` §9.1)

## 1. 목적

모든 프로젝트 구성원이 문서의 특정 버전에 의견을 남기고, 버전별로 다른 구성원의 의견을 확인한다(US-02). 새 버전이 올라와도 이전 버전의 피드백은 그대로 남는다.

## 2. 사용자 시나리오

1. 구성원 A가 v1을 보고 피드백을 남긴다.
2. 구성원 B가 v2를 올린다. v1의 피드백은 v1에 그대로 보인다.
3. 구성원들이 v2에 피드백을 남긴다. v1과 v2의 피드백 목록은 서로 독립적이다.
4. 오너가 최종 확정하면 피드백 작성은 막히고 기존 피드백은 계속 조회된다.

## 3. 기능 요구사항

| ID | 요구사항 |
| --- | --- |
| FR-1 | 피드백은 하나의 DocumentVersion에 속한다: 작성자, 내용, 작성 시각 |
| FR-2 | 내용은 앞뒤 공백을 제거한 뒤 1~2,000자. 줄바꿈 허용. 서버는 원문 그대로 저장하고 화면은 텍스트로만 렌더링(HTML 해석 금지) |
| FR-3 | 작성은 하나의 트랜잭션: 문서 행 `FOR UPDATE` 잠금 → ACTIVE 확인 → 버전 확인 → 저장 → 활동 이력 `FEEDBACK_CREATED`. 최종 확정(SPEC-07)과 직렬화 |
| FR-4 | 조회는 버전 단위, 작성 순(`id ASC`). 문서 상태와 관계없이 가능 |
| FR-5 | 수정·삭제·답글·상태(OPEN/APPLIED/CONFIRMED)·좌표는 없다 (`spec.md` §4.2, §16.2 미결정) |

## 4. 권한 정책

| 행동 | 오너 | 구성원 | 비구성원 | 비로그인 |
| --- | --- | --- | --- | --- |
| 작성 (ACTIVE) | 허용 | 허용 | 404 `PROJECT_NOT_FOUND` | 401 |
| 작성 (FINALIZED) | 409 | 409 | 404 | 401 |
| 조회 | 허용 | 허용 | 404 | 401 |

## 5. 데이터 모델 (`V8__create_feedbacks.sql`)

| 컬럼 | 타입 |
| --- | --- |
| id | BIGINT IDENTITY PK |
| document_version_id | BIGINT NOT NULL FK document_versions RESTRICT |
| author_id | BIGINT NOT NULL FK users RESTRICT |
| content | VARCHAR(2000) NOT NULL, CHECK 공백 아님 |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() |

인덱스 `(document_version_id, id)`.

## 6. API

- `POST /api/projects/{projectId}/documents/{documentId}/versions/{versionNumber}/feedbacks` (CSRF) — 본문 `{ "content": "..." }` → 201 `FeedbackResponse`
- `GET  …/versions/{versionNumber}/feedbacks` → 200 `FeedbackResponse[]`
- `FeedbackResponse`: `id, versionNumber, author{id, displayName}, content, createdAt`

## 7. 예외 상황

| 상황 | 응답 |
| --- | --- |
| 내용 누락·공백·2,000자 초과 | 400 `VALIDATION_FAILED` |
| 문서 없음·다른 프로젝트 문서 | 404 `DOCUMENT_NOT_FOUND` |
| 이 문서에 없는 버전 번호 | 404 `DOCUMENT_VERSION_NOT_FOUND` |
| FINALIZED 문서에 작성 | 409 `DOCUMENT_ALREADY_FINALIZED` |
| CSRF 누락 | 403 `CSRF_INVALID` |

## 8. Acceptance Criteria

- AC-FB-01 오너·구성원이 각 버전에 피드백을 작성하면 201, 작성자·내용·시각이 저장·응답된다.
- AC-FB-02 버전별 목록은 서로 독립적이며 작성 순이다.
- AC-FB-03 새 버전 등록 후에도 이전 버전 피드백이 유지된다.
- AC-FB-04 비구성원 404, 비로그인 401, CSRF 누락 403, 변화 없음.
- AC-FB-05 잘못된 내용 400, 다른 문서의 버전·없는 문서 404.
- AC-FB-06 FINALIZED 이후 작성 409, 기존 피드백 조회 가능.
- AC-FB-07 작성 시 `FEEDBACK_CREATED`(target=FEEDBACK, version_number) 기록.
- AC-FB-08 최종 확정과 동시에 진행된 피드백은 확정 전에 커밋되었거나 409이며, 확정 이후 생성된 피드백이 없다.
- AC-FB-09 HTML·스크립트 문자열은 그대로 저장·반환된다(서버는 변환하지 않고, 화면은 텍스트로 표시).

## 9. 필수 테스트

통합(MockMvc + PostgreSQL): AC-FB-01~09. 스키마 CHECK.

## 10. 선행 SPEC 및 의존성

- SPEC-05 버전 조회(버전 번호 경로), SPEC-06 활동 이력, SPEC-07 상태·잠금 규칙.
- Frontend 피드백 패널은 #28.

## 11. 검증 기록

| 일자 | 환경 | 명령 | 결과 |
| --- | --- | --- | --- |
| 2026-11-10 | Claude Code 클라우드(JDK 21 init script, PostgreSQL 16) | `./gradlew test --tests 'com.docflow.feedback.*'` | 6/6 통과 (AC-FB-01~09) |
| 2026-11-10 | 동일 | `./gradlew test` | 381 통과, 0 실패 |
| — | GitHub Actions | | 미확인 |

구현 메모: 길이는 코드 포인트 기준(이모지 2,000자 허용)으로 PostgreSQL `VARCHAR(2000)`과 같게 판단한다.

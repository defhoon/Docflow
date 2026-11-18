# SPEC-09 문서 설명 및 버전별 변경사항 (Document Description & Version Notes)

- Issue: #49 · 브랜치: `feat/49/document-metadata` (기준 `main` `5a8355a`)
- 상태: **구현 완료, 리뷰 대기** (PR 병합·운영 배포 전)
- 관련: SPEC-03(문서 등록), SPEC-04(새 버전), SPEC-05(버전 히스토리), SPEC-06(활동 이력), SPEC-07(최종 확정)

## 1. 배경 & 목적

파일을 열기 전에는 문서의 목적을 알기 어렵고, 새 버전이 올라와도 무엇이 바뀌었는지 확인하기 어렵다. 작성자가 문서 설명과 버전별 정보를 직접 적고, 프로젝트 구성원이 파일을 열지 않고 확인하게 한다.

## 2. 범위

**In Scope**

- 문서 설명(Document 단위): 등록 시 선택 입력, 최대 2,000자. ACTIVE에서 구성원이 수정. FINALIZED에서 읽기 전용.
- 버전 정보(DocumentVersion 단위): 업로드 시 선택 입력, 최대 1,000자. v1은 "초기 버전 내용", v2 이상은 직전 버전 대비 "변경사항"으로 표시. 등록 후 수정하지 않는다.
- 조회: 문서 상세·목록(설명), 버전 목록·최신 버전(버전 정보).

**Out of Scope**

- 버전 정보 수정·삭제, 설명 변경 이력(본문 버전 관리).
- AI 요약(§10 확장 원칙만 정한다): AI API, 테이블, LLM 호출, 비동기 작업.
- 기존 권한·상태 정책 변경(§5).

## 3. 기능 요구사항

| ID | 요구사항 |
| --- | --- |
| FR-1 | `documents.description`: NULL 허용, 최대 2,000자(기존 1,000자에서 확대). 줄바꿈·탭 허용, 그 밖의 제어 문자 거부(기존 `ProjectText.isAllowedDescription`) |
| FR-2 | 입력 정규화: 앞뒤 공백(NBSP 포함) 제거. 빈 값·공백뿐이면 `null`(설명 없음). 길이는 정규화 후 UTF-16 code unit 기준(기존 설명 `@Size`와 같음) |
| FR-3 | 등록(`POST …/documents`, multipart)은 기존 `description` 파트와 새 `versionNote` 파트(v1 초기 버전 내용)를 선택으로 받는다 |
| FR-4 | 설명 변경(`PATCH …/documents/{documentId}`, JSON)은 하나의 트랜잭션: 접근 확인 → 권한 → 문서 행 `FOR UPDATE` 잠금 → ACTIVE 확인 → 값이 다르면 저장·`updated_at` 갱신·활동 이력 |
| FR-5 | 설명 변경 활동 이력: `DOCUMENT_DESCRIPTION_UPDATED`, 대상 `DOCUMENT`, `version_number` NULL. 값이 같으면 저장·이력 없음 |
| FR-6 | `document_versions.version_note`: NULL 허용, 최대 1,000자, 정규화·허용 문자는 FR-1·FR-2와 같음 |
| FR-7 | 새 버전(`POST …/versions`, multipart)은 `versionNote` 파트(직전 버전 대비 변경사항)를 선택으로 받는다. 버전 번호·비교 기준은 기존 서버 정책(문서 행 잠금 후 `MAX+1`, 직전 = 번호 −1)을 따른다 |
| FR-8 | 버전 정보는 업로드할 때 한 번 저장하고 수정하지 않는다(`updatable = false`). 각 버전의 정보는 독립적이다 |
| FR-9 | 응답에 추가: `DocumentResponse.latestVersion.versionNote`, `DocumentVersionResponse.versionNote`, `DocumentSummaryResponse.description`. 값이 없으면 `null` |
| FR-10 | 화면은 설명·버전 정보를 텍스트로만 표시한다(HTML 해석 없음, 줄바꿈 유지). 목록의 설명은 한 줄로 줄여 표시한다 |

## 4. 데이터 모델 (`V10__document_description_and_version_notes.sql`)

| 변경 | 내용 | 기존 데이터 영향 |
| --- | --- | --- |
| `documents.description` | `VARCHAR(1000)` → `VARCHAR(2000)` | 없음(PostgreSQL VARCHAR 길이 확대는 메타데이터만 변경) |
| `document_versions.version_note` | `VARCHAR(1000)` NULL 추가, 기본값 없음 | 없음(기존 버전은 NULL = 정보 없음) |
| `audit_logs.ck_audit_logs_action` | `DOCUMENT_DESCRIPTION_UPDATED` 추가(제약 삭제 후 재생성) | 없음(기존 행은 새 제약을 만족) |

기존 마이그레이션 V1~V9는 수정하지 않았다. FINALIZED 불변 트리거(V7)는 상태·확정 열만 막으므로 설명 변경 차단은 애플리케이션(FR-4)이 담당한다.

## 5. 권한·상태 정책 (기존 정책 유지)

| 행동 | OWNER | MEMBER | 비구성원 | 비로그인 |
| --- | --- | --- | --- | --- |
| 등록 시 설명·초기 버전 내용 | 허용 | 허용 | 404 `PROJECT_NOT_FOUND` | 401 |
| 새 버전 변경사항 (ACTIVE) | 허용 | 허용 | 404 | 401 |
| 설명 변경 (ACTIVE) | 허용 | 허용 | 404 | 401 |
| 설명 변경 (FINALIZED) | 409 `DOCUMENT_ALREADY_FINALIZED` | 409 | 404 | 401 |
| 조회 (상태 무관) | 허용 | 허용 | 404 | 401 |

- 설명 변경 권한은 문서·버전 등록 권한과 같다(OWNER·MEMBER, 작성자가 아니어도 됨). `ProjectPermissionService.requireDocumentEditor`가 `requireVersionUploader`에 위임한다. 기존 권한 구조와 충돌하지 않아 기본안을 그대로 채택했다.
- 유지: 구성원의 업로드·피드백, OWNER만 확정, 과거 버전 확정 가능, FINALIZED 영구, FINALIZED 이후 새 버전·피드백·설명 변경 금지, 조회·다운로드 가능.

## 6. API

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `POST /api/projects/{projectId}/documents` | multipart: `title`, `description`(선택), `versionNote`(선택), `file` | 201 `DocumentResponse` |
| `POST /api/projects/{projectId}/documents/{documentId}/versions` | multipart: `file`, `versionNote`(선택) | 201 `DocumentResponse` |
| `PATCH /api/projects/{projectId}/documents/{documentId}` (신규, CSRF) | JSON `{ "description": string \| null }`. **키는 필수**, 값 `null`·빈 값이면 설명 삭제. 그 밖의 필드는 무시 | 200 `DocumentResponse` |
| `GET …/documents`, `GET …/documents/{id}`, `GET …/versions` | — | 필드 추가(FR-9) |

하위 호환: 새 필드는 모두 선택 입력이며, 기존 클라이언트가 보내지 않으면 `null`로 저장된다. 응답은 필드 추가만 있다.

## 7. 실패 시나리오

| 상황 | 응답 | 저장 |
| --- | --- | --- |
| 설명 2,001자 이상, 버전 정보 1,001자 이상 | 400 `VALIDATION_FAILED` + `fieldErrors[field]` | 없음(파일도 저장 전 거부) |
| 허용하지 않는 제어 문자 | 400 | 없음 |
| PATCH 본문에 `description` 키 없음(예: `{}`, `{"status":"ACTIVE"}`) | 400 | 없음 |
| PATCH 본문이 JSON이 아님 | 415 | 없음 |
| FINALIZED 문서 설명 변경 | 409 `DOCUMENT_ALREADY_FINALIZED` | 없음 |
| 다른 프로젝트 문서·없는 문서 | 404 `DOCUMENT_NOT_FOUND` | 없음 |
| 업로드 파일 검증 실패(버전 정보 포함) | 400 | 문서·버전·파일 없음(기존 보상 삭제 규칙) |
| CSRF 누락 | 403 | 없음 |

### 결정

| ID | 결정 | 이유·대안 |
| --- | --- | --- |
| M1 | 설명 컬럼은 기존 `documents.description`을 2,000자로 확대한다 | 새 컬럼을 만들면 같은 의미의 값이 둘이 된다. 기존 값은 그대로 유효하다 |
| M2 | 버전 정보 컬럼 이름 `version_note`(JSON `versionNote`), 단일 필드 | v1/v2 이상의 의미 차이는 화면 문구(FR-10)로 구분. 대안(두 컬럼)은 같은 성격의 값을 나눌 이유가 없다 |
| M3 | 설명 변경은 `PATCH /documents/{id}` + JSON, 키 필수 | 기존 등록·업로드는 multipart를 유지하고 선택 파트만 추가(하위 호환). 키를 선택으로 두면 실수로 설명이 지워진다 |
| M4 | 동시 수정은 문서 행 잠금 순서대로 적용, 나중 요청이 남는다(마지막 저장 우선) | 소규모 협업에서 충분. 낙관적 잠금(버전 비교)은 필요 시 확장 |
| M5 | 값이 같은 변경은 저장·활동 이력을 남기지 않는다 | 이력 잡음 방지 |
| M6 | 설명 변경 활동 이력에는 설명 본문을 저장하지 않는다 | 기존 이력은 행동·대상만 기록한다(SPEC-06 FR-3) |

## 8. Acceptance Criteria

- AC-META-01 등록 시 설명·초기 버전 내용이 저장되고 상세·목록·버전 목록에서 조회된다(줄바꿈 유지, 앞뒤 공백 제거).
- AC-META-02 선택 입력을 보내지 않거나 공백뿐이면 `null`로 저장되고 등록·업로드는 정상이다.
- AC-META-03 v2 이상의 변경사항이 저장·조회된다.
- AC-META-04 버전 정보는 버전마다 독립적이다.
- AC-META-05 설명 2,000자·버전 정보 1,000자는 허용, 1자 초과는 400이며 아무것도 저장되지 않는다.
- AC-META-06 허용하지 않는 제어 문자는 400.
- AC-META-07 OWNER·MEMBER가 설명을 수정하면 활동 이력이 남는다. 같은 값이면 남지 않는다. 빈 값·`null`이면 설명이 삭제된다. 키가 없으면 400.
- AC-META-08 비구성원 404, 비로그인 401, CSRF 누락 403, 다른 프로젝트 문서 404 — 모두 변화 없음.
- AC-META-09 FINALIZED 이후 설명 수정은 409, 기존 설명·버전 정보·다운로드는 계속 조회된다.
- AC-META-10 업로드 실패 시 버전 정보를 포함해 아무것도 저장되지 않는다.
- AC-META-11 SPEC-09 이전 데이터(설명·버전 정보 NULL)와 기존 요청 형식이 그대로 동작한다.
- AC-META-12 스키마: 설명 VARCHAR(2000), 버전 정보 VARCHAR(1000) NULL.
- AC-META-13 설명 PATCH로 상태·확정 정보·작성자를 바꿀 수 없다(보안 회귀).
- AC-META-UI-01~04 등록 폼(설명·초기 버전 내용, 글자 수, 실패 시 유지), 새 버전 폼(변경사항, 성공 시 비움·실패 시 유지), 상세(설명 표시·수정·빈 상태·FINALIZED 읽기 전용·중복 제출 방지·409 처리), 버전 히스토리(v1/v2 문구 구분·빈 상태).

## 9. 필수 테스트

- 통합(MockMvc + PostgreSQL): `DocumentMetadataIntegrationTest`(AC-META-01~12), `AccessControlRegressionIntegrationTest#descriptionPatchCannotChangeStatus`(AC-META-13).
- 기존 회귀: 응답 필드 수 단언(내부 정보 비노출)을 새 필드에 맞게 1씩 늘리고 새 필드 이름을 명시 확인.
- Frontend(Vitest): `DocumentMetadata.test.tsx`(UI-01~04), `api.test.ts`, `validation.test.ts`.

## 10. 향후 AI 확장 원칙 (이번에 구현하지 않음)

- 사용자 작성 정보(`description`, `version_note`)와 AI 요약은 별도 저장소(예: `document_version_summaries`)에 둔다. AI 결과가 사용자 작성 값을 덮어쓰지 않는다.
- AI 요약은 업로드 트랜잭션 밖에서 비동기로 만들어, AI 실패가 업로드 실패로 이어지지 않게 한다.
- 요약 레코드에 비교 기준 버전(v1: 없음, v2 이상: 직전 버전 번호), 생성 상태(대기·완료·실패), 모델·생성 시각을 둔다.
- 화면은 "작성자 작성"과 "AI 요약"을 구분해 표시한다.

## 11. 검증 기록

| 일자 | 환경 | 명령 | 결과 |
| --- | --- | --- | --- |
| 2026-11-18 | Claude Code 클라우드(JDK 21 toolchain 대체, PostgreSQL 16 로컬) | `./gradlew test --tests 'com.docflow.document.*IntegrationTest'` | 통과(`DocumentMetadataIntegrationTest` 11개 포함) |
| 2026-11-18 | 동일 | `./gradlew test` | PR 본문에 기록 |
| 2026-11-18 | 동일 | `npm run lint`, `npx vitest run`, `npm run build` | PR 본문에 기록 |
| — | GitHub Actions | PR CI | PR에서 확인 |
| — | Railway | V10 마이그레이션 적용 | **미실행**(배포 승인 대기) |

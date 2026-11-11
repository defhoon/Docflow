# SPEC-07 문서 협업 상태와 최종 버전 확정

- Issue: #24 · 브랜치: `feat/24/document-finalize` (선행 `feat/23`)
- 상태: Draft v0.1 (2026-11-10) — Backend 구현·자동 테스트 완료, 확정 Dialog·읽기 전용 화면은 #28
- 대체: v0.4 SPEC-12 승인, SPEC-06/10/11의 상태 전이 (`spec.md` §9.1)

## 1. 목적

프로젝트 오너가 문서의 버전 하나(최신이 아니어도 됨)를 공식 최종본으로 확정하고, 그 문서의 협업을 영구 종료(FINALIZED, 읽기 전용)한다(US-03).

## 2. 사용자 시나리오

1. 구성원들이 v1~v3을 올리고 피드백을 남긴다(ACTIVE).
2. 오너가 v2를 선택하고 확인 Dialog에서 확정한다 → FINALIZED, 최종 버전 v2, 확정자·시각 저장, 활동 이력 `DOCUMENT_FINALIZED`.
3. 이후 누구도 새 버전·피드백·재확정을 할 수 없고, 모든 버전 조회·다운로드·미리보기와 피드백 조회는 계속 가능하다.
4. 수정이 필요하면 새 문서를 등록한다(재개 기능 없음).

## 3. 기능 요구사항

| ID | 요구사항 |
| --- | --- |
| FR-1 | 상태는 `ACTIVE`, `FINALIZED`. 허용 전이는 `ACTIVE → FINALIZED` 하나 |
| FR-2 | 문서 등록 시 `ACTIVE` |
| FR-3 | 확정 요청 본문 `{ "versionNumber": N }`. 해당 문서의 버전만 선택 가능 |
| FR-4 | 확정은 하나의 트랜잭션: 문서 행 `FOR UPDATE` 잠금 → ACTIVE 확인 → 버전 확인 → 상태·`final_version_id`·`finalized_by`·`finalized_at` 저장 → 활동 이력 기록 |
| FR-5 | 새 버전 등록(SPEC-04)과 피드백 작성(SPEC-08)도 같은 문서 행을 잠근 뒤 ACTIVE를 재확인한다. 따라서 확정과 직렬화되며, 확정 커밋 이후 들어온 업로드·피드백은 409 |
| FR-6 | FINALIZED 문서: 새 버전 409 `DOCUMENT_STATUS_CONFLICT`, 재확정 409 `DOCUMENT_ALREADY_FINALIZED`. 조회·다운로드·미리보기 유지 |
| FR-7 | 확정 정보는 문서 상세 응답 `finalization`(versionNumber, finalizedBy{id, displayName}, finalizedAt)과 목록 `finalVersionNumber`로 제공 |
| FR-8 | 상태를 바꾸는 다른 API는 없다. DB 트리거가 FINALIZED 문서의 상태·확정 컬럼 변경을 거부한다(마지막 방어선) |
| FR-9 | 확정 버전·다른 버전·피드백·이력은 삭제하지 않는다 |

## 4. 권한 정책

| 행동 | 오너 | 구성원 | 비구성원 | 비로그인 |
| --- | --- | --- | --- | --- |
| 최종 확정 | 허용 | 403 `ACCESS_DENIED` | 404 `PROJECT_NOT_FOUND` | 401 |
| 확정 정보 조회 | 허용 | 허용 | 404 | 401 |

## 5. 데이터 모델 (`V7__document_finalization.sql`)

- 기존 상태 마이그레이션: `DRAFT`, `IN_REVIEW`, `CHANGES_REQUESTED`, `RE_REVIEW` → `ACTIVE`. 이 상태들은 모두 "협업 중"이며 SPEC-04까지 상태 전이 기능이 없어 실제로는 `DRAFT`만 존재한다.
- `APPROVED` 행이 있으면 마이그레이션을 **중단**한다(예외). 애플리케이션은 APPROVED를 만든 적이 없고, 확정 버전·확정자 정보가 없는 행을 FINALIZED로 임의 변환하면 데이터를 지어내게 되기 때문이다. 발견 시 수동 판단 후 재실행한다.
- `documents`: `status` CHECK(`ACTIVE`,`FINALIZED`), 기본값 `ACTIVE`; `final_version_id`, `finalized_by`(FK users), `finalized_at` 추가.
- CHECK: ACTIVE이면 세 컬럼 모두 NULL, FINALIZED이면 모두 NOT NULL.
- `document_versions`에 `UNIQUE (id, document_id)`를 두고 `documents (final_version_id, id)` → `document_versions (id, document_id)` 복합 FK로 "확정 버전은 같은 문서의 버전"을 DB에서도 보장.
- 트리거 `trg_documents_finalized_immutable`: 이전 값이 FINALIZED이면 status·final_version_id·finalized_by·finalized_at 변경 거부.

## 6. API

`POST /api/projects/{projectId}/documents/{documentId}/finalize` (CSRF 필요)

- 요청: `{ "versionNumber": 2 }` (1 이상 정수, 필수)
- 200: `DocumentResponse` (status `FINALIZED`, `finalization` 포함)

## 7. 예외 상황

| 상황 | 응답 |
| --- | --- |
| versionNumber 누락·0 이하·숫자 아님 | 400 `VALIDATION_FAILED` / `BAD_REQUEST` |
| 문서 없음·다른 프로젝트 문서 | 404 `DOCUMENT_NOT_FOUND` |
| 다른 문서의 버전 번호·없는 번호 | 404 `DOCUMENT_VERSION_NOT_FOUND` |
| 일반 구성원 | 403 `ACCESS_DENIED` |
| 이미 FINALIZED(중복·동시 확정 포함) | 409 `DOCUMENT_ALREADY_FINALIZED` |
| FINALIZED 문서에 새 버전 | 409 `DOCUMENT_STATUS_CONFLICT` |
| CSRF 누락 | 403 `CSRF_INVALID` |

## 8. Acceptance Criteria

- AC-FIN-01 오너가 최신 버전을 확정하면 FINALIZED, 확정 정보 저장, 응답에 포함.
- AC-FIN-02 오너가 과거 버전(최신 아님)을 확정할 수 있다.
- AC-FIN-03 일반 구성원 403, 비구성원 404, 비로그인 401, 변화 없음.
- AC-FIN-04 다른 문서의 버전·없는 버전 404, 변화 없음.
- AC-FIN-05 이미 확정된 문서의 재확정(같은·다른 버전) 409, 확정 정보 불변.
- AC-FIN-06 동시 확정 요청 중 정확히 하나만 성공하고 활동 이력은 1건.
- AC-FIN-07 FINALIZED 이후 새 버전 409, 파일·행 남지 않음.
- AC-FIN-08 FINALIZED 이후 버전 목록·다운로드 가능.
- AC-FIN-09 FINALIZED → ACTIVE 전환 API가 없고, DB 직접 변경도 트리거가 거부.
- AC-FIN-10 확정과 동시에 진행된 업로드는 확정 전에 커밋되었거나 409이며, 확정 이후 생성된 버전이 없다.
- AC-FIN-11 확정 시 `DOCUMENT_FINALIZED`(target=DOCUMENT, version_number=확정 버전) 기록.
- AC-FIN-12 기존 상태 마이그레이션: 협업 상태 → ACTIVE, APPROVED 존재 시 중단.

## 9. 필수 테스트

통합(MockMvc + PostgreSQL): AC-FIN-01~11. 동시성: 병렬 확정 경쟁, 업로드 대기 중 확정 커밋, 병렬 확정·업로드 불변식. 마이그레이션: AC-FIN-12(SQL 단위 검증). 도메인 단위: 상태 규칙.

## 10. 선행 SPEC 및 의존성

- SPEC-02 `requireOwner`, SPEC-04 문서 행 잠금, SPEC-05 조회 API, SPEC-06 활동 이력.
- SPEC-04 영향: 업로드 허용 상태가 `DRAFT·CHANGES_REQUESTED`에서 `ACTIVE`로 바뀐다(SPEC-04 AC-VER-05·06은 FINALIZED 기준으로 갱신).
- 후속: SPEC-08 피드백이 같은 잠금·상태 규칙을 사용. Frontend 확정 Dialog·읽기 전용 화면은 #28.

## 11. 검증 기록

| 일자 | 환경 | 명령 | 결과 |
| --- | --- | --- | --- |
| 2026-11-10 | Claude Code 클라우드(JDK 21 init script, PostgreSQL 16) | `./gradlew test --tests '*DocumentFinalizeIntegrationTest' --tests '*DocumentStatusMigrationIntegrationTest'` | 10/10 통과 (AC-FIN-01~12) |
| 2026-11-10 | 동일 | `./gradlew test` | 375 통과, 0 실패 |
| 2026-11-10 | 동일 | Frontend `tsc`, `lint`, `vitest run`, `build` | 통과 (Vitest 225: 이전 상태별 매개변수 케이스 5개 → ACTIVE/FINALIZED 2개로 통합) |
| — | GitHub Actions | | 미확인 |

구현 메모

- 기존 테스트 갱신(삭제 아님): SPEC-04 AC-VER-05·06과 SPEC-05 AC-VDL-06의 상태 매개변수를 ACTIVE/FINALIZED 기준으로 바꿨고, 응답 필드 수 검증에 `finalization`·`finalVersionNumber`를 반영했다.
- `documents.final_version_id ↔ document_versions.document_id` 상호 참조 때문에 테스트 정리는 `TRUNCATE audit_logs, document_versions, documents`로 바꿨다. 운영에는 삭제 기능이 없다.
- PostgreSQL은 CHECK 제약을 이름 순으로 평가하므로 확정 정보 CHECK 이름을 `ck_documents_status_finalization`으로 두어 잘못된 상태 값은 `ck_documents_status`로 보고된다.

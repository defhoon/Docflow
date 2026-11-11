# SPEC-06 활동 이력 (Audit Log)

- Issue: #23 · 브랜치: `feat/23/audit-log` (선행 `docs/22`, `feat/21`)
- 상태: **Done (2026-11-11, AC-AUD-01~09 충족)**. main 병합 완료
- 대체: v0.4 SPEC-13 Audit Log (`spec.md` §9.1)

## 1. 목적

누가 언제 문서를 등록하고, 새 버전을 올리고, 피드백을 남기고, 최종 확정했는지 프로젝트 구성원이 확인한다(US-04). 기록은 업무 변경과 같은 트랜잭션에서 저장해 "변경은 됐는데 이력이 없는" 상태를 만들지 않는다.

## 2. 사용자 시나리오

1. 구성원이 문서를 등록한다 → `DOCUMENT_CREATED` 1건이 v1과 함께 기록된다.
2. 구성원이 새 버전을 올린다 → `VERSION_CREATED`(버전 번호 포함).
3. (SPEC-08) 피드백 작성 → `FEEDBACK_CREATED`. (SPEC-07) 오너 최종 확정 → `DOCUMENT_FINALIZED`(확정 버전 번호 포함).
4. 구성원은 프로젝트·문서 활동 이력을, 로그인 사용자는 자신이 접근 가능한 모든 프로젝트의 최근 활동을 조회한다.

## 3. 기능 요구사항

| ID | 요구사항 |
| --- | --- |
| FR-1 | 공통 기록 컴포넌트 `AuditLogService.record`는 호출자의 트랜잭션 안에서만 동작한다(`Propagation.MANDATORY`). 트랜잭션 없이 호출하면 예외 |
| FR-2 | 업무 트랜잭션이 롤백되면 이력도 저장되지 않는다. 이력 저장이 실패하면 업무 변경도 롤백된다 |
| FR-3 | 기록 항목: 프로젝트, 행위자, 행동, 대상 유형·ID, 문서 ID(있으면), 버전 번호 스냅샷(있으면), 시각 |
| FR-4 | 행동 집합(공통 구조): `DOCUMENT_CREATED`, `VERSION_CREATED`, `FEEDBACK_CREATED`, `DOCUMENT_FINALIZED` |
| FR-5 | 이력은 수정·삭제 API가 없다(append-only). |
| FR-6 | 조회: 최신순(`id DESC`), `limit` 1~100(기본 20), 다음 페이지 커서 `beforeId` |
| FR-7 | 응답에 파일 저장 키, 원본 파일 경로, 이메일 등 내부·개인 정보는 포함하지 않는다(표시 이름만) |

## 4. 권한 정책

| API | 오너 | 구성원 | 비구성원 | 비로그인 |
| --- | --- | --- | --- | --- |
| 프로젝트 활동 | 허용 | 허용 | 404 `PROJECT_NOT_FOUND` | 401 |
| 문서 활동 | 허용 | 허용 | 404 | 401 |
| 내 최근 활동(전체) | 접근 가능한 프로젝트의 이력만 | 동일 | — | 401 |

구성원에서 제거되면 그 프로젝트 이력은 더 이상 보이지 않는다(현재 구성원 기준).

## 5. 데이터 모델 (`V6__create_audit_logs.sql`)

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| id | BIGINT IDENTITY PK | |
| project_id | BIGINT NOT NULL FK projects RESTRICT | |
| actor_id | BIGINT NOT NULL FK users RESTRICT | |
| action | VARCHAR(40) NOT NULL CHECK(FR-4) | |
| target_type | VARCHAR(30) NOT NULL CHECK(`DOCUMENT`,`DOCUMENT_VERSION`,`FEEDBACK`) | |
| target_id | BIGINT NOT NULL | |
| document_id | BIGINT NULL FK documents RESTRICT | |
| version_number | INT NULL | 기록 시점 스냅샷 |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | |

인덱스: `(project_id, id DESC)`, `(document_id, id DESC)`.

## 6. API

- `GET /api/activities?limit&beforeId` — 접근 가능한 모든 프로젝트
- `GET /api/projects/{projectId}/activities?limit&beforeId`
- `GET /api/projects/{projectId}/documents/{documentId}/activities?limit&beforeId`

응답 `ActivityResponse[]`: `id, action, actor{id, displayName}, project{id, name}, document{id, title}|null, versionNumber|null, createdAt`

## 7. 예외 상황

| 상황 | 응답 |
| --- | --- |
| `limit` 범위 밖·숫자 아님, `beforeId` < 1 | 400 `VALIDATION_FAILED` |
| 프로젝트 비구성원·없음 | 404 `PROJECT_NOT_FOUND` |
| 다른 프로젝트의 문서 ID | 404 `DOCUMENT_NOT_FOUND` |
| 이력 저장 실패 | 업무 요청 전체 실패(롤백, 파일 보상 삭제는 기존 규칙) |

## 8. Acceptance Criteria

- AC-AUD-01 문서 등록 성공 시 `DOCUMENT_CREATED`(document_id, target=DOCUMENT, version_number=1) 1건이 기록된다.
- AC-AUD-02 새 버전 등록 성공 시 `VERSION_CREATED`(target=DOCUMENT_VERSION, version_number=N) 1건이 기록된다.
- AC-AUD-03 업무 트랜잭션이 실패(예: 409)하면 이력이 남지 않는다.
- AC-AUD-04 이력 저장이 실패하면 업무 변경도 저장되지 않는다.
- AC-AUD-05 구성원·오너는 프로젝트·문서 이력을 최신순으로 조회한다. 비구성원 404, 비로그인 401.
- AC-AUD-06 `/api/activities`는 접근 가능한 프로젝트의 이력만 반환한다.
- AC-AUD-07 `limit`·`beforeId` 페이지가 겹치거나 빠지지 않는다. 잘못된 값은 400.
- AC-AUD-08 응답에 storageKey·이메일이 없다.
- AC-AUD-09 record를 트랜잭션 밖에서 호출하면 실패한다.

## 9. 필수 테스트

통합: AC-AUD-01~08(MockMvc + PostgreSQL), 단위: AC-AUD-09, 롤백(AC-AUD-03·04).

## 10. 선행 SPEC 및 의존성

- SPEC-01/02(접근 확인 `getAccessibleProject`), SPEC-03/04(기록 지점), SPEC-05 브랜치 기준.
- 후속: SPEC-07(`DOCUMENT_FINALIZED`), SPEC-08(`FEEDBACK_CREATED`)이 같은 컴포넌트를 사용한다.
- Frontend 표시(홈 최근 활동, 문서 상세 이력)는 #28 UI에서 구현한다.

## 11. 검증 기록

| 일자 | 환경 | 명령 | 결과 |
| --- | --- | --- | --- |
| 2026-11-10 | Claude Code 클라우드(JDK 21 init script, PostgreSQL 16) | `./gradlew test --tests 'com.docflow.audit.*'` | 7/7 통과 (AC-AUD-01~09) |
| 2026-11-10 | 동일 | `./gradlew test` | 373 통과, 0 실패 |
| — | GitHub Actions | push 후 PR에서 실행 | 미확인 (`feat/**` push CI 결과로 확인 예정) |

구현 메모: AC-AUD-04는 `AuditLogRepository.save` 실패를 주입해 문서·버전·파일이 남지 않음을 확인했다.

### Done 판정 (2026-11-11, Issue #41)

| DoD (`spec.md` §14) | 근거 |
| --- | --- |
| AC 전부 충족 | AC-AUD-01~09: 통합·단위 테스트(위 검증 기록) |
| 권한·상태 전이 | `AccessControlRegressionIntegrationTest`(#37): 비로그인 401·비구성원 404·구성원 확정 403·FINALIZED 변경 409 |
| Frontend 상태·연동 | 홈 최근 활동·프로젝트·문서 활동 이력 화면(#28), 클라우드 실제 브라우저 시나리오에서 활동 기록 확인 |
| 자동 테스트 | main 기준 Backend 419·Frontend 244 통과(클라우드), GitHub Actions main run 113 (`d50f18f`): Backend·Frontend·Docker 성공 |
| 수동 검증 | Windows 수동(사용자, 2026-11-11): `docs/windows-verification.md` §7.1 |


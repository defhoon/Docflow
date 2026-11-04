# SPEC-04: 문서 버전 등록

- 상태: **Draft v0.2 (2026-11-04) — 설계 결정 V1~V8 승인, 미결정 OQ-1~10 결정 반영. 사용자 검토 승인(2026-11-04). Issue #19·브랜치 생성. 구현 결정 I1~I7 승인(2026-11-04). 구현·클라우드·CI 검증 완료, Windows 자동 테스트·빌드 성공(2026-11-04, 사용자). AC-VER-18의 동시성·크기 테스트 반복 실행은 미보고**
- Issue: [#19](https://github.com/defhoon/Docflow/issues/19)
- 브랜치: `feat/19/document-version-create`
- 관련 문서: [`spec.md`](../spec.md) §3, §6.1, §7.1, §8 US-05, §9, §12, §13, §14, §16.2 / [`specs/03-document-create.md`](./03-document-create.md) §5.1~§5.7, §6, §7, §8, §12, §13, §16 / [`specs/02-project-members.md`](./02-project-members.md) §7.2(D5), §10(잠금 대기 기법) / [ADR-0001](../docs/adr/0001-technology-stack.md) Q2, [ADR-0002](../docs/adr/0002-package-structure.md), [ADR-0005](../docs/adr/0005-session-authentication.md) / [`CLAUDE.md`](../CLAUDE.md) §2~§6
- 작성일: 2026-11-04 (기준 커밋: `main` `a140a94`)
- 변경 이력:
  - v0.1 (2026-11-04): 설계 결정 V1~V8(사용자 승인)을 반영한 상세 명세 초안
  - Windows 검증 기록 (2026-11-04): 사용자가 보고한 Windows 자동 테스트·빌드 결과를 §17에 기록
  - 구현 (2026-11-04): Backend·Frontend 구현, 클라우드 테스트·E2E·CI 결과를 §17에 기록. 구현 중 확인한 사항(§13.2 잠금 SQL·`updated_at`·AC-VER-10 재현, §9.2 안내 문구, §11.5 테스트 컨텍스트 정리)을 반영. V1~V8·OQ-1~10·I1~I7 변경 없음
  - 구현 결정 (2026-11-04): 사용자가 구현 결정 I1~I7을 승인(§15.4). V1~V8·OQ-1~10 변경 없음
  - v0.2 승인 (2026-11-04): 사용자가 Draft v0.2 검토를 승인(V1~V8·OQ-1~10, AC 판정 규칙: AC-VER-01~16 클라우드·CI, AC-VER-17 클라우드 실제 브라우저 E2E, AC-VER-18 Windows 자동 테스트·빌드 필수, AC-VER-19 회귀·CI 필수). Issue #19, 브랜치 `feat/19/document-version-create` 생성
  - v0.2 (2026-11-04): OQ-1~10 결정(사용자 승인)을 반영. 응답 계약 유지(OQ-1), 409 본문에 상태 값 미포함(OQ-2), 잠금 후 구성원 재확인 없음(OQ-3), 잠금 대기 상한 없음·대기 시간 측정 기록(OQ-4), `DocumentVersionController` 신설(OQ-5), 문서 상세 화면 배치(OQ-6), 허용되지 않은 상태에서 UI 비활성화+안내(OQ-7), 네트워크 오류 안내 문구 확정·자동 재시도 없음(OQ-8), Windows 실제 브라우저 수동 E2E는 후속 Issue로 추적(OQ-9), `spec.md` §16.2에 APPROVED 잠정 거부 명시(OQ-10). 관련 API·Frontend·AC·테스트 갱신. V1~V8 변경 없음

표기: **[기존]** 이미 구현·확정된 내용(주로 SPEC-03), **[결정 Vn]**·**[결정 OQ-n]** 사용자가 확정한 설계 결정, **[제안]** 이 SPEC의 설계안(검토 대상), **[검증 필요]** 구현 단계에서 실제 실행으로 확인할 사항.

---

## 1. 목적 및 배경

- `spec.md` §9 SPEC-04 "Document Version 등록"이다.
- 근거:
  - US-05: "Author로서 수정된 문서를 새로운 버전으로 등록하면서 이전 버전을 유지하고 싶다."
  - §13 MVP AC: "기존 버전을 유지하면서 새로운 Version을 등록할 수 있다", "수정된 문서를 새로운 Version으로 등록할 수 있다."
  - §3 요구사항 추적: F-02·F-04 → 기존 문서를 유지하며 수정본 관리 → Document Version.
  - §6.1 Workflow: `CHANGES_REQUESTED` → 문서 수정 → **새 Version 등록** → 재검토 요청 → `RE_REVIEW`.
- [기존] SPEC-03이 `document_versions` 테이블, `FileStorage`, 파일 검증, 저장–DB 보상 규칙을 만들었고, 다음 버전 번호 할당·동시 등록·최신 버전 포인터·승인 문서의 새 버전 여부를 SPEC-04로 넘겼다(SPEC-03 §5.1, §7.3, §13).

## 2. 목표

- 프로젝트 구성원이 기존 문서에 새 파일 버전을 등록할 수 있다.
- 새 버전 번호를 동시 요청에서도 중복·누락 없이 할당한다.
- 기존 버전의 DB 행과 파일을 변경하지 않는다.
- SPEC-03의 파일 검증·크기 제한·저장소·보상 정책을 그대로 적용한다.
- 허용되지 않은 문서 상태에서는 등록을 거부한다.

## 3. 범위

### In Scope

| 항목 | 내용 |
| --- | --- |
| 새 버전 등록 API | `POST /api/projects/{projectId}/documents/{documentId}/versions` (multipart `file`) [결정 V5] |
| 버전 번호 | 문서 행 잠금 후 `MAX(version_number) + 1` [결정 V1] |
| 최신 버전 반영 | 기존 상세(`latestVersion`)·목록(`latestVersionNumber`) 조회가 새 버전을 반영. `documents.updated_at` 갱신 [결정 V2] |
| 권한 | OWNER·MEMBER, 별도 권한 메서드 [결정 V3] |
| 상태 정책 | `DRAFT`·`CHANGES_REQUESTED`만 허용, 그 외 `409 DOCUMENT_STATUS_CONFLICT` [결정 V4] |
| 재사용 | SPEC-03 파일 저장·검증·보상 로직을 공통 컴포넌트로 추출, `DocumentVersionService` 신설 [결정 V5] |
| Frontend | 문서 상세 화면의 새 버전 등록 폼, 사전 검사, 오류 표시, 중복 클릭 방지 [제안] |
| 테스트 | 단위, 실제 PostgreSQL 통합, 동시성, Frontend, 클라우드 Next.js 실제 브라우저 E2E, Windows 자동 테스트 [결정 V8] |

### Out of Scope [결정 V6, V7]

| 항목 | 담당 |
| --- | --- |
| 버전 목록, 버전 단건 조회, 파일 다운로드, 미리보기 | SPEC-05 |
| Review 요청·검토 대상 버전 지정 | SPEC-06 |
| Feedback과 버전 연결(`spec.md` §16.2 미결정) | SPEC-07~09 중 해당 SPEC에서 결정 |
| 재검토 요청과 상태 전이(`CHANGES_REQUESTED` → `RE_REVIEW` 등) | SPEC-11 (상태 전이 전반은 SPEC-06·10~12) |
| 승인 문서(`APPROVED`)의 새 버전 정책 재검토 | SPEC-12 |
| Audit Log 기록 | SPEC-13 (기록 지점만 §13에 남김) |
| 버전 삭제, 문서 수정·삭제 | 범위 밖 |
| 변경 메모(버전 설명) 필드 | 범위 밖 (SPEC-14 AI Version Summary 때 재검토 가능) |
| Idempotency Key, 자동 재시도 중복 방지 | 범위 밖 [결정 V7] |
| Windows 실제 브라우저 수동 E2E | 후속 검증으로 별도 관리, 구현 완료 시점에 후속 Issue로 추적(생성은 별도 승인) [결정 V8, OQ-9] (§11.7) |

## 4. 사용자 시나리오

### 4.1 새 버전 등록 (OWNER·MEMBER)

1. 구성원이 문서 상세 화면(`/projects/{projectId}/documents/{documentId}`)에서 "새 버전 등록" 영역의 파일을 선택하고 제출한다.
2. 서버가 파일을 검증·저장하고 v(N+1)을 만든다.
3. 화면은 같은 상세 화면에서 최신 버전 정보(버전 번호, 원본 파일명, 크기, 업로드 시각)를 갱신해 표시한다.
4. 프로젝트 상세의 문서 목록에서 최신 버전 번호가 갱신되어 보인다.

### 4.2 허용되지 않은 상태

- 문서가 `IN_REVIEW`·`RE_REVIEW`·`APPROVED`이면 등록이 `409`로 거부되고, 화면은 "현재 문서 상태에서는 새 버전을 등록할 수 없습니다."를 표시한다.
- [기존 사실] 현재는 상태 전이 기능이 없어 모든 문서가 `DRAFT`다. 이 시나리오는 SPEC-06 이후에 실제로 발생하며, SPEC-04에서는 테스트에서 상태를 직접 설정해 검증한다(§11.2).

### 4.3 구성원이 아닌 사용자

- 등록 요청은 `404 PROJECT_NOT_FOUND`이며 파일을 저장하지 않는다([기존] SPEC-03 404 은닉 정책).

### 4.4 같은 파일 재등록 [결정 V7]

- 직전 버전과 같은 파일(SHA-256 동일)을 다시 올려도 새 버전 번호로 등록된다. 경고·차단하지 않는다.
- 제출 중 버튼 비활성화로 한 화면에서의 중복 클릭만 막는다.

## 5. 도메인 및 데이터 모델

### 5.1 스키마 [기존, 변경 없음]

- **Flyway 마이그레이션을 추가하지 않는다.** [기존] V5의 `document_versions`를 그대로 사용한다.
- 사용하는 제약:
  - `uk_document_versions_document_version UNIQUE (document_id, version_number)` — 번호 중복 최종 방어선 [결정 V1]
  - `uk_document_versions_storage_key UNIQUE (storage_key)`
  - `ck_document_versions_version_positive (version_number >= 1)`
  - FK 모두 `RESTRICT`
- `latest_version_id` 같은 포인터 컬럼을 추가하지 않는다 [결정 V2].

### 5.2 버전 번호 [결정 V1]

- 새 버전 번호 = (잠금 획득 후 조회한 해당 문서의 `MAX(version_number)`) + 1.
- 문서는 항상 v1을 가지므로(SPEC-03) 첫 새 버전은 v2다.
- 번호는 문서별로 연속이며, 삭제 기능이 없으므로 빈 번호가 생기지 않는다. 다만 롤백된 시도는 번호를 소비하지 않는다(행이 생기지 않으므로).

### 5.3 최신 버전 [결정 V2]

- [기존] 목록은 `MAX(version_number)` 서브쿼리, 상세는 `findFirstByDocumentIdOrderByVersionNumberDesc`를 그대로 쓴다. 새 버전 등록 후 두 조회 모두 새 버전을 반환해야 한다.
- 새 버전 등록 시 `documents.updated_at`을 등록 시각으로 갱신한다. `documents`의 다른 컬럼(제목, 설명, 상태, `created_by`)은 바꾸지 않는다.
- [기존 사실, 구현 제약] `BaseTimeEntity.updatedAt`은 JPA Auditing(`@LastModifiedDate`)으로 관리되며, 엔티티 필드가 바뀌지 않으면 갱신되지 않는다. 따라서 명시적 갱신이 필요하다(§13.2).
- 목록 정렬은 `created_at DESC, id DESC`이므로 새 버전 등록이 목록 순서를 바꾸지 않는다.

### 5.4 DocumentVersion [제안]

- `DocumentVersion.next(Document document, int versionNumber, StoredDocumentFile file, User uploadedBy)` 팩토리를 추가한다. `versionNumber`는 2 이상이어야 한다(호출 측 계산, 엔티티에서 방어적 확인).
- [기존] `first()`와 필드는 그대로 둔다. `uploaded_by`는 세션 사용자다 [결정 V3].
- 기존 버전 엔티티는 모든 컬럼이 `updatable = false`이며 수정하지 않는다 [결정 V2].

## 6. Backend API 명세

### 6.1 공통 [기존 재사용]

- 인증: Spring Security 세션. CSRF: `X-XSRF-TOKEN` 헤더(multipart 포함). `SecurityConfig` 변경 없음.
- 오류 형식: `{ code, message, fieldErrors? }`.
- 숫자가 아닌 `projectId`·`documentId`: `400 BAD_REQUEST`.

### 6.2 엔드포인트 [결정 V5]

| 메서드·경로 | 권한 | 성공 |
| --- | --- | --- |
| `POST /api/projects/{projectId}/documents/{documentId}/versions` (`multipart/form-data`) | 구성원 (OWNER, MEMBER) | `201`, `Location: /api/projects/{projectId}/documents/{documentId}`, 갱신된 `DocumentResponse` |

- 문서는 경로의 `projectId`와 `documentId`를 함께 조건으로 조회한다. 다른 프로젝트의 문서 ID는 존재하지 않는 문서와 같은 `404 DOCUMENT_NOT_FOUND`다(IDOR 방어, [기존] SPEC-03 §6.2).
- `Location`은 문서 상세를 가리킨다. 버전 단건 조회 API는 SPEC-05 범위이므로 만들지 않는다 [제안].

### 6.3 요청·응답 본문

**요청 (`multipart/form-data`)**

| 파트 | 필수 | 설명 |
| --- | --- | --- |
| `file` | O | 파일 1개. 형식·크기·파일명 규칙은 [기존] SPEC-03 §5.5~§5.7 |

- 정의되지 않은 파트(예: `versionNumber`, `uploadedBy`, `status`, `title`)는 무시한다. 버전 번호·업로더·상태는 요청으로 받지 않는다.
- 변경 메모 필드는 없다 [결정 V5].
- 허용 형식(PDF·DOCX·XLSX·PPTX) 사이의 형식 변경을 허용한다(예: v1 PDF → v2 DOCX) [결정 V5]. 저장 `content_type`은 서버가 판별한다.

**응답: `DocumentResponse`** ([기존] SPEC-03 §6.3 형식 그대로)

```jsonc
{
  "id": 10,
  "title": "요구사항 명세서",
  "description": "1차 초안",
  "status": "DRAFT",                       // 변경 없음
  "createdBy": { "id": 1, "displayName": "Author" },   // 최초 작성자, 변경 없음
  "createdAt": "2026-10-27T09:00:00Z",
  "updatedAt": "2026-11-05T10:00:00Z",     // 이번 등록 시각으로 갱신
  "latestVersion": {
    "versionNumber": 2,
    "originalFilename": "requirements-v2.docx",
    "sizeBytes": 524288,
    "uploadedAt": "2026-11-05T10:00:00Z"
  }
}
```

- 응답에 넣지 않는 것: `storage_key`, 서버 경로, `sha256`, `content_type`, 업로더 이메일 [기존].
- `DocumentResponse` 계약을 바꾸지 않는다. `latestVersion`에 업로더 표시 이름을 추가하지 않으며, 업로더 표시는 SPEC-05에서 검토한다 [결정 OQ-1].

### 6.4 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| [기존] `VALIDATION_FAILED` | 400 | 파일 누락·빈 파일·형식 불일치·손상·파일명 오류 (`fieldErrors[file]`) |
| [기존] `BAD_REQUEST` | 400 | 숫자가 아닌 경로 변수 |
| [기존] `AUTH_REQUIRED` | 401 | 미인증 |
| [기존] `CSRF_INVALID` | 403 | CSRF 실패 |
| [기존] `PROJECT_NOT_FOUND` | 404 | 존재하지 않거나 구성원이 아닌 프로젝트 |
| [기존] `DOCUMENT_NOT_FOUND` | 404 | 이 프로젝트에 없는 문서 (다른 프로젝트 문서, 미존재 — 본문 동일) |
| **[신규] `DOCUMENT_STATUS_CONFLICT`** | 409 | 문서 상태가 `IN_REVIEW`·`RE_REVIEW`·`APPROVED`라 새 버전을 등록할 수 없음 [결정 V4]. 메시지 예: "현재 문서 상태에서는 새 버전을 등록할 수 없습니다." |
| [기존] `FILE_TOO_LARGE` | 413 | 20,971,520바이트 초과, multipart 요청 상한 초과 (D4 제한사항 동일 적용, §13.3) |
| [기존] `UNSUPPORTED_MEDIA_TYPE` | 415 | `multipart/form-data`가 아닌 요청 |
| [기존] `INTERNAL_ERROR` | 500 | 파일 저장 실패, DB 오류, 예상하지 못한 버전 번호 UNIQUE 위반. 내부 경로·저장 키 미노출 |

- `DOCUMENT_STATUS_CONFLICT` 응답 본문은 공통 형식 `{ code, message }`만 쓰고 **현재 문서 상태 값을 포함하지 않는다** [결정 OQ-2]. 구성원은 상세 조회로 상태를 확인한다.

### 6.5 처리 순서 [결정 V1, V4]

```text
Security(CSRF·인증) → multipart 해석(크기 상한 → 413) → 경로 변수 타입(400) → 본문 바인딩(file 필수 → 400)
→ 프로젝트 접근(404 PROJECT_NOT_FOUND) → 버전 등록 권한(requireVersionUploader)
→ 문서 조회 by (documentId, projectId) (404 DOCUMENT_NOT_FOUND)
→ 상태 사전 확인 (409 DOCUMENT_STATUS_CONFLICT)                       ← 파일 저장 전 빠른 실패
→ 파일명 검증(400) → 임시 파일 복사·크기·SHA-256(413/400) → 형식 검증(400)
→ 최종 위치 배치 (CREATE_NEW, 이 요청이 생성한 파일)
→ DB 트랜잭션 시작
   → 문서 행 잠금 (SELECT … FOR UPDATE, documentId + projectId)  ← 잠금 획득 실패/문서 없음 → 404
   → 상태 재검증 (409 → 롤백 → 보상 삭제)                          ← 최종 판정
   → MAX(version_number) + 1 계산
   → DocumentVersion INSERT, documents.updated_at 갱신
→ 커밋 → 201
```

- multipart 해석과 바인딩은 Service 호출 전에 일어나므로, 비구성원도 크기 초과·파일 누락 요청에는 `413`·`400`을 먼저 받을 수 있다([기존] SPEC-03 §6.5와 같은 원칙). 이 응답에는 프로젝트·문서 정보가 없다.
- 파일 내용 처리(임시 파일 복사 이후)는 접근·권한·문서 존재·상태 사전 확인 뒤에 수행한다. 이 확인에 실패한 요청의 파일은 저장소에 쓰지 않는다.

## 7. 권한, 상태 정책, 트랜잭션, 동시성

### 7.1 권한 [결정 V3]

| 작업 | OWNER | MEMBER | 비구성원 | 미인증 |
| --- | --- | --- | --- | --- |
| 새 버전 등록 | O | O | `404 PROJECT_NOT_FOUND` | `401` |

- `ProjectPermissionService`에 **별도 메서드** `requireVersionUploader(Project accessibleProject, Long userId)`를 둔다. 현재는 OWNER·MEMBER 모두 허용한다. SPEC-06에서 Author/Reviewer 역할 모델이 정해지면 이 메서드만 바꾼다.
- 문서 작성자(`documents.created_by`)가 아닌 구성원도 등록할 수 있다. `uploaded_by`에는 세션 사용자를 기록하고 `created_by`는 바꾸지 않는다.
- [기존] 접근 확인은 `ProjectService.getAccessibleProject()`. 권한 규칙을 Controller·Repository·Frontend에 두지 않는다.
- 업로드 중 구성원에서 제거되는 경우: [기존] SPEC-02 D5 원칙대로 접근 확인 시점 기준이다. **문서 행 잠금 획득 후 구성원 여부를 다시 확인하지 않는다** [결정 OQ-3]. 따라서 접근 확인을 통과한 뒤 구성원에서 제거된 사용자의 등록은 성공할 수 있다(제거 커밋 이후 시작된 요청부터 404).
- `spec.md` US-05의 "Author"와의 관계: Author/Reviewer 역할 모델은 `spec.md` §16.2 미결정이며, SPEC-04는 이를 확정하지 않고 "프로젝트 구성원"(SPEC-02 Q10)을 기준으로 한다.

### 7.2 상태 정책 [결정 V4]

| 문서 상태 | 새 버전 등록 | 근거 |
| --- | --- | --- |
| `DRAFT` | 허용 | 검토 전 수정본 |
| `CHANGES_REQUESTED` | 허용 | `spec.md` §6.1 핵심 Workflow |
| `IN_REVIEW` | `409 DOCUMENT_STATUS_CONFLICT` | 검토 중 대상 변경 방지 |
| `RE_REVIEW` | `409 DOCUMENT_STATUS_CONFLICT` | 재검토 중 대상 변경 방지 |
| `APPROVED` | `409 DOCUMENT_STATUS_CONFLICT` | 잠정. `spec.md` §16.2 "승인된 Document에 새로운 Version을 추가할 수 있는가?"는 SPEC-12에서 재검토 |

- 새 버전 등록은 **문서 상태를 바꾸지 않는다.** `CHANGES_REQUESTED`에서 새 버전을 등록해도 `CHANGES_REQUESTED`로 남고, `RE_REVIEW` 전이는 SPEC-11의 재검토 요청이 담당한다.
- 상태 확인은 두 번 한다.
  1. **사전 확인**(파일 저장 전, 트랜잭션 밖): 불필요한 업로드·저장을 줄이기 위한 빠른 실패.
  2. **재검증**(잠금 획득 후, 트랜잭션 안): 최종 판정. 사전 확인과 잠금 사이에 상태가 바뀌었으면 `409`, 트랜잭션 롤백, 이 요청이 만든 파일 보상 삭제.
- 허용 규칙은 한 곳(예: `DocumentStatus` 또는 `Document`의 도메인 메서드 `canAcceptNewVersion()`)에 둔다 [제안].

### 7.3 파일 저장과 DB 트랜잭션 [결정 V1, V5; 기존 SPEC-03 §7.2 재사용]

**정상 흐름**: §6.5. 파일이 최종 위치에 배치된 뒤에만 DB 트랜잭션을 시작한다([기존] SPEC-03).

**실패 흐름과 보상**

| 실패 지점 | 응답 | DB | 파일 |
| --- | --- | --- | --- |
| 접근·권한·문서 조회·상태 사전 확인 | 401·403·404·409·400 | 변화 없음 | 저장하지 않음 |
| 임시 파일 쓰기 실패 | `500` | 변화 없음 | 임시 파일 삭제 시도 |
| 크기 초과 / 형식 오류 | `413` / `400` | 변화 없음 | 임시 파일 삭제 |
| 최종 배치 실패 — 같은 저장 키 파일 존재 | `500` | 변화 없음 (트랜잭션 시작 전) | 임시 파일만 삭제. 기존 파일은 삭제·변경하지 않음 |
| 최종 배치 실패 — 생성 후 복사 중 오류 | `500` | 변화 없음 | 이 요청이 만든 파일과 임시 파일 삭제 시도 |
| 트랜잭션 시작(연결 획득) 실패 | `500` | 변화 없음 | 이 요청이 만든 파일 삭제 (실패 시 원래 예외에 suppressed) |
| 잠금 후 상태 재검증 실패 | `409 DOCUMENT_STATUS_CONFLICT` | **롤백** | 롤백 확정 후 이 요청이 만든 파일 삭제 |
| INSERT·`updated_at` 갱신 실패 (제약 위반 등) | `500` | **롤백** (새 버전 행, `updated_at` 모두) | 롤백 확정 후 이 요청이 만든 파일 삭제 |
| 예상하지 못한 `uk_document_versions_document_version` 위반 | `500 INTERNAL_ERROR` | 롤백 | 롤백 확정 후 삭제. **ERROR 로그에 `projectId`, `documentId`, 시도한 `versionNumber`, 제약 이름, `storageKey`를 남긴다** (원본 파일명·경로 제외) [결정 V1] |
| 커밋 결과 불명확 | `500` | 알 수 없음 | **삭제하지 않음**, `storage_key` WARN 로그 |
| 보상 삭제 실패 | 원래 오류 유지 | 롤백됨 | 고아 파일 잔존, `storage_key` 로그 |

- [기존] 보상은 `TransactionSynchronization#afterCompletion`의 `STATUS_ROLLED_BACK`에서만 삭제하고, `STATUS_UNKNOWN`은 보존한다(`StoredFileCompensation`). 이 요청이 `CREATE_NEW`로 생성에 성공한 파일만 삭제한다.
- 기존 버전의 행·파일은 어떤 실패 경로에서도 변경·삭제되지 않는다 [결정 V2].
- 원자성의 한계: [기존] 저장소와 DB 사이에 원자적 트랜잭션이 없어 고아 파일은 생길 수 있다(SPEC-03 R1). "DB 행은 있는데 파일이 없음"은 순서로 막는다.

### 7.4 동시성과 잠금 [결정 V1]

- 잠금 대상: 해당 `documents` 행 하나. 잠금은 **파일 배치가 끝난 뒤 시작한 DB 트랜잭션 안에서만** 잡는다. 파일 복사·검증 시간 동안에는 잠금을 잡지 않는다.
- 잠금 조회는 `documentId`와 `projectId`를 함께 조건으로 한다. **다른 테이블(예: `users`)을 join하지 않는다** — PostgreSQL `FOR UPDATE`가 join된 테이블의 행까지 잠그는 것을 피하기 위함이다(§13.2).
- 같은 문서에 대한 동시 등록 요청은 잠금 순서대로 직렬화되어 v2, v3, …를 중복·누락 없이 받는다. 어느 요청도 번호 충돌로 실패하지 않는 것이 정상 동작이다.
- 서로 다른 문서의 등록은 서로 막지 않는다.
- SPEC-03 문서 등록(새 문서 INSERT)과는 잠금이 겹치지 않는다.
- **SPEC-04 전용 잠금 대기 시간 상한(lock timeout)을 설정하지 않는다.** 기존 DB·커넥션 설정은 유지한다 [결정 OQ-4]. 잠금 보유 구간은 파일 배치 후의 INSERT·UPDATE 몇 건으로 짧다. 동시성 테스트(§11.3)에서 잠금 대기 시간을 측정해 §17에 기록한다.
- 같은 사용자의 연속 제출, 서로 다른 사용자의 동시 제출 모두 별도 버전으로 등록된다 [결정 V7].

### 7.5 CSRF·세션 [기존 재사용]

- multipart 요청도 `X-XSRF-TOKEN` 헤더로 CSRF를 검증한다. Frontend는 [기존] `lib/api-client.ts`의 `FormData` 지원(`CSRF_INVALID` 1회 재시도 시 같은 `FormData` 재전송)을 그대로 쓴다.
- 세션 만료 시 `401 AUTH_REQUIRED`, Frontend는 기존 세션 만료 흐름을 따른다.

## 8. Backend 구조와 재사용 [결정 V5]

| 구성 요소 | 변경 |
| --- | --- |
| 파일 수신·검증·배치 (`storeValidatedFile`: 임시 복사 → 크기·SHA-256 → 파일명·형식 검증 → `CREATE_NEW` 배치 → 임시 파일 정리) | `DocumentService`에서 **공통 컴포넌트로 추출** [제안: 이름 예 `DocumentFileIntake`]. 동작 변경 없음 |
| 보상 등록 트랜잭션 실행 (트랜잭션 안에서 `StoredFileCompensation` 등록, 시작 전 실패 시 직접 삭제) | **공통 컴포넌트로 추출** [제안: 이름 예 `StoredFileTransaction`]. 동작 변경 없음 |
| `DocumentService.create` (SPEC-03) | 추출한 컴포넌트를 사용하도록 변경. **SPEC-03 동작·API 계약·테스트 계약 유지** |
| `DocumentVersionService` | **신설.** §6.5 흐름 구현 |
| `DocumentVersion.next(...)` | 신설 |
| `DocumentRepository` | 잠금 조회 메서드 추가 (`documentId`+`projectId`, join 없음, 비관적 쓰기 잠금) [제안] |
| `DocumentVersionRepository` | 잠금 후 `MAX(version_number)` 조회 메서드 추가 (기존 `findFirst…Desc` 재사용 가능) |
| `ProjectPermissionService` | `requireVersionUploader` 추가 |
| `ErrorCode` | `DOCUMENT_STATUS_CONFLICT(409)` 추가 |
| Controller | **`DocumentVersionController` 신설** (`/api/projects/{projectId}/documents/{documentId}/versions`) [결정 OQ-5]. 기존 `DocumentController`는 변경하지 않는다. Controller는 Repository를 호출하지 않고 Entity를 반환하지 않는다 ([기존] `spec.md` §11.4) |
| `FileStorage`·`LocalFileStorage`·검증기·`StoredFileCompensation`·크기 설정 | **변경 없음** (그대로 사용) |

- 새 의존성, 설정 변경(multipart·Tomcat·Next.js), 마이그레이션은 없다.
- 공통 컴포넌트 추출은 SPEC-04 승인 범위 안의 리팩터링이며, SPEC-03 테스트 전체가 회귀 기준이다(§11.5).

## 9. Frontend [제안]

### 9.1 화면

| 경로 | 변경 |
| --- | --- |
| `/projects/[projectId]/documents/[documentId]` | [기존] 문서 상세에 **"새 버전 등록" 영역** 추가: 파일 입력 + 등록 버튼 |
| 목록(프로젝트 상세 문서 섹션) | 변경 없음. 새 버전 등록 후 다시 조회하면 `v{N}`이 갱신되어 보임 |

- 새 버전 등록 UI는 기존 문서 상세 화면에 배치하고 별도 경로(`…/versions/new`)를 만들지 않는다 [결정 OQ-6].
- 버전 목록·다운로드 UI는 만들지 않는다(SPEC-05).

### 9.2 등록 폼

- 사전 검사: [기존] `features/documents/validation.ts`의 `validateFile` 재사용(필수, 확장자 4종, 1~20,971,520바이트, 파일명 255자). 서버가 최종 기준이다.
- 전송: `FormData`에 `file`만 넣는다. [기존] `api-client` 사용.
- 제출 중 버튼·입력 비활성화, 연속 제출 차단([기존] SPEC-03 패턴) [결정 V7].
- 성공: 응답 `DocumentResponse`로 상세 화면의 최신 버전 정보를 갱신하고, 파일 입력을 비우고(`key` 재마운트), 성공 문구(예: "새 버전 v2를 등록했습니다.")를 표시한다. 다른 경로로 이동하지 않는다.
- 상태 표시: 문서 상태가 `DRAFT`·`CHANGES_REQUESTED`가 아니면 등록 영역(파일 입력·버튼)을 **비활성화하고 안내 문구**(예: "현재 문서 상태에서는 새 버전을 등록할 수 없습니다.")를 표시한다 [결정 OQ-7]. 구현 문구: "초안 또는 수정 요청 상태의 문서에만 새 버전을 등록할 수 있습니다."(`409` 문구와 같은 문장이 한 화면에 두 번 나오지 않도록 예시 문구 대신 사용). 이는 사용성 보조이며 **Backend가 상태를 최종 검증한다**(§7.2). 화면 표시 후 상태가 바뀌어 제출이 `409`가 되면 §9.3대로 표시한다.
- 자동 재시도: 네트워크 오류 시 요청을 자동으로 다시 보내지 않는다 [결정 OQ-8]. [기존] `api-client`의 `403 CSRF_INVALID` 1회 재시도는 서버가 요청을 거부해 아무것도 저장하지 않은 경우에만 동작하므로 이 원칙과 충돌하지 않는다.
- `<Activity>` 재진입: 상세 화면을 떠났다가 다시 들어오면 파일 입력과 제출 상태가 초기화되어야 한다([기존] SPEC-03 교훈).

### 9.3 오류 표시

| 응답 | 표시 |
| --- | --- |
| `400 fieldErrors[file]` | 파일 입력 아래 서버 메시지 |
| `413` | 파일 입력 아래 "파일은 20MB 이하만 업로드할 수 있습니다." |
| `409 DOCUMENT_STATUS_CONFLICT` | "현재 문서 상태에서는 새 버전을 등록할 수 없습니다." + 상세 다시 조회 |
| `404 DOCUMENT_NOT_FOUND` / `PROJECT_NOT_FOUND` | 각각 "문서를 찾을 수 없습니다." / "프로젝트를 찾을 수 없습니다." |
| `401` | [기존] 세션 만료 흐름 |
| `403`·`415`·`5xx` | [기존] SPEC-03 문구 |
| 네트워크 오류 (연결 종료·응답 유실 등) | "새 버전 등록 결과를 확인하지 못했습니다. 문서 상세 정보를 새로고침하여 최신 버전을 확인한 뒤 다시 시도해 주세요." [결정 OQ-8]. 결과가 불명확함을 알리고 자동 재시도하지 않는다. 같은 파일 재등록은 허용되므로(V7) 사용자가 최신 버전을 먼저 확인한 뒤 다시 시도할지 판단하게 한다 |

## 10. 예외 및 오류 처리 (`spec.md` §12 검토)

| 시나리오 | Backend | Frontend |
| --- | --- | --- |
| 미인증 | `401` | 로그인 화면 |
| 비구성원 | `404 PROJECT_NOT_FOUND`, 파일 저장 없음 | 프로젝트 없음 |
| 다른 프로젝트 문서 ID / 없는 문서 | `404 DOCUMENT_NOT_FOUND` (같은 본문), 파일 저장 없음 | 문서 없음 |
| 허용되지 않은 상태 | `409 DOCUMENT_STATUS_CONFLICT`, 저장 없음 | 상태 안내 |
| 파일 누락·빈 파일·형식·파일명 오류 | `400 VALIDATION_FAILED` | 파일 필드 오류 |
| 20MiB 초과 | `413 FILE_TOO_LARGE` (21MiB 초과는 D4 제한사항) | 사전 검사로 전송 안 함 |
| 파일 저장 실패 | `500`, 새 행 없음 | 서버 오류 문구 |
| DB 실패 | `500`, 롤백, 이 요청이 만든 파일 보상 삭제 | 서버 오류 문구 |
| 예상하지 못한 번호 UNIQUE 위반 | `500`, 롤백, 보상 삭제, 원인 로그 | 서버 오류 문구 |
| 커밋 결과 불명확 | `500`, 파일 보존, 로그 | 서버 오류 문구 |
| 동시 등록 | 잠금으로 직렬화, 모두 성공 | — |
| 같은 파일 재등록·중복 요청 | 별도 버전으로 등록 (허용) | 제출 중 비활성화 |
| CSRF 실패 | `403 CSRF_INVALID` | [기존] 1회 재시도 |
| 네트워크 실패·프록시 타임아웃 | 결과 불명확 가능(서버가 등록을 마쳤을 수 있음) | "새 버전 등록 결과를 확인하지 못했습니다. 문서 상세 정보를 새로고침하여 최신 버전을 확인한 뒤 다시 시도해 주세요." 표시, 자동 재시도 없음 [결정 OQ-8] |
| 업로드 중 구성원 제거 | 접근 확인 시점 기준(SPEC-02 D5) | — |
| 잘못된 상태 전이 | 해당 없음 (SPEC-04는 상태를 바꾸지 않음) | — |

## 11. 테스트 전략 [결정 V8]

### 11.1 Backend 단위 테스트

- `DocumentVersion.next`: 번호 2 이상, 필드 매핑, `uploaded_by`.
- 상태 허용 규칙: 5개 상태 각각의 허용·거부.
- `ProjectPermissionService.requireVersionUploader`: OWNER·MEMBER 허용.
- 공통 컴포넌트(추출 후): [기존] `DocumentServiceTransactionTest`의 커밋·롤백·결과 불명확·시작 실패·보상 실패 시나리오를 새 버전 경로에도 적용(테스트 대역 트랜잭션 관리자).

### 11.2 Backend 통합 테스트 (실제 PostgreSQL, 보안 설정 활성)

| 대상 | 내용 |
| --- | --- |
| 등록 | `201`, `Location`, `DocumentResponse`(`latestVersion.versionNumber = 2`, `updatedAt` 증가, `status`·`createdBy` 불변), `document_versions` 행 추가(`uploaded_by` = 세션 사용자), 파일 크기·SHA-256 일치 |
| 기존 버전 보존 | v1 행의 모든 컬럼과 v1 파일 내용·SHA-256이 등록 전후 동일 |
| 연속 등록 | v2, v3 순서대로 할당, 목록 `latestVersionNumber`·상세 `latestVersion` 반영 |
| 형식 변경 | v1 PDF → v2 DOCX 등록 성공, `content_type` 서버 판별 |
| 같은 파일 재등록 | 직전과 같은 파일 → 새 버전 번호로 성공, 저장 키는 다름 |
| 권한 | OWNER·MEMBER 성공(작성자가 아닌 MEMBER 포함), 비구성원 `404`(파일 저장 없음) |
| IDOR | 다른 프로젝트 문서 ID → `404 DOCUMENT_NOT_FOUND`, 미존재와 본문 동일 |
| 상태 | JDBC로 상태를 `IN_REVIEW`·`RE_REVIEW`·`APPROVED`로 바꾼 뒤 `409 DOCUMENT_STATUS_CONFLICT`, 응답 본문이 `{ code, message }`만 있고 상태 값이 없음 [결정 OQ-2], 행·파일 변화 없음. `CHANGES_REQUESTED` 성공, 등록 후 상태 유지 |
| 상태 재검증 | 사전 확인 통과 후 잠금 전에 상태가 바뀌는 경우 → `409`, 롤백, 보상 삭제 (잠금 대기 기법으로 재현, §11.3) |
| 검증·크기 | 400(누락·빈 파일·위장·파일명), 413(실제 HTTP, `RANDOM_PORT`), 저장 없음 |
| 보상 | 고정 저장 키로 DB 실패(저장 키 UNIQUE 위반) → 롤백·보상 삭제, 기존 파일 키 충돌 → 기존 파일 보존 ([기존] SPEC-03 §10.2.1 A·B 방식) |
| 예상하지 못한 번호 UNIQUE 위반 | 잠금을 우회한 상황을 테스트에서 구성(예: 잠금 조회만 테스트 대역으로 교체 또는 직접 INSERT 경합)해 `500`, 롤백, 보상 삭제, 로그 내용 확인 [검증 필요: 재현 방법은 구현 시 확정] |
| 공통 | 401, CSRF 403, 415, 숫자가 아닌 ID 400, 정의되지 않은 파트 무시 |
| 응답 내부 정보·계약 | 응답에 저장 키·경로·해시·Content Type·이메일 없음. `DocumentResponse` 필드 구성이 SPEC-03과 동일(업로더 표시 이름 없음) [결정 OQ-1] |
| 라우팅 | 새 엔드포인트가 `DocumentVersionController`에서 처리되고, 기존 `DocumentController` 엔드포인트(등록·목록·상세) 동작이 변하지 않음 [결정 OQ-5] |

- 정리 순서: `document_versions` → `documents` → `projects` → `users` ([기존] R20). 저장소 파일 비교는 OS 독립 논리 키로 한다([기존] SPEC-03 §16.4 수정 사항).

### 11.3 동시성 테스트 (필수)

- **결정적 직렬화 검증**: [기존] SPEC-02의 `pg_locks` 잠금 대기 기법으로, 첫 요청이 문서 잠금을 잡은 상태에서 두 번째 요청이 대기하는지 확인하고, 해제 후 각각 v2·v3을 받는지 확인한다.
- **병렬 부하 검증**: 같은 문서에 N개(예: 8개) 동시 등록 → 모두 `201`, 번호 2..N+1 연속·중복 없음, 행 수 = 신규 파일 수, 보상·고아 파일 없음.
- **상태 재검증 경합**: 등록 요청이 파일 배치를 마친 뒤 잠금을 기다리는 동안 다른 트랜잭션이 상태를 `IN_REVIEW`로 바꾸고 커밋 → 등록 요청 `409`, 보상 삭제.
- 서로 다른 문서 동시 등록이 서로 막지 않음.
- **잠금 대기 시간 측정** [결정 OQ-4]: 병렬 부하 검증에서 요청별 잠금 대기 시간(또는 트랜잭션 시작~커밋 시간)과 전체 소요 시간을 측정해 환경(클라우드·CI·Windows)과 함께 §17에 기록한다. 측정값에 따른 자동 판정 기준(상한)은 두지 않는다.

### 11.4 Frontend 테스트 (Vitest)

| 대상 | 내용 |
| --- | --- |
| 문서 API | 경로·메서드, `FormData`에 `file`만 |
| 등록 폼 | 사전 검사(필수·확장자·크기 경계 20,971,520), 제출 중 비활성화·연속 제출 차단, 성공 시 최신 버전 갱신·입력 초기화·성공 문구 |
| 오류 표시 | 400·413·409·404(문서·프로젝트)·401·403·415·5xx. 네트워크 오류 시 "새 버전 등록 결과를 확인하지 못했습니다. 문서 상세 정보를 새로고침하여 최신 버전을 확인한 뒤 다시 시도해 주세요." 표시 [결정 OQ-8] |
| 자동 재시도 없음 | 네트워크 오류 후 등록 API가 다시 호출되지 않음(호출 횟수 1회) [결정 OQ-8] |
| 상태별 UI | `DRAFT`·`CHANGES_REQUESTED`에서 활성, `IN_REVIEW`·`RE_REVIEW`·`APPROVED`에서 파일 입력·버튼 비활성화와 안내 문구 [결정 OQ-7] |
| 배치 | 등록 영역이 문서 상세 화면 안에 있고 별도 경로가 없음 [결정 OQ-6] |
| `<Activity>` 재진입 | 성공·실패 후 숨김 → 재표시 시 초기화 |
| 회귀 | SPEC-01·02·03 Frontend 테스트 전체 |

### 11.5 회귀

- Backend: SPEC-AUTH·01·02·03 기존 테스트 전체(기준: `main` `a140a94`에서 254개). 공통 컴포넌트 추출 후에도 SPEC-03 테스트가 **수정 없이** 통과해야 한다(테스트 수정이 필요하면 사유를 보고하고 승인 후 진행).
- Frontend: 기존 169개 전체, lint, typecheck, `next build`.
- [구현 확인] 테스트 컨텍스트마다 Hikari 풀(기본 10개)이 열리므로, 신규 SPEC-04 통합 테스트 4개 클래스를 기존 컨텍스트 7개에 더하면 PostgreSQL 기본 `max_connections`(100, CI 서비스 컨테이너 동일)를 넘어 컨텍스트 로딩이 실패했다. 설정을 바꾸지 않고 신규 클래스에 `@DirtiesContext`를 붙여 클래스 종료 시 컨텍스트를 닫는다. 기존 테스트는 수정하지 않았다.

### 11.6 클라우드 실제 경로 E2E (필수)

- 환경: Claude Code 클라우드, 임시 PostgreSQL 16, Spring Boot jar, Next.js `next start`(rewrite 경유), Chromium(Playwright).
- 항목: OWNER·MEMBER 새 버전 등록 → 상세 갱신, 목록 `v{N}` 반영, 19MiB·20MiB 새 버전 업로드(크기·SHA-256 일치), 20MiB+1 사전 차단, 비구성원 404, 허용되지 않은 상태(DB에서 상태 설정)에서 등록 영역 비활성화·안내(OQ-7)와 API 직접 호출 시 409, 네트워크 오류(브라우저 오프라인·연결 리셋) 시 OQ-8 문구 표시와 자동 재시도 없음, 같은 파일 재등록, 동시 업로드(HTTP 수준), CSRF 헤더 포함.
- 결과는 실행 명령·환경·결과를 §17에 기록한다.

### 11.7 Windows 검증

| 구분 | 내용 | 완료 기준 포함 |
| --- | --- | --- |
| Windows 자동 테스트·빌드 | 테스트 전용 DB(`docflow_test`, 전용·빈 DB)에서 `.\gradlew.bat clean build`, 동시성·크기 테스트 반복 실행, Frontend `npm run lint`·`test`·`build` | **포함 (필수)** |
| Windows 실제 브라우저 수동 E2E | 새 버전 등록 화면 흐름, Next.js 경유 대용량 업로드 등 | **불포함** — 구현 완료 시점에 별도 후속 Issue로 추적, Issue 생성은 별도 승인 후 진행 [결정 OQ-9] |

- 실행 전 테스트 로그의 JDBC URL이 `docflow_test`인지 확인한다([기존] SPEC-03 §16.4 안전 조치). 개발 DB에서 실행하지 않는다.

### 11.8 검증 환경과 주체

| 검증 | 주체 |
| --- | --- |
| Backend 단위·통합·동시성 | Claude Code 클라우드(JDK 21 + 임시 PostgreSQL 16), GitHub Actions CI(JDK 25) |
| Frontend lint·test·build | Claude Code 클라우드, CI |
| 클라우드 실제 경로 E2E (§11.6) | Claude Code 클라우드 |
| Windows 자동 테스트·빌드 (§11.7) | 사용자 |
| Windows 실제 브라우저 수동 E2E | 사용자, 후속 Issue (OQ-9) |

- 실행하지 않은 검증은 통과로 기록하지 않는다. 실행한 테스트는 명령·환경·결과를 구분해 기록한다 [결정 V8].

## 12. Acceptance Criteria

| ID | 기준 | 확인 방법 | 검증 | 완료 판정 근거 |
| --- | --- | --- | --- | --- |
| **AC-VER-01** | OWNER·MEMBER는 기존 문서에 새 버전을 등록할 수 있다 | `201`, `Location`(문서 상세), `DocumentResponse.latestVersion.versionNumber` = 등록 전 최대 + 1, `uploaded_by` = 세션 사용자(작성자가 아닌 MEMBER 포함) | 통합 | 통합 테스트 통과 |
| **AC-VER-02** | 새 버전 등록은 기존 버전과 문서 정보를 변경하지 않는다 | 기존 버전 행 전 컬럼·파일 내용·SHA-256 불변, `documents.created_by`·`title`·`description`·`status` 불변, `updated_at`만 갱신 | 통합 | 등록 전후 비교 단언이 있는 통합 테스트 통과 |
| **AC-VER-03** | 최신 버전이 조회에 반영된다 | 상세 `latestVersion`, 목록 `latestVersionNumber`가 새 버전, 목록 정렬(`created_at DESC, id DESC`) 불변 | 통합 | 통합 테스트 통과 |
| **AC-VER-04** | 동시 등록에서 번호가 중복·누락 없이 할당된다 | N개 동시 요청 모두 `201`, 번호 연속, 행 수 = 신규 파일 수, 잠금 대기 순서 결정적 검증, 잠금 대기 시간 측정 [OQ-4] | 동시성 통합 | 잠금 대기 테스트 + 병렬 부하 테스트 통과, 측정한 대기 시간을 §17에 기록(상한 판정 없음) |
| **AC-VER-05** | 허용된 상태에서만 등록할 수 있다 | `DRAFT`·`CHANGES_REQUESTED` 성공(상태 유지), `IN_REVIEW`·`RE_REVIEW`·`APPROVED`는 `409 DOCUMENT_STATUS_CONFLICT`(본문에 상태 값 없음 [OQ-2]), 행·파일 변화 없음 | 단위 + 통합 | 5개 상태 모두에 대한 단위·통합 테스트 통과, 409 본문 필드 단언 포함 |
| **AC-VER-06** | 상태는 잠금 후 다시 검증된다 | 사전 확인 통과 후 상태가 바뀐 경우 `409`, 롤백, 이 요청이 만든 파일 보상 삭제 | 동시성 통합 | 상태 재검증 경합 테스트 통과 |
| **AC-VER-07** | 파일 검증·크기 제한이 SPEC-03과 같다 | 파일 누락·빈 파일·위장·파일명 오류 `400`(`fieldErrors[file]`), 20,971,520바이트 `201`, +1바이트 `413` (실제 HTTP), 저장 없음 | 통합 (RANDOM_PORT) | 통합 테스트 통과 |
| **AC-VER-08** | 파일 저장 실패 시 DB 행이 생기지 않는다 | 저장소 I/O 오류·저장 키 충돌 → `500`, 새 행 0, 기존 파일 보존, 응답에 내부 정보 없음 | 통합 | 통합 테스트 통과 |
| **AC-VER-09** | DB 실패 시 롤백되고 이 요청의 파일만 보상 삭제된다 | DB 제약 위반 → `500`, 새 행 0, `updated_at` 불변, 이 요청이 만든 파일 삭제, 기존 파일 보존 | 통합 | 통합 테스트 통과 |
| **AC-VER-10** | 예상하지 못한 번호 UNIQUE 위반은 서버 오류로 처리되고 원인 로그가 남는다 | `500 INTERNAL_ERROR`, 롤백, 보상 삭제, 로그에 `projectId`·`documentId`·`versionNumber`·제약 이름·`storageKey`(원본 파일명·경로 없음) | 통합 | 재현 테스트 + 로그 캡처 단언 통과 |
| **AC-VER-11** | 커밋 결과 불명확·보상 실패 처리가 SPEC-03과 같다 | 결과 불명확 시 파일 보존·WARN 로그, 보상 삭제 실패 시 원래 오류 유지 | 단위 (테스트 대역) | 단위 테스트 통과 |
| **AC-VER-12** | 비구성원과 다른 프로젝트 문서는 존재를 알 수 없다 | 비구성원 `404 PROJECT_NOT_FOUND`, 다른 프로젝트 문서 `404 DOCUMENT_NOT_FOUND`(미존재와 본문 동일), 파일 저장 없음 | 통합 | 통합 테스트 통과 |
| **AC-VER-13** | 같은 파일 재등록은 새 버전으로 등록된다 | 직전과 SHA-256이 같아도 `201`, 새 번호, 다른 저장 키 | 통합 | 통합 테스트 통과 |
| **AC-VER-14** | 인증·CSRF·요청 형식 오류가 기존 계약대로 처리된다 | 미인증 `401`, CSRF 없음 `403`, 비 multipart `415`, 숫자가 아닌 ID `400`, 정의되지 않은 파트 무시, 저장 없음 | 통합 | 통합 테스트 통과 |
| **AC-VER-15** | 응답은 내부 정보를 노출하지 않고 계약이 SPEC-03과 같다 | 응답에 저장 키·경로·SHA-256·Content Type·이메일 없음, `DocumentResponse` 필드 구성이 SPEC-03과 동일(업로더 표시 이름 미추가 [OQ-1]), 새 엔드포인트는 `DocumentVersionController`가 처리 [OQ-5] | 통합 | 응답 필드 단언이 있는 통합 테스트 통과 |
| **AC-VER-16** | Frontend 등록 영역이 검증·중복 제출 방지·성공 갱신·상태별 비활성화·오류 표시를 한다 | §11.4: 문서 상세 화면 배치 [OQ-6], 허용되지 않은 상태에서 비활성화·안내 [OQ-7], 네트워크 오류 시 확정 문구 표시와 자동 재시도 없음 [OQ-8] | Vitest | Vitest 통과 |
| **AC-VER-17** | 클라우드 실제 경로에서 새 버전 등록이 동작한다 | §11.6 항목: Next.js 경유 Chromium 등록·상세·목록 반영, 19·20MiB 크기·SHA-256 일치, 상태별 비활성화·409, 404 화면, 네트워크 오류 문구 | 클라우드 E2E (Chromium 자동화) | §11.6 전 항목 실행, §17에 명령·환경·결과 기록 |
| **AC-VER-18** | Windows에서 자동 테스트와 빌드가 통과한다 | `docflow_test`에서 `.\gradlew.bat clean build` 성공, 동시성·크기 테스트 반복 통과, Frontend lint·test·build 성공 | Windows 자동 (사용자) | 사용자 실행 결과(명령·환경·결과) 기록. **미실행이면 SPEC-04 Done 아님** |
| **AC-VER-19** | 기존 기능이 회귀하지 않고 CI가 통과한다 | SPEC-AUTH·01·02·03 Backend·Frontend 테스트 전체 통과(SPEC-03 테스트 수정 없음), `SecurityConfig` 변경 없음, CI 성공 | 회귀 + CI | 최신 커밋 CI 성공 + 클라우드 전체 실행 결과 |

- **판정 환경 공통 규칙:** 검증 유형이 단위·통합·동시성·Vitest인 AC(AC-VER-01~16)의 "통과"는 Claude Code 클라우드 실행과 최신 커밋 GitHub Actions CI 결과로 판정한다. 같은 테스트의 Windows 실행은 AC-VER-18로 따로 판정하며, AC-VER-18을 실행하지 않았으면 SPEC-04는 Done이 아니다. 각 결과는 §17에 명령·환경·결과로 기록한다.
- Windows 실제 브라우저 수동 E2E는 AC에 포함하지 않는다. 구현 완료 시점에 별도 후속 Issue로 추적한다(생성은 별도 승인) [결정 V8, OQ-9].
- OQ-3(잠금 후 구성원 재확인 없음)은 동작을 추가하지 않는 결정이므로 별도 AC를 두지 않는다. 기존 SPEC-02 D5 원칙과 AC-VER-12(접근 확인 시점의 404)로 다룬다.
- Done 판정: AC-VER-01~19 모두 충족 + `spec.md` §14 DoD. Windows 자동 검증(AC-VER-18)이 수행되지 않았다면 Done으로 판정하지 않는다.

## 13. 기술적 고려사항, 선행 작업, 잔여 위험

### 13.1 선행 작업

| 항목 | 내용 |
| --- | --- |
| SPEC-03 기록 정리 | `main`에서 SPEC-03이 "병합 전"으로 남아 있던 기록(`specs/README.md`, SPEC-03 §15, `README.md`)을 이 Draft와 함께 갱신 |
| SPEC-03 후속 | #17(Windows 실환경 검증), #18(D4)은 SPEC-04와 독립. 같은 업로드 경로를 쓰므로 결과가 SPEC-04에도 유효 |

### 13.2 구현 제약 [검증 필요]

- **잠금 쿼리 형태**: [기존] `DocumentRepository.findByIdAndProjectId`는 `join fetch d.createdBy`를 쓴다. 여기에 비관적 잠금을 붙이면 PostgreSQL이 `users` 행까지 잠글 수 있으므로 join 없는 별도 잠금 조회를 둔다. Hibernate가 만드는 잠금 SQL(`FOR UPDATE` 또는 `FOR NO KEY UPDATE`)과 두 등록 트랜잭션의 실제 직렬화를 구현 시 로그·테스트로 확인한다.
  - **[확인 2026-11-04, I3]** Hibernate 7.4.5 실제 SQL: `select … from documents d1_0 where d1_0.id=? and d1_0.project_id=? for no key update of d1_0` (join 없음). 잠금 보유 중 다른 연결에서 작성자 `users` 행 `FOR NO KEY UPDATE NOWAIT` 성공, `documents` 행은 `55P03`. `DocumentVersionConcurrencyIntegrationTest.lockQueryLocksOnlyTheDocumentRow`가 SQL 형태와 잠금 범위를 회귀 검증한다.
- **`updated_at` 갱신**: `@LastModifiedDate`는 엔티티가 변경될 때만 동작한다. 도메인 메서드로 명시적 변경(예: 갱신 시각 필드를 바꾸는 메서드) 또는 JPQL 벌크 UPDATE 중 하나를 구현 시 선택하고, 응답 `updatedAt`과 DB 값이 일치함을 확인한다. 벌크 UPDATE는 영속성 컨텍스트 동기화에 주의한다.
  - **[확인, I2]** `BaseTimeEntity.markModified()`로 변경 상태를 만들고 트랜잭션 안에서 flush한다. 응답 `updatedAt`과 DB 값이 마이크로초 정밀도에서 일치함을 통합 테스트로 확인했다.
- **TOCTOU**: 접근 확인은 트랜잭션 밖(readOnly)에서 일어나므로 상태 판정은 잠금 트랜잭션 안에서 다시 한다(§7.2).
- **리팩터링 회귀**: 공통 컴포넌트 추출은 SPEC-03 경로(보상, 시나리오 A·B, 크기 경계, Windows 소켓 테스트)를 건드린다. SPEC-03 테스트가 수정 없이 통과해야 한다.
- **예상하지 못한 UNIQUE 위반 재현**: 정상 경로에서는 잠금 때문에 발생하지 않으므로 재현 방법(테스트 대역 등)을 구현 시 정한다(AC-VER-10).
  - **[확인, I3]** Mock 없이 재현했다: 별도 연결이 문서 잠금 없이 `(document_id, 2)` 행을 미커밋 INSERT → 요청은 잠금 획득(FK의 `FOR KEY SHARE`와 충돌 없음)·MAX=1 계산 후 INSERT에서 대기 → 별도 연결 커밋 → 실제 `23505 uk_document_versions_document_version` → `500`, 롤백, 보상 삭제, ERROR 원인 로그.
- **테스트 DB 격리**: 버전 행이 늘어나므로 정리 순서 유지, 전용·빈 테스트 DB 원칙 유지([기존] R20).

### 13.3 잔여 위험

| ID | 위험 | 대응 |
| --- | --- | --- |
| R1 | 저장소–DB 원자성 부재로 고아 파일 | [기존] SPEC-03 보상 정책, 자동 정리 범위 밖 |
| R2 | D4: 요청 21MiB 초과 시 Next.js 경유 413 대신 연결 리셋 | [기존] 알려진 제한사항, #18. 새 버전 업로드에도 동일 |
| R3 | 같은 문서 등록이 잠금으로 직렬화되어 대기 발생. 잠금 대기 상한이 없어 비정상적으로 긴 트랜잭션이 있으면 대기가 길어질 수 있음 | 잠금 구간을 파일 배치 후 DB 작업으로 한정. 상한 없이 측정·기록 [결정 OQ-4] |
| R9 | 네트워크 오류 시 등록 결과 불명확 → 사용자가 다시 시도하면 같은 파일 버전이 중복 생성될 수 있음 | 결과 불명확 안내 문구, 자동 재시도 없음 [결정 OQ-8]. 중복 버전 자체는 허용 [결정 V7] |
| R10 | 접근 확인 후 구성원에서 제거된 사용자의 등록이 성공할 수 있음 | SPEC-02 D5 원칙 유지 [결정 OQ-3] |
| R4 | 같은 파일 재등록·자동 재시도로 의미 없는 버전 증가 | 허용 [결정 V7]. 제출 중 비활성화로 완화. Idempotency Key 범위 밖 |
| R5 | 상태 전이 기능 부재로 409 정책은 테스트에서만 실제 동작 확인 | SPEC-06 이후 통합 시 재검증 |
| R6 | `APPROVED` 새 버전 거부가 이후 정책과 다를 수 있음 | SPEC-12에서 재검토 [결정 V4] |
| R7 | 작성자가 아닌 MEMBER도 새 버전을 올릴 수 있음 | [결정 V3], SPEC-06 역할 모델에서 재검토 |
| R8 | 형식 검증이 안전성을 보증하지 않음 | [기존] SPEC-03 R8, 실행·렌더링하지 않음 |

### 13.4 Audit Log 기록 지점 (SPEC-13 참고)

- 기록 후보 이벤트: 새 버전 등록 성공(`projectId`, `documentId`, `versionNumber`, `uploadedBy`, 시각). SPEC-04는 애플리케이션 INFO 로그만 남긴다(원본 파일명 제외).

## 14. 후속 SPEC과의 관계

| 후속 SPEC | 이 SPEC이 제공하는 것 | 후속에서 결정할 것 |
| --- | --- | --- |
| SPEC-05 Version 조회 | 문서별 연속 버전 번호, `uploaded_by`, 버전별 파일 | 버전 목록, 다운로드, 미리보기 여부, 업로더 표시 |
| SPEC-06 Review 요청 | 버전 등록 시점의 상태 규칙(`IN_REVIEW` 중 등록 금지) | 검토 대상 버전 지정, Author/Reviewer 역할 모델 → `requireVersionUploader` 재검토 |
| SPEC-07~09 Feedback | — | Feedback과 버전 연결, APPLIED 조건(`spec.md` §16.2) |
| SPEC-10~11 수정 요청·재검토 | `CHANGES_REQUESTED`에서 새 버전 등록 허용, 상태는 바꾸지 않음 | 재검토 요청 시 대상 버전, `RE_REVIEW` 전이 |
| SPEC-12 승인 | `APPROVED`에서 새 버전 거부(잠정) | 승인 후 새 버전 정책 |
| SPEC-13 Audit Log | 기록 지점(§13.4) | 기록 범위 |

## 15. 결정 사항 및 미결정 사항

### 15.1 결정 사항 (2026-11-04, 사용자 승인)

| ID | 결정 | 반영 위치 |
| --- | --- | --- |
| V1 | 파일 배치 후 시작한 DB 트랜잭션에서 문서 행 `SELECT … FOR UPDATE` → `MAX(version_number)+1`. UNIQUE 유지. 상태 재검증, DB 실패 시 기존 보상 정책. 예상하지 못한 UNIQUE 위반은 서버 오류 + 원인 로그 | §5.2, §6.5, §7.3, §7.4 |
| V2 | `MAX(version_number)` 유지, 포인터 없음, 새 버전 등록 시 `documents.updated_at` 갱신, 기존 버전 데이터·파일 불변 | §5.1, §5.3 |
| V3 | OWNER·MEMBER 모두 등록, 비구성원 404 은닉, `uploaded_by` = 세션 사용자, `created_by` 불변, 별도 권한 메서드 | §7.1 |
| V4 | `DRAFT`·`CHANGES_REQUESTED` 허용, `IN_REVIEW`·`RE_REVIEW`·`APPROVED`는 `409 DOCUMENT_STATUS_CONFLICT`. 업로드 전 확인 + 잠금 후 재검증. 등록 시 상태 변경 없음. `APPROVED`는 SPEC-12에서 재검토 | §6.4, §7.2 |
| V5 | 파일 저장·검증·보상 로직 공통 컴포넌트 추출, `DocumentVersionService` 신설, `POST …/documents/{documentId}/versions`(file만), `201` + 갱신된 `DocumentResponse`, 변경 메모 없음, 허용 형식 간 변경 허용, SPEC-03 동작·테스트 계약 유지 | §6, §8 |
| V6 | SPEC-04는 버전 등록·번호 할당·최신 버전 반영·상태 검증만 담당. 목록·다운로드·미리보기 SPEC-05, Review 대상 SPEC-06, Feedback 연결 후속, 재검토·상태 전이 SPEC-11, Audit Log SPEC-13, 버전 삭제 제외 | §3, §14 |
| V7 | 직전 버전과 SHA-256이 같아도 새 버전으로 등록, 매번 새 번호, 제출 중 중복 클릭 방지, Idempotency Key·자동 재시도 중복 방지 제외 | §4.4, §7.4, §9.2 |
| V8 | 단위·실제 PostgreSQL 통합·동시성 테스트, 클라우드 Next.js 실제 브라우저 E2E, Windows 자동 테스트·빌드, 기존 회귀와 CI 필수. Windows 실제 브라우저 수동 E2E는 후속. 미실행 검증 미기록, 명령·환경·결과 구분 기록, Windows 검증 미수행 시 Done 아님 | §11, §12 |

### 15.2 미결정 사항 결정 결과 (2026-11-04, 사용자 승인)

| ID | 결정 | 반영 위치 |
| --- | --- | --- |
| OQ-1 | `latestVersion` 응답에 업로더 표시 이름을 추가하지 않는다. SPEC-05에서 검토 | §6.3, §11.2, AC-VER-15 |
| OQ-2 | `409 DOCUMENT_STATUS_CONFLICT` 응답에 현재 문서 상태 값을 포함하지 않는다 | §6.4, §11.2, AC-VER-05 |
| OQ-3 | 문서 행 잠금 획득 후 구성원 여부를 재확인하지 않는다. SPEC-02 D5 접근 확인 시점 정책 유지 | §7.1, R10 |
| OQ-4 | SPEC-04 전용 잠금 대기 시간 상한을 설정하지 않는다. 기존 DB 설정 유지, 동시성 테스트에서 대기 시간 측정·기록 | §7.4, §11.3, AC-VER-04, R3 |
| OQ-5 | `DocumentVersionController` 신설 | §8, §11.2, AC-VER-15 |
| OQ-6 | 새 버전 등록 UI는 기존 문서 상세 화면에 배치 | §9.1, §11.4, AC-VER-16 |
| OQ-7 | 등록할 수 없는 상태에서 등록 UI 비활성화 + 안내 문구. Backend도 상태 검증 | §9.2, §11.4, §11.6, AC-VER-16·17 |
| OQ-8 | 네트워크 오류 시 "새 버전 등록 결과를 확인하지 못했습니다. 문서 상세 정보를 새로고침하여 최신 버전을 확인한 뒤 다시 시도해 주세요." 표시. 자동 재시도 없음. 같은 파일 재등록 허용(V7)과 충돌하지 않음 | §9.2, §9.3, §10, §11.4, §11.6, AC-VER-16·17, R9 |
| OQ-9 | Windows 실제 브라우저 수동 E2E는 완료 필수 조건에서 제외, 구현 완료 시점에 별도 후속 Issue로 추적(생성은 별도 승인) | §3, §11.7, §12, §16 |
| OQ-10 | 루트 `spec.md` §16.2에 "APPROVED 문서의 새 버전 등록은 SPEC-04에서 잠정 거부, SPEC-12에서 재검토"를 명시 | §7.2, `spec.md` §16.2 |

### 15.3 남은 미결정 사항

정책 미결정 없음. §13.2의 구현 제약(잠금 SQL 형태, `updated_at` 갱신 방식, 예상하지 못한 UNIQUE 위반 재현 방법)은 구현 단계에서 확인할 기술 사항이다. 결과가 이 SPEC과 다르면 보고 후 SPEC을 먼저 갱신한다.

### 15.4 구현 결정 (2026-11-04, 사용자 승인)

V1~V8·OQ-1~10을 구현하는 방식에 대한 결정이다. 정책을 바꾸지 않는다.

| ID | 결정 | 반영 위치 |
| --- | --- | --- |
| I1 | 공통 컴포넌트 `DocumentFileIntake`(임시 복사·크기·SHA-256·형식 검증·`CREATE_NEW` 배치)와 `StoredFileTransaction`(보상 등록 후 트랜잭션 실행, 시작 전 실패 시 직접 삭제)은 Spring Bean이 아닌 package-private 일반 객체로 만들고 각 Service 생성자에서 생성한다. `DocumentService` 생성자 시그니처를 유지해 SPEC-03 테스트를 수정하지 않는다 | §8, §11.5 |
| I2 | `documents.updated_at` 갱신은 `BaseTimeEntity`에 `protected markModified()`를 추가해 엔티티를 변경 상태로 만들고, 트랜잭션 안에서 flush해 JPA Auditing이 기록한 값을 응답에 쓴다. JPQL 벌크 UPDATE는 쓰지 않는다 | §5.3, §13.2 |
| I3 | 잠금 조회는 join 없는 JPQL + `PESSIMISTIC_WRITE`로 하고, 실제 Hibernate 잠금 SQL을 확인한 뒤 진행한다. AC-VER-10은 Mock 없이 실제 PostgreSQL UNIQUE 위반으로 재현한다: 별도 연결이 문서 잠금 없이 같은 번호의 버전 행을 미커밋 INSERT한 상태에서 요청을 보내고, 요청 INSERT가 대기하는 것을 확인한 뒤 커밋한다(잠금 SQL이 `FOR NO KEY UPDATE`일 때 성립). 성립하지 않으면 운영 코드를 바꾸지 않고 대안을 보고한다 | §7.4, §11.2, §13.2, AC-VER-10 |
| I4 | 잠금 대기 시간은 테스트에서만 측정한다(운영 코드에 측정 로그를 추가하지 않음) | §11.3, AC-VER-04 |
| I5 | 병렬 부하 검증은 N=8, 기존 Hikari 설정(기본 풀 크기, `connection-timeout: 5000`) 유지 | §11.3, R3 |
| I6 | Frontend는 `409` 시 오류 문구를 유지하면서 로딩 화면 없이 문서 상세를 백그라운드로 다시 조회한다 | §9.3 |
| I7 | 구현은 ① SPEC-03 파일 처리 리팩터링 ② Backend API ③ PostgreSQL 통합·동시성 테스트 ④ Frontend ⑤ E2E·회귀·문서 순서로, 각 단계 테스트 후 의미 있는 단위로 커밋한다 | §16 |

## 16. 구현 계획 및 단계별 체크리스트 (구현 승인 후)

0. [x] 설계 결정 V1~V8 확정 (2026-11-04)
1. [x] SPEC-04 Draft v0.1 작성, SPEC-03 병합 기록 정리 (커밋 전)
2. [x] OQ-1~10 결정 반영 (Draft v0.2, 2026-11-04)
2-1. [x] Draft v0.2 최종 검토 승인 (사용자, 2026-11-04)
3. [x] GitHub Issue #19 생성, 브랜치 `feat/19/document-version-create` 생성 (2026-11-04, 사용자 승인)
4. [x] Backend: 공통 컴포넌트 추출(SPEC-03 회귀 확인) → `DocumentVersionService`·잠금 조회·권한 메서드·오류 코드·Controller + 단위·통합·동시성 테스트 (2026-11-04)
5. [x] Frontend: 문서 상세 새 버전 등록 영역 + Vitest (2026-11-04)
6. [x] 클라우드 전체 테스트·빌드, 클라우드 실제 경로 E2E (§11.6) → §17 기록 (2026-11-04)
7. [x] CI 확인 (`e8c5bd0` 성공, 이후 문서 커밋은 PR에서 재확인)
8. [x] Windows 자동 테스트·빌드 (사용자, §11.7) → §17 기록 (2026-11-04). 동시성·크기 테스트 반복 실행 결과는 미보고
9. [x] 문서: README, `docs/architecture.md`, 이 SPEC §17
10. [ ] PR (`Closes #19`), 병합은 사용자
11. [ ] Windows 실제 브라우저 수동 E2E 후속 Issue 생성 (구현 완료 시점, 별도 승인 후) [결정 OQ-9]

## 17. 검증 결과

실행한 결과만 기록한다. 실행하지 않은 검증은 "미실행"으로 둔다.

### 17.1 요약

| 항목 | Claude Code 클라우드 | GitHub Actions CI | Windows 로컬 (사용자) |
| --- | --- | --- | --- |
| Backend build·test | 성공, 299개 통과(기존 254 + 신규 45) | 성공 (`e8c5bd0`, run 67) | 성공 (`.\gradlew.bat clean build --rerun-tasks`, §17.5) |
| Frontend lint·test·build | 성공, Vitest 198개(기존 169 + 신규 29), `tsc`, `next build` | 성공 (`e8c5bd0`, run 67) | 성공, Vitest 198/198 (§17.5) |
| 클라우드 실제 경로 E2E (§11.6) | 19/19 통과 | — | — |
| Windows 실제 브라우저 수동 E2E | — | — | 후속 Issue로 추적 예정 (AC 미포함, OQ-9) |
| 잠금 대기 시간 측정 (OQ-4) | §17.3 | 미기록 (CI는 테스트 표준 출력을 남기지 않음) | 미보고 |

### 17.2 실행 환경과 명령

- 클라우드: Linux, OpenJDK 21.0.12(Gradle init script로 toolchain 21 지정, CI는 JDK 25), PostgreSQL 16.15(임시 인스턴스, 기본 `max_connections` 100), Node 22.22, Chromium(Playwright).
- Backend: `./gradlew clean build` (DB `docflow`, 전용 테스트 DB). 기준선은 구현 전 `clean test --rerun-tasks` 254개 통과.
- Frontend: `npm run lint`, `npx tsc --noEmit`, `npm test`, `npm run build`.
- E2E: Spring Boot jar(`local` 프로필, 전용 DB `docflow_e2e`, 전용 저장 루트) + `next start`(rewrite 경유) + Chromium. 스크립트는 저장소 밖(클라우드 scratchpad)에 두었다.

### 17.3 AC별 결과

| AC | 근거 (클라우드·CI) | 결과 |
| --- | --- | --- |
| AC-VER-01 | `DocumentVersionApiIntegrationTest.memberWhoIsNotAuthorUploadsVersionTwoWithoutChangingExistingData`, `consecutiveUploads…` | 통과 |
| AC-VER-02 | 같은 테스트(v1 행 전 컬럼·파일 SHA-256, documents 컬럼 비교, `updated_at` 증가·응답 값 일치) | 통과 |
| AC-VER-03 | `consecutiveUploadsAreReflectedInDetailAndListWithoutReordering` | 통과 |
| AC-VER-04 | `DocumentVersionConcurrencyIntegrationTest.requestsWaitForDocumentLockAndReceiveDistinctNumbers`, `parallelUploadsGetContiguousNumbersWithoutLoss`(N=8), `lockOnOneDocumentDoesNotBlock…`, `lockQueryLocksOnlyTheDocumentRow` | 통과. 측정값 아래 |
| AC-VER-05 | `DocumentStatusTest`(5개 상태), `reviewAndApprovedStatusesRejectNewVersion`(409 본문 키 `code`·`message`만), `changesRequestedAcceptsNewVersionAndKeepsStatus`, `DocumentVersionServiceTransactionTest.statusRejectedBeforeUploadStoresNothing` | 통과 |
| AC-VER-06 | `statusChangedWhileWaitingForLockIsRejectedAndFileIsCompensated`, 단위 `statusRejectedAfterLockRollsBackAndDeletesFile` | 통과 |
| AC-VER-07 | `invalidFilesAreRejectedLikeDocumentCreation`, `DocumentVersionUploadSizeIntegrationTest`(실제 HTTP 20,971,520 → 201·SHA-256 일치, +1 → 413) | 통과 |
| AC-VER-08 | `DocumentVersionStorageIntegrationTest.scenarioB_…`, `storageFailureLeavesNoRowsAndNoTempFiles`, `storageIoErrorLeavesNoRows` | 통과 |
| AC-VER-09 | `scenarioA_databaseFailureRollsBackAndDeletesOnlyThisRequestsFile`, 단위 `insertFailureRollsBackAndDeletesFile` | 통과 |
| AC-VER-10 | `unexpectedVersionNumberUniqueViolationIsServerErrorWithCauseLog`(실제 PostgreSQL 위반, §13.2), 단위 `versionNumberConflictLogsCauseAndRethrows` | 통과 |
| AC-VER-11 | 단위 `commitOutcomeUnknownKeepsFile`, `rollbackFailureKeepsFile`, `transactionBeginFailureDeletesFile`, `compensationFailure…` 2개 + 통합 `compensationFailureKeepsOriginalErrorAndLeavesOrphan` | 통과 |
| AC-VER-12 | `outsidersAndForeignDocumentsCannotBeDistinguished` | 통과 |
| AC-VER-13 | `sameFileAgainCreatesNewVersionWithDifferentStorageKey` | 통과 |
| AC-VER-14 | `securityAndRequestFormatErrorsFollowExistingContract` | 통과 |
| AC-VER-15 | 응답 필드 수·내부 정보 단언, `handler().handlerType(DocumentVersionController)`·기존 조회는 `DocumentController` | 통과 |
| AC-VER-16 | `DocumentVersionUploadForm.test.tsx`(22), `DocumentDetailVersionUpload.test.tsx`(5), `version-api.test.ts`(2) | 통과 |
| AC-VER-17 | 클라우드 E2E 19/19 (아래) | 통과 |
| AC-VER-18 | Windows 자동 테스트·빌드 (§17.5) | `clean build --rerun-tasks`(동시성·크기 테스트 포함 1회)와 Frontend lint·`tsc`·Vitest·build 성공. **동시성·크기 테스트 반복 실행과 테스트 DB 이름(`docflow_test`) 확인은 보고에 없어 미기록** |
| AC-VER-19 | SPEC-03 포함 기존 254개·Frontend 169개 수정 없이 통과(기존 테스트 파일 중 `ProjectPermissionServiceTest`에는 케이스 1개만 추가), `SecurityConfig` 변경 없음, CI 성공 | 통과 (최종 커밋 CI는 PR에서 재확인) |

**잠금 대기 측정 (클라우드, OQ-4·I4, 판정 기준 없음)** — `clean build` 실행 기준:

- 결정적 직렬화: 첫 요청 v2 54ms, 둘째 v3 39ms(요청 시작~응답, 차단 연결 보유 시간 포함), 잠금 해제 후 두 요청 완료까지 17ms.
- 병렬 N=8: 전체 114ms, 요청별 최소 42ms·중앙값 71ms·최대 114ms, 동시 잠금 대기 최대 5개(표본 조사). 같은 테스트 단독 실행 때는 전체 154ms, 최대 대기 7개.

**클라우드 E2E (Chromium → Next.js `next start` → Spring Boot jar → PostgreSQL)** — 19개 확인 모두 통과:

1. OWNER 새 버전 → 상세 v2 갱신·성공 문구, 요청에 `X-XSRF-TOKEN`·multipart, 목록 `v2` 반영
2. 작성자가 아닌 MEMBER 새 버전 v3, `uploaded_by` = MEMBER, `created_by` 불변
3. 19MiB·20MiB 새 버전 크기·SHA-256(DB·저장 파일) 일치 (각 약 1초)
4. 20MiB+1 화면 사전 차단, 요청 없음
5. 비구성원 화면 "프로젝트를 찾을 수 없습니다." + API `404 PROJECT_NOT_FOUND`, 저장 없음
6. `IN_REVIEW`·`RE_REVIEW`·`APPROVED`(DB 설정): 파일 입력·버튼 비활성화와 안내 문구, 브라우저 직접 호출 `409`(본문 키 `code`·`message`, 상태 값 없음)
7. 화면 표시 후 DB에서 `IN_REVIEW`로 변경 → 제출 `409` → 문구 유지, 로딩 없이 상태 "검토 중" 반영·비활성화 (I6)
8. 연결 리셋(`ERR_CONNECTION_RESET`)·브라우저 오프라인(`ERR_INTERNET_DISCONNECTED`) → OQ-8 문구, 요청 각 1회(자동 재시도 없음), 새 버전 없음
9. 같은 파일 재등록 → v4, SHA-256 같고 저장 키 다름
10. Next.js 경유 동시 8개 → 모두 `201`, v2~v9, CSRF 헤더 없는 요청 `403 CSRF_INVALID`
11. 종료 후 DB 저장 키 34개 = 저장소 파일 34개, 임시 파일 없음

### 17.4 미실행·남은 항목

- AC-VER-18 중 동시성·크기 테스트 반복 실행과 테스트 DB 이름 확인: 보고되지 않아 기록하지 않았다(§17.5).
- Windows 실제 브라우저 수동 E2E: AC 미포함, 후속 Issue 생성은 별도 승인(OQ-9).
- CI에서의 잠금 대기 측정값: CI가 테스트 표준 출력을 남기지 않아 기록하지 못했다.
- D4(21MiB 초과 요청의 Next.js 경유 연결 리셋, #18)는 새 버전 업로드에도 같고 이번 범위에서 다루지 않았다.

### 17.5 Windows 자동 검증 (사용자, 2026-11-04)

사용자가 보고한 결과만 기록한다.

| 항목 | 명령 | 결과 |
| --- | --- | --- |
| Backend | `.\gradlew.bat clean build --rerun-tasks` | BUILD SUCCESSFUL (약 3분, Gradle 작업 8개 실행). 개별 테스트 수는 보고에 없음 |
| Frontend 설치 | `npm ci` | 성공 |
| Frontend lint | `npm run lint` | 성공 |
| Frontend 타입 검사 | `npx tsc --noEmit` | 성공 |
| Frontend 테스트 | `npx vitest run` | 198/198 통과 |
| Frontend 빌드 | `npm run build` | 성공 |
| 동시성·크기 테스트 반복 실행 (§11.7) | — | 보고 없음 |
| 테스트 DB 이름(`docflow_test`) 확인 | — | 보고 없음 |
| 잠금 대기 측정값 (OQ-4) | — | 보고 없음 |

- 검증 커밋은 보고에 명시되지 않았다. 보고 시점 원격 브랜치 HEAD는 `1026afc`이며 제품·테스트 코드는 `e8c5bd0`과 같다.

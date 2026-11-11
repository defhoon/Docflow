# SPEC-05: 문서 버전 조회·다운로드·미리보기

- 상태: **구현·클라우드·CI 검증 완료 (2026-11-04). Windows 검증(AC-VDL-22) 대기. 설계 확정 v0.3(Q1~Q5, P1~P7, A1~A4) 기준. PR 미생성**
- Issue: [#21](https://github.com/defhoon/Docflow/issues/21)
- 브랜치: `feat/21/document-version-download`
- 관련 문서: [`spec.md`](../spec.md) §3, §4.1, §8 US-05, §9, §11, §12, §14, §16.2 / [`specs/03-document-create.md`](./03-document-create.md) §5.4~§5.7, §5.6(D4·프록시 타임아웃), §13 R8 / [`specs/04-document-version-create.md`](./04-document-version-create.md) §5, §6.3(OQ-1), §14 / [`specs/02-project-members.md`](./02-project-members.md) §7.2(D5) / [ADR-0006](../docs/adr/0006-document-preview-conversion.md)(미리보기 변환 엔진), [ADR-0001](../docs/adr/0001-technology-stack.md) Q2, [ADR-0005](../docs/adr/0005-session-authentication.md) / [`CLAUDE.md`](../CLAUDE.md) §2~§6
- 작성일: 2026-11-04 (기준 커밋: `main` `bb43b70`, PR #20 병합)
- 변경 이력:
  - 구현 (2026-11-04): Phase 1~4 구현·검증, 결과를 §18에 기록. 구현 중 확인한 사항 반영: §7.4 iframe `sandbox` 미사용 근거와 PDF 뷰어 표시 옵션(`#navpanes=0&view=FitH`), §9.1 히스토리 목록 조회 실패는 `role="status"`, §16.2 기술 확인 결과. 정책(Q·P·A) 변경 없음
  - v0.3 (2026-11-04): A1(LibreOffice 프로세스 직접 실행, `DocumentPreviewConverter` 인터페이스)·A2(최초 요청 시 비동기 변환, `202` + 폴링)·A3(버전별 캐시 + 전체 용량 상한 + 미사용 캐시 정리, 원본 삭제 금지, 사용 중 캐시 보호)·A4(CI·Windows 실제 변환 테스트 필수) 결정 반영. 변환 실행 규칙(작업별 디렉터리·프로필, 인수 배열, 최소 권한, 네트워크 제한, 정리), PDF 유효성 검증 방식, AC·테스트 계획 확정. ADR-0006 작성. Issue #21·브랜치 생성
  - v0.2 (2026-11-04): 미리보기를 이 SPEC에 포함(Q1 추천안 미채택). Q2~Q5 결정 반영. 변환 엔진 실측과 대안 비교
  - v0.1 (2026-11-04): 실제 코드 분석을 바탕으로 한 설계 초안(미리보기 제외 추천)

표기: **[기존]** 이미 구현·확정된 내용, **[결정]** 사용자가 확정한 사항, **[제안]** 이 SPEC의 설계안(구현 세부), **[검증 필요]** 구현 단계에서 실제 실행으로 확인할 사항, **[실측]** 설계 중 클라우드에서 실제 실행한 결과.

---

## 1. 목적 및 배경

SPEC-03·04로 문서와 버전 파일을 등록할 수 있지만 사용자는 최신 버전의 메타데이터만 볼 수 있다. 이전 버전이나 파일 내용을 확인할 방법이 없다. 이후 Review(SPEC-06~)에서 검토자는 특정 버전의 내용을 봐야 한다. SPEC-05는 버전 히스토리, 버전별 원본 다운로드, 버전별 미리보기를 제공한다.

## 2. 목표

- 구성원이 문서의 모든 버전(번호·원본 파일명·업로더·업로드 시각·크기)을 확인한다.
- 구성원이 특정 버전의 원본 파일을 손상 없이 내려받는다.
- 구성원이 특정 버전을 PDF·DOCX·XLSX·PPTX 모두 브라우저 안에서 미리본다.
- 미리보기는 원본을 바꾸지 않으며, 미리보기가 실패해도 원본 다운로드는 항상 쓸 수 있다.
- 저장 키·서버 경로·캐시 경로를 노출하지 않고, 브라우저와 서버 모두에서 신뢰할 수 없는 파일을 안전하게 다룬다.

## 3. 범위

### In Scope

- 버전 목록 API, 원본 다운로드 API, 미리보기 API
- Office(DOCX·XLSX·PPTX) → PDF 서버 변환(LibreOffice), 버전별 결과 캐시(용량 상한·미사용 정리), 시간·동시 실행·대기열·결과 크기 제한, 실패·재시작 정리
- 문서 상세 화면의 버전 히스토리, 버전별 다운로드·미리보기 UI
- SPEC-04 새 버전 등록 후 히스토리 갱신
- `FileStorage` 읽기 기능, 변환 엔진 실행 환경(클라우드·CI·Windows·배포 요건), CI LibreOffice 설치, ADR-0006

### Out of Scope

| 항목 | 담당 |
| --- | --- |
| 버전 비교(Diff), AI 변경 요약 | Post-MVP (`spec.md` §4.2) |
| 버전·문서 삭제, 버전 복원 | `spec.md` §16.2 미결정 |
| 업로드 시점 사전 변환 | 이번 범위 아님 [결정 A2] |
| 미리보기 안 주석·하이라이트·페이지 링크 | Feedback 위치 연결 결정(`spec.md` §16.2) 이후 |
| 버전 단건 메타데이터 API | 필요 시 SPEC-06 [결정 P7] |
| Review 대상 버전 지정, Feedback–버전 연결 | SPEC-06, SPEC-08 |
| 다운로드·미리보기 이력(Audit Log) | SPEC-13 |
| 악성 코드 검사, 다중 인스턴스 작업 분산 | 범위 밖 |

## 4. 사용자 시나리오

1. 구성원이 문서 상세에 들어가면 "버전 히스토리"가 최신 버전부터 보인다. 각 행에 `v{N}`(최신 표시), 원본 파일명, 업로더, 업로드 시각, 크기, "미리보기"·"다운로드" 버튼이 있다.
2. "미리보기"를 누르면 히스토리 아래 미리보기 영역에 해당 버전이 열린다. PDF는 바로, Office 파일은 처음 한 번 변환하는 동안 "미리보기를 준비하는 중입니다"가 보인 뒤 열린다. 같은 버전을 다시 보면 변환 없이 바로 열린다.
3. 변환이 실패하거나 60초 안에 끝나지 않으면 "미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요."와 다운로드 버튼이 보인다.
4. "다운로드"는 원본 파일을 원래 파일명으로 저장한다.
5. 새 버전을 등록하면(SPEC-04) 히스토리 맨 위에 새 버전이 나타난다.
6. 비구성원, 다른 프로젝트의 문서·버전, 없는 버전은 존재 여부를 알 수 없다(404).

## 5. 도메인 및 데이터 모델

### 5.1 스키마 [기존, 변경 없음]

- `document_versions`(V5)를 그대로 쓴다. 마이그레이션 없음. 미리보기 상태는 저장소의 캐시 파일과 메모리로 관리한다(§8). 이유: 버전 파일은 바뀌지 않으므로(SPEC-04 V2) 캐시 파일 존재가 곧 완료 상태이고, MVP는 단일 인스턴스다(R11).

### 5.2 버전 식별 [결정 P1]

- 외부 식별자는 문서 안의 `versionNumber`. 소속 확인 순서: `projectId`(접근) → `documentId`+`projectId` → `documentId`+`versionNumber` (IDOR 방어).

### 5.3 업로더 표시 [결정 Q5]

- 버전 목록 항목에 업로더 `{ id, displayName }`. `DocumentResponse.latestVersion`에는 추가하지 않는다.

## 6. Backend API 명세

### 6.1 공통 [기존 재사용]

- 세 API 모두 `GET`이므로 CSRF 토큰이 필요 없다. `SecurityConfig` 변경 없음. 오류 형식 `{ code, message, fieldErrors? }`.
- 문서 상태와 관계없이 조회·다운로드·미리보기할 수 있다 [결정 P6].

### 6.2 엔드포인트

| 메서드·경로 | 성공 |
| --- | --- |
| `GET /api/projects/{projectId}/documents/{documentId}/versions` | `200`, 버전 배열 |
| `GET …/versions/{versionNumber}/download` | `200`, 원본 파일 |
| `GET …/versions/{versionNumber}/preview` | `200` 미리보기 PDF / `202` 준비 중 / 실패 시 오류 |

- 모두 [기존] `DocumentVersionController`에 둔다.

### 6.3 버전 목록 [결정 P2]

```json
[
  { "versionNumber": 3, "originalFilename": "요구사항-v3.docx", "sizeBytes": 524288,
    "uploadedBy": { "id": 2, "displayName": "Reviewer" }, "uploadedAt": "2026-11-05T10:00:00Z", "latest": true }
]
```

- `versionNumber DESC`, 페이지네이션 없음, 첫 항목만 `latest`. 저장 키·경로·SHA-256·`content_type`·이메일·내부 ID 미포함. 업로더는 한 번의 쿼리로 함께 조회(N+1 없음).

### 6.4 원본 다운로드 [결정 P3, P4, P5]

| 헤더 | 값 |
| --- | --- |
| `Content-Type` | 저장된 서버 판별 `content_type` |
| `Content-Disposition` | `attachment; filename="<ASCII 대체>"; filename*=UTF-8''<인코딩된 원본 파일명>` (Spring `ContentDisposition`으로 생성) |
| `Content-Length` | 실제 파일 크기 |
| `X-Content-Type-Options` / `Cache-Control` / `X-Frame-Options` | [기존] Spring Security 기본값(`nosniff` / `no-cache, no-store, …` / `DENY`) 유지 |
| `Content-Security-Policy` | `default-src 'none'; sandbox` |

- 처리 순서: [readOnly 트랜잭션] 접근·문서·버전 확인 → 트랜잭션 종료 → 파일 열기·크기 비교(실패 시 `500 DOCUMENT_FILE_UNAVAILABLE`, 헤더 전송 전) → 고정 버퍼 스트리밍 → 닫기.
- `Accept` 값과 관계없이 동작해야 한다 [검증 필요: `Accept: application/json` 요청의 동작]. Frontend는 다운로드·미리보기에 `Accept: */*`를 보내는 전용 함수를 쓴다.

### 6.5 미리보기 [결정 A2]

| 상황 | 응답 |
| --- | --- |
| 원본이 PDF | `200 application/pdf`, 원본 파일 스트리밍(변환·캐시 없음) |
| Office, 캐시 있음 | `200 application/pdf`, 캐시 PDF 스트리밍 |
| Office, 캐시 없음·변환 대기·진행 중 | `202 Accepted`, `Retry-After: 2`, 본문 `{ "status": "PENDING" }`. 진행 중인 작업이 없으면 이 요청이 작업을 등록한다 |
| 변환 실패(엔진 오류·손상 파일·유효하지 않은 결과·결과 100MiB 초과) | `422 DOCUMENT_PREVIEW_FAILED` "미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요." |
| 60초 시간 초과 | `422 DOCUMENT_PREVIEW_FAILED` (같은 본문) |
| 변환 엔진을 찾을 수 없음·실행 불가 | `503 DOCUMENT_PREVIEW_UNAVAILABLE` "지금은 미리보기를 사용할 수 없습니다. 원본 파일을 내려받아 확인해 주세요." |
| 대기열(10개) 가득 참 | `503 DOCUMENT_PREVIEW_UNAVAILABLE` + `Retry-After` |
| 원본 파일 누락·크기 불일치 | `500 DOCUMENT_FILE_UNAVAILABLE` [결정 Q3] |

- `200` 헤더: `Content-Type: application/pdf`, `Content-Disposition: attachment; filename*=…<원본 이름>.pdf`, `Content-Length`, `CSP: default-src 'none'; sandbox`, [기존] `nosniff`·`no-store`·`X-Frame-Options: DENY` 유지. URL을 직접 열어도 렌더링하지 않고 저장된다. 화면 표시는 Frontend가 Blob으로 처리한다(§9.3).
- 미리보기 결과는 원본과 다른 경로(`previews/`)에 저장하며 원본 파일·DB 행을 바꾸지 않는다.
- `GET`이 작업 등록 부수 효과를 가진다. 결과는 같은 버전에 대해 항상 같고 사용자 데이터를 바꾸지 않으므로 `GET`을 유지한다(대안 `POST`+`GET`은 CSRF와 요청 수 증가로 채택하지 않음).

### 6.6 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| [기존] `AUTH_REQUIRED` / `BAD_REQUEST` | 401 / 400 | 미인증 / 숫자가 아닌 경로 변수 |
| [기존] `PROJECT_NOT_FOUND` / `DOCUMENT_NOT_FOUND` | 404 | 접근 불가 프로젝트 / 이 프로젝트에 없는 문서 |
| **[신규] `DOCUMENT_VERSION_NOT_FOUND`** | 404 | 이 문서에 없는 버전 번호 |
| **[신규] `DOCUMENT_FILE_UNAVAILABLE`** | 500 | 원본 파일 누락·열기 실패·크기 불일치 [결정 Q3] |
| **[신규] `DOCUMENT_PREVIEW_FAILED`** | 422 | 변환 실패·시간 초과·결과 무효·결과 크기 초과 |
| **[신규] `DOCUMENT_PREVIEW_UNAVAILABLE`** | 503 | 엔진 없음·실행 불가·대기열 가득 참 |

## 7. 권한과 보안

### 7.1 권한 [기존 원칙]

- 세 API 모두 OWNER·MEMBER 허용, 미인증 401, 비구성원 `404 PROJECT_NOT_FOUND`. [기존] `ProjectService.getAccessibleProject()`로 확인한다.
- **캐시 접근 제어:** 캐시 PDF는 미리보기 API가 접근·문서·버전 확인을 통과한 뒤에만 읽는다. 캐시 디렉터리는 정적 파일로 제공되지 않으며, 캐시 파일 이름(저장 키의 SHA-256)도 응답에 나오지 않는다. 변환 작업은 버전 단위로 공유되지만 결과 전달은 요청마다 권한을 확인한다.
- 구성원 제거 이후 요청은 404([기존] SPEC-02 D5). 이미 시작된 변환은 끝까지 진행되어 캐시에 남는다.

### 7.2 내부 정보 비노출

- 응답·오류 본문에 서버 경로·저장 키·캐시 경로·변환 엔진 출력을 넣지 않는다. 로그에는 `projectId`·`documentId`·`versionNumber`·`storageKey`, 변환 소요 시간·종료 코드·실패 유형만 남기고 원본 파일명과 엔진 출력 전문은 남기지 않는다(엔진 출력은 마지막 일부만 DEBUG).

### 7.3 서버 측 변환 보안 [결정 A1, ADR-0006]

| 위협 | 대응 |
| --- | --- |
| 셸 주입·인수 조작 | 셸을 거치지 않고 인수 배열로 실행. 원본 파일명은 인수·작업 경로에 쓰지 않고 `input.<ext>`로 복사 |
| 작업 간 간섭·프로필 손상 | 변환마다 독립 작업 디렉터리와 독립 LibreOffice 사용자 프로필(`-env:UserInstallation`) |
| 매크로 실행 | 작업 프로필에 매크로 실행 비활성화·최고 보안 수준 설정을 미리 넣음 [검증 필요: 설정 키] |
| 외부 링크·원격 리소스로 인한 서버 측 요청·로컬 파일 접근 | 프로필에 링크 갱신 비활성화. 실행 래퍼(`docflow.preview.launcher`)로 OS 수준 네트워크 차단(Linux 네트워크 namespace). [실측] 클라우드에서 `unshare -rn` 안의 변환 성공·네트워크 차단 확인 |
| 권한 남용 | Backend·변환 프로세스는 비특권 전용 사용자로 실행(배포 요건). 자식 프로세스 환경 변수를 비우고 최소 값만 전달(DB 비밀번호 등 미전달) |
| 자원 고갈 | 업로드 20MiB·[기존] ZIP 자원 상한, 시간 60초, 동시 1개, 대기열 10개, 결과 100MiB, 캐시 용량 상한 |
| 프로세스 잔존 | 시간 초과·Backend 종료 시 프로세스 트리 강제 종료(Windows 하위 프로세스 포함) |
| 결과물 신뢰 | 종료 코드 0, 결과 파일 1개, `%PDF-` 헤더, 크기 상한 확인 후 캐시에 원자적 이동 |
| OS 격리가 없는 환경 | Windows, 사용자 namespace가 막힌 Linux는 프로필 설정·작업 디렉터리 격리만 적용 → 잔여 위험(R2), ADR-0006에 명시 |

### 7.4 브라우저 측 표시 보안 [제안]

- 미리보기 PDF는 `fetch`로 받아 `type`을 코드에서 `application/pdf`로 고정한 Blob을 만들고, Blob URL을 같은 화면의 `<iframe>`에 넣어 브라우저 내장 PDF 뷰어로 표시한다. 응답 `Content-Type`이 `application/pdf`가 아니면 표시하지 않는다.
- `X-Frame-Options`는 HTTP 응답 헤더라 Blob URL 문서에 적용되지 않으므로 [기존] `X-Frame-Options: DENY`·`SecurityConfig`를 바꾸지 않는다.
- `<iframe sandbox>`: **사용하지 않는다** [확인 2026-11-04]. 실제 브라우저 E2E에서 `sandbox` 값(빈 값, `allow-scripts`, `allow-same-origin`, `allow-scripts allow-same-origin`)을 주면 모두 Chromium 내장 PDF 뷰어가 렌더링되지 않았고, 속성이 없을 때만 렌더링되었다(§18.4). 보호는 Blob 형식 고정(`application/pdf`), 비 PDF 응답 거부, 서버 응답의 `attachment`·`CSP sandbox`·`nosniff`, 브라우저 PDF 뷰어 격리에 의존한다.
- PDF 뷰어 표시 옵션: Blob URL에 `#navpanes=0&view=FitH`(썸네일 패널 닫기, 폭 맞춤)를 붙인다. 문서 상세의 좁은 영역(약 620px)에서 패널이 열려 있으면 Chromium이 본문 페이지를 그리지 않는 것을 확인했다(§18.4). 지원하지 않는 브라우저는 무시한다.
- Blob URL은 미리보기를 닫거나 다른 버전으로 바꿀 때, 화면이 숨겨지거나 언마운트될 때 해제한다.
- 브라우저 내장 뷰어가 E2E에서 동작하지 않으면 PDF.js(`pdfjs-dist`) 도입을 별도 승인 요청한다(R7).

## 8. Backend 구조 [제안]

### 8.1 구성 요소

| 구성 요소 | 변경 |
| --- | --- |
| `FileStorage`·`LocalFileStorage` | 원본 읽기(`openForRead`, 크기) 추가. 기존 쓰기·삭제 동작 변경 없음 |
| `PreviewCache` (신규) | `previews/cache/` 아래 캐시 파일 관리: 조회·사용 중 표시·등록(원자적 이동)·용량 정리·미사용 정리. `previews/` 밖의 파일은 다루지 않는다 |
| `DocumentPreviewConverter` (신규 인터페이스) | `convertToPdf(Path input, DocumentFileType type, Path workDir, Duration timeout)` |
| `LibreOfficePreviewConverter` (신규) | ADR-0006 실행 규칙 구현 |
| `DocumentPreviewService` (신규) | 상태 판단, 작업 등록·공유(single-flight), 실행기(동시 1, 대기열 10), 실패 기록 |
| `DocumentVersionRepository` | 목록(업로더 함께, DESC), `(documentId, versionNumber)` 단건 |
| `DocumentVersionService` | 목록, 다운로드 준비. [기존] `create` 변경 없음 |
| `DocumentVersionController` | GET 3개 추가 |
| `ErrorCode` | 4개 추가(§6.6) |
| `GlobalExceptionHandler` | 클라이언트 연결 중단을 ERROR로 남기지 않음 [검증 필요] |
| 설정 | `docflow.preview.*` (§8.3) |
| `SecurityConfig`, 마이그레이션 | 변경 없음 |

### 8.2 변환 작업 흐름

```text
GET preview → 접근·문서·버전 확인(readOnly 트랜잭션, 종료)
→ PDF 원본? → 원본 스트리밍
→ 캐시 있음? → 사용 중 표시(참조 수 +1) → 캐시 스트리밍 → 참조 수 -1, 마지막 사용 시각 갱신
→ 실패 기록 있음(재시도 시간 전)? → 422
→ 같은 버전 작업 대기·진행 중? → 202 (작업 공유)
→ 대기열 여유 없음? → 503
→ 작업 등록 → 202
[실행기: 동시 1개]
   previews/work/<작업 UUID>/{in,out,profile} 생성 → 원본을 in/input.<ext>로 복사
   → (래퍼 +) soffice 인수 배열 실행, 60초 제한
   → 결과 검증(종료 코드 0, out/input.pdf 하나, %PDF-, ≤100MiB)
   → previews/cache/<sha256(storageKey)>.pdf로 원자적 이동 → 캐시 등록 → 용량 정리
   실패·시간 초과 → 프로세스 트리 종료 → 실패 기록
   항상 → 작업 디렉터리 삭제
```

### 8.3 자원 제한·설정 기본값 [결정]

| 설정 | 기본값 | 근거 |
| --- | --- | --- |
| `docflow.preview.timeout` | 60초 | [결정] 사용자 승인. [실측] 일반 문서 2~6초 |
| `docflow.preview.concurrency` | 1 | [결정] |
| `docflow.preview.queue-capacity` | 10 | [결정] 초과 시 503 |
| `docflow.preview.max-output-bytes` | 100MiB | [결정] |
| `docflow.preview.soffice-path` (`DOCFLOW_PREVIEW_SOFFICE_PATH`) | PATH의 `soffice`, Windows 기본 설치 경로 후보 | ADR-0006 |
| `docflow.preview.launcher` | 빈 값(래퍼 없음) | 환경별로 OS 격리 래퍼 지정 |
| `docflow.preview.cache.max-bytes` | 2GiB | [결정 A3] 전체 캐시 용량 상한, 설정 가능 |
| `docflow.preview.cache.max-idle` | 30일 | [결정 A3] 이 기간 사용하지 않은 캐시 정리 |
| `docflow.preview.retry-after-failure` | 10분 | 시간 초과·엔진 오류 후 재시도 허용 간격 |

- 설정 검증: `cache.max-bytes`는 `max-output-bytes` 이상이어야 한다(아니면 시작 실패).
- Frontend 폴링: 2초 간격(또는 `Retry-After`), 최대 90초.

### 8.4 캐시 정책 [결정 A3]

- **키·위치:** 버전의 `storage_key` SHA-256을 이름으로 `previews/cache/<hash>.pdf`. 버전 파일이 불변이므로 캐시 무효화가 필요 없다.
- **사용 기록:** 메모리 색인(`크기`, `마지막 사용 시각`, `참조 수`). 캐시를 전달할 때 마지막 사용 시각을 갱신하고 파일 수정 시각에도 반영한다(재시작 후 복원용). 시작 시 `previews/cache/`를 읽어 색인을 다시 만든다.
- **용량 상한:** 새 캐시를 등록한 뒤 전체 크기가 `cache.max-bytes`를 넘으면 마지막 사용 시각이 오래된 순으로 삭제해 상한의 90% 이하로 줄인다(LRU).
- **미사용 정리:** 1시간마다 `cache.max-idle` 동안 사용하지 않은 캐시를 삭제한다.
- **충돌 방지:**
  - 색인 변경(등록·삭제·참조 수)은 하나의 잠금 안에서 처리한다.
  - 전송 중(참조 수 > 0)이거나 해당 버전 변환 작업이 진행 중인 캐시는 삭제하지 않는다. 전송은 참조 수를 올린 뒤 파일을 연다.
  - 삭제할 수 있는 캐시만으로 상한을 맞추지 못하면 상한을 일시적으로 넘긴 채 WARN 로그를 남기고 다음 정리 때 다시 시도한다. Windows에서 열린 파일 삭제가 실패하면 다음 정리 때 다시 시도한다.
  - 삭제된 캐시는 다음 미리보기 요청 때 다시 변환된다.
- **원본 보호:** 정리는 `previews/cache/` 안의 `.pdf`만 대상으로 하며, 경로 확인으로 `documents/`(원본)를 절대 삭제하지 않는다.

### 8.5 실패·종료·재시작 정리 [제안]

| 상황 | 처리 |
| --- | --- |
| 변환 실패(손상·무효 결과·크기 초과) | 실패 기록(재시작 전까지 유지), 작업 디렉터리 삭제 |
| 시간 초과·엔진 비정상 종료 | 프로세스 트리 강제 종료 후 대기, 실패 기록(10분 뒤 재시도 허용), 작업 디렉터리 삭제 |
| 엔진 없음 | 작업 등록 전에 `503` |
| Backend 정상 종료 | 실행기 종료, 실행 중 프로세스 트리 강제 종료, 작업 디렉터리 삭제 |
| Backend 비정상 종료·재시작 | 메모리의 작업·실패 기록 소실. 시작 시 `previews/work/` 전체 삭제, 캐시 색인 재구성. 다음 요청에서 다시 변환. 강제 종료로 남은 LibreOffice 프로세스는 래퍼(namespace) 사용 시 함께 종료되며, 래퍼가 없으면 남을 수 있음(R3) |
| 디스크 쓰기 실패 | 실패 기록(재시도 허용), 부분 파일 삭제 |

## 9. Frontend [제안]

### 9.1 화면

- [기존] 문서 상세에 "버전 히스토리"(메타데이터·설명 아래, [기존] "새 버전 등록" 위)와 "미리보기" 영역(히스토리 아래)을 추가한다. 별도 경로는 만들지 않는다.
- 히스토리 행: `v{N}`(최신 표시), 원본 파일명, 업로더, 업로드 시각, 크기, "미리보기"·"다운로드" 버튼.
- 미리보기 영역: 선택 안 함 → 불러오는 중 / 변환 중("미리보기를 준비하는 중입니다. 파일에 따라 최대 1분 정도 걸릴 수 있습니다.") → 표시(`<iframe title="v{N} 미리보기">`, 높이 약 80vh, "닫기", "미리보기는 참고용이며 원본 파일이 기준입니다" 안내) → 실패(문구 + 해당 버전 다운로드 버튼).
- 미리보기는 자동으로 열지 않고 사용자가 누른 버전만 연다.
- 히스토리 목록 조회 실패는 영역 안에 "목록 다시 불러오기"와 함께 `role="status"`(정중한 알림)로 표시한다. 사용자 동작의 결과가 아닌 배경 조회 실패이기 때문이다. 다운로드·미리보기 실패는 `role="alert"`.

### 9.2 SPEC-04 연동

- 새 버전 등록 성공 시 버전 목록을 다시 조회한다. 409 백그라운드 재조회(SPEC-04 I6) 때도 함께 갱신한다. 열려 있던 미리보기는 유지한다.

### 9.3 다운로드·미리보기 요청 [결정 Q2]

- 전용 함수(`Accept: */*`, `credentials: same-origin`, `cache: no-store`)로 `fetch` → Blob.
  - 다운로드: 목록의 `originalFilename`으로 `<a download>`를 만들어 클릭 후 Blob URL 해제.
  - 미리보기: `200`이면 PDF Blob을 iframe에 표시, `202`면 `Retry-After`(없으면 2초) 뒤 다시 요청, 최대 90초. 다른 버전 선택·닫기·화면 이탈 시 `AbortController`로 중단.
- 버튼별 진행 중 비활성화. 다운로드 자동 재시도 없음.

### 9.4 오류 표시

| 응답 | 표시 |
| --- | --- |
| `401` | [기존] 세션 만료 흐름 |
| `404` 프로젝트/문서/버전 | "프로젝트를 찾을 수 없습니다." / "문서를 찾을 수 없습니다." / "버전을 찾을 수 없습니다." |
| `500 DOCUMENT_FILE_UNAVAILABLE` | "파일을 불러올 수 없습니다. 관리자에게 문의해 주세요." |
| `422 DOCUMENT_PREVIEW_FAILED`, 폴링 90초 초과 | "미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요." + 다운로드 버튼 |
| `503 DOCUMENT_PREVIEW_UNAVAILABLE` | "지금은 미리보기를 사용할 수 없습니다. 원본 파일을 내려받아 확인해 주세요." + 다운로드 버튼 |
| 그 밖의 `5xx` / 네트워크 | [기존] 서버 오류 문구 / "파일을 불러오지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요." |

## 10. 미리보기 방식 결정 근거

### 10.1 PDF 표시

| 안 | 판단 |
| --- | --- |
| **브라우저 내장 뷰어 + Blob iframe (채택)** | 의존성 없음, 보안 헤더 유지. 브라우저 차이·headless 확인 한계는 E2E로 검증 |
| PDF.js(`pdfjs-dist`) | 내장 뷰어가 E2E에서 실패할 때만 별도 승인 후 검토 |

### 10.2 Office 변환 엔진 [결정 A1]

- LibreOffice 프로세스 직접 실행, `DocumentPreviewConverter` 인터페이스로 분리. 대안 비교와 실측은 ADR-0006.
- [실측] DOCX 37KB 4.4초, XLSX 540KB 6.4초, PPTX 103KB 2.2초, DOCX 8.8MB는 120초 안에 끝나지 않음. 네트워크 namespace + 환경 변수 정리 + 독립 프로필에서 DOCX 3.0초, 1페이지 PDF.
- 60초 안에 변환하지 못한 문서는 미리보기 실패로 안내하고 원본 다운로드를 제공한다. 목표 "4개 형식 미리보기"는 제한 안에서 변환 가능한 파일에 대해 보장한다.

### 10.3 실행 환경 [결정 A4, ADR-0006]

| 환경 | LibreOffice | 네트워크 제한 래퍼 | 실제 변환 테스트 |
| --- | --- | --- | --- |
| Claude Code 클라우드 | 설치됨(24.2.7) | `unshare -rn` 사용 가능 [실측] | 실행 |
| GitHub Actions `ubuntu-latest` | 워크플로에 설치 단계 추가(Writer·Calc·Impress, `fonts-noto-cjk`) | 비특권 사용자 namespace 가능 여부 [검증 필요], 가능하면 적용 | **필수** |
| Windows 로컬 | 개발자가 설치(`DOCFLOW_PREVIEW_SOFFICE_PATH` 또는 기본 경로) | 없음 → 잔여 위험 | **필수** |
| 배포 | 실행 환경에 설치, 비특권 사용자, 래퍼 또는 컨테이너 권장 | 환경에 따름 | — |

### 10.4 변환 시점 [결정 A2]

- 최초 미리보기 요청 시 비동기 변환, `202` + 폴링. 요청이 짧아 [기존] Next.js 프록시 30초 제한과 무관하다.

## 11. 예외 및 오류 처리 (`spec.md` §12 검토)

| 시나리오 | Backend | Frontend |
| --- | --- | --- |
| 미인증 / 비구성원 / 다른 프로젝트 문서 / 다른 문서·없는 버전 | 401 / 404 / 404 / 404 | 각 안내 |
| 원본 누락·크기 불일치 | `500 DOCUMENT_FILE_UNAVAILABLE`, ERROR 로그 | 파일 불가 문구 |
| 다운로드 전송 중 읽기 실패 / 클라이언트 중단 | 연결 종료, ERROR 로그 / ERROR 아님 | 네트워크 문구 / — |
| 변환 실패·60초 초과·결과 무효·100MiB 초과 | `422`, 실패 기록, 프로세스·작업 디렉터리 정리 | 실패 문구 + 다운로드 |
| 엔진 없음·대기열 가득 | `503` | 사용 불가 문구 + 다운로드 |
| 같은 버전 동시 미리보기 요청 | 작업 1개 공유 | 각자 폴링 |
| 캐시 정리와 전송·변환 동시 발생 | 사용 중 캐시 삭제 안 함 | — |
| 변환 중 서버 재시작 | 작업 소실, 시작 시 작업 디렉터리 정리, 다음 요청에서 재변환 | 폴링 실패 → 다시 시도 |
| 변환 중 새 버전 등록 | 영향 없음(버전 불변) | 목록 갱신 |
| 잘못된 상태 전이 | 해당 없음 | — |

## 12. 테스트 계획

### 12.1 Backend 단위

| 대상 | 내용 |
| --- | --- |
| 다운로드 헤더 | 한글·공백·`"`·`;`·이모지·255자 파일명의 `filename*`·ASCII 대체값 |
| `LocalFileStorage` 읽기 | 정상, 없음, 루트 이탈 거부, 스트림 닫힘 |
| `PreviewCache` | 등록(원자적 이동), LRU 용량 정리(상한 90%까지), 미사용 정리, 참조 중·변환 중 캐시 보호, 상한을 못 맞출 때 WARN, `documents/` 경로 삭제 불가, 시작 시 색인 재구성 |
| `DocumentPreviewService` (가짜 변환기) | single-flight, 동시 1개, 대기열 10 초과 `503`, 실패 기록·재시도 간격, 결과 무효(`%PDF` 아님)·크기 초과 거부, 작업 디렉터리 정리, 시작 시 `previews/work/` 정리 |
| `LibreOfficePreviewConverter` | 인수 배열 구성(셸 미사용, 원본 파일명 미포함, 작업별 프로필 URI), 환경 변수 최소화, 래퍼 인수 앞붙임, 시간 초과 시 프로세스 트리 종료(테스트용 장기 실행 프로세스·하위 프로세스), 엔진 없음 처리 |

### 12.2 Backend 통합 (실제 PostgreSQL, 보안 설정 활성)

| 대상 | 내용 |
| --- | --- |
| 목록 | 정렬·`latest`·필드·업로더(작성자가 아닌 업로더 포함)·N+1 없음 |
| 다운로드 | 4개 형식 바이트·SHA-256 일치, 헤더, 이전 버전, `Accept` 조합 |
| 권한·IDOR | 세 API 모두 401·404(비구성원·다른 프로젝트 문서·다른 문서 버전·없는 번호·0)·400, 캐시가 있는 버전도 비구성원 404 |
| 비노출 | 세 API 응답·오류 본문에 저장 키·`previews/`·저장 루트 경로·엔진 출력 없음 |
| 상태 | 5개 상태 모두 허용 |
| 원본 실패 | 원본 삭제 후 다운로드·미리보기 `500`(JSON) |
| 실제 HTTP | 20MiB 다운로드 SHA-256, 클라이언트 중단 시 ERROR 로그 없음 |
| 회귀 | 기존 299개 수정 없이 통과 |

### 12.3 실제 LibreOffice 변환 통합 (클라우드·CI·Windows 필수, 건너뛰지 않음)

- 테스트 픽스처: 형식별로 고유 표식 텍스트(예: `DOCFLOW-PREVIEW-DOCX-1`)를 담은 DOCX·XLSX·PPTX와 PDF. 코드로 생성하거나 작은 바이너리 픽스처로 둔다 [검증 필요: 테스트용 최소 OOXML을 LibreOffice가 변환하는지].
- 검증:
  - PDF 원본: 미리보기 본문 = 원본 바이트.
  - DOCX·XLSX·PPTX: `202` 이후 `200`. 반환 PDF를 PDF 파서로 열어 페이지 수 1 이상, 형식별 표식 텍스트 포함, 암호화 없음을 확인한다(상태 코드만 보지 않는다).
  - 두 번째 요청은 즉시 `200`이며 변환 횟수 1.
  - 원본 SHA-256·DB 행 불변, 캐시는 `previews/cache/`에만 생성.
  - 손상 Office 파일(형식 검증을 통과하는 손상 본문) → `422`, 이후 다운로드 정상, 작업 디렉터리 없음.
  - 같은 버전 동시 미리보기 5개 → 변환 1회.
  - 실제 프로세스 시간 초과(테스트 설정으로 짧은 제한) → `422`, 남은 LibreOffice 프로세스·작업 디렉터리 없음.
  - 래퍼 적용 환경(클라우드, 가능하면 CI)에서 네트워크 차단 래퍼로 변환 성공.
- PDF 파서: Apache PDFBox를 `testImplementation`으로 도입(ADR-0006 결정 5). **구현 착수 시 의존성 승인 후 추가한다.**
- 엔진이 필요한 테스트는 엔진이 없으면 실패한다(건너뛰지 않는다). CI·Windows에서 LibreOffice 설치가 전제다.

### 12.4 Frontend (Vitest)

- 히스토리 표시, 목록 실패 시 영역 내 오류, 다운로드(경로·`Accept`·파일명·Blob 해제·진행 중 비활성화), 미리보기 상태 전이(`202` 폴링 → `200` 표시, `422`·`503`·90초 초과 → 실패 문구 + 다운로드), Blob `type` 고정·비 PDF 응답 거부, 다른 버전 선택·닫기·언마운트 시 폴링 중단과 Blob 해제, 401 흐름, SPEC-04 등록 후 목록 갱신, `<Activity>` 재진입.

### 12.5 클라우드 실제 브라우저 E2E [결정 Q4]

- 경로: Chromium(Playwright) → Next.js `next start` → Spring Boot jar → PostgreSQL, 실제 LibreOffice.
- 항목:
  - 히스토리 표시, 한글 파일명 다운로드(`suggestedFilename`), 19·20MiB 다운로드 SHA-256(Next.js 경유)
  - PDF·DOCX·XLSX·PPTX 미리보기: 변환 대기 문구 → iframe 표시, iframe이 Blob URL·PDF 뷰어를 로드했는지 확인, 스크린샷에서 내용이 비어 있지 않음, 두 번째는 대기 없이 표시
  - iframe 보안 설정: 채택한 `sandbox` 값에서 뷰어 동작 확인, 응답 헤더 `X-Frame-Options: DENY`·`nosniff`·`CSP sandbox` 유지 확인
  - 변환 실패 파일의 실패 문구와 다운로드, 비구성원 404, 새 버전 등록 후 히스토리 갱신
- headless Chromium에서 내장 PDF 뷰어가 렌더링되지 않으면 headful 실행(가상 디스플레이) 등 확인 방법을 바꾸고 §18에 기록한다 [검증 필요].

### 12.6 CI·Windows [결정 A4]

- CI: Backend Job에 LibreOffice·한글 글꼴 설치 단계 추가, 실제 변환 테스트 포함 `./gradlew build` 통과. 워크플로 변경은 이 SPEC 범위에 포함한다.
- Windows(사용자): LibreOffice 설치 후 `.\gradlew.bat clean build`(실제 변환 테스트 포함), Frontend lint·`tsc`·test·build.

## 13. Acceptance Criteria

| ID | 기준 | 검증 |
| --- | --- | --- |
| AC-VDL-01 | 구성원(OWNER·MEMBER)은 문서의 모든 버전을 최신순으로 조회한다(번호·파일명·업로더 표시 이름·업로드 시각·크기·최신 표시) | 통합 |
| AC-VDL-02 | 목록·다운로드·미리보기 응답과 오류에 저장 키·서버 경로·캐시 경로·SHA-256·이메일·내부 ID·엔진 출력이 없다 | 통합 |
| AC-VDL-03 | 임의 버전의 원본을 바이트 단위로 동일하게 내려받는다(4개 형식) | 통합 |
| AC-VDL-04 | 다운로드는 `attachment`·RFC 5987 파일명·서버 판별 `Content-Type`·`Content-Length`·`nosniff`·`no-store`·`CSP sandbox`로 응답한다 | 단위 + 통합 |
| AC-VDL-05 | 세 API 모두 미인증 401, 비구성원·다른 프로젝트 문서·다른 문서 버전·없는 버전 404(같은 유형은 같은 본문), 숫자 아님 400. 캐시가 있어도 같다 | 통합 |
| AC-VDL-06 | 문서 상태와 관계없이 조회·다운로드·미리보기할 수 있다 | 통합 |
| AC-VDL-07 | 20MiB 원본을 고정 버퍼로 스트리밍하며 실제 HTTP·Next.js 경유에서 SHA-256이 일치한다 | 통합(RANDOM_PORT) + E2E |
| AC-VDL-08 | 원본 누락·크기 불일치 시 `500 DOCUMENT_FILE_UNAVAILABLE`(JSON, 부분 본문 없음)과 원인 로그 | 통합 |
| AC-VDL-09 | 클라이언트 연결 중단은 ERROR 로그를 남기지 않는다 | 통합(실제 HTTP) |
| AC-VDL-10 | PDF 원본은 변환 없이 같은 바이트로 미리보기 응답된다 | 실제 변환 통합 |
| AC-VDL-11 | DOCX·XLSX·PPTX는 실제 LibreOffice로 변환되어 미리보기된다: `202` 후 `200`, PDF 파서로 열리고 1페이지 이상, 형식별 표식 텍스트 포함. 두 번째 요청은 캐시로 즉시 응답(변환 1회) | 실제 변환 통합 (클라우드·CI·Windows) |
| AC-VDL-12 | 미리보기는 원본 파일·DB 행을 바꾸지 않는다(원본 SHA-256·행 불변, 결과는 `previews/`에만) | 실제 변환 통합 |
| AC-VDL-13 | 변환 실패·60초 초과·결과 무효·100MiB 초과는 `422`, 엔진 없음·대기열 초과는 `503`이며, 이후에도 원본 다운로드가 정상이다. 실패·시간 초과 후 변환 프로세스와 작업 디렉터리가 남지 않는다 | 단위 + 실제 변환 통합 |
| AC-VDL-14 | 같은 버전 동시 미리보기는 변환 1회로 처리되고, 동시 변환 1개·대기열 10개 제한이 지켜진다 | 단위 + 실제 변환 통합 |
| AC-VDL-15 | 캐시 전체 용량이 상한을 넘으면 오래 사용하지 않은 캐시부터 정리되고, 설정 기간 미사용 캐시가 정리된다. 전송·변환 중인 캐시와 원본은 삭제되지 않는다 | 단위 + 통합 |
| AC-VDL-16 | 변환은 셸 없이 인수 배열로, 변환마다 독립 작업 디렉터리·사용자 프로필로, 최소 환경 변수로 실행된다. 설정된 네트워크 차단 래퍼 안에서 변환이 성공한다. Backend 시작 시 남은 작업 디렉터리가 정리된다 | 단위 + 실제 변환 통합 |
| AC-VDL-17 | 기존 `SecurityConfig`·`X-Frame-Options: DENY`·`nosniff`를 바꾸지 않고, 실제 브라우저에서 PDF Blob iframe으로 4개 형식 미리보기가 표시된다(채택한 iframe 보안 설정 기록) | 통합 + 클라우드 E2E |
| AC-VDL-18 | 문서 상세에 버전 히스토리·다운로드·미리보기 영역이 있고, 상태별 문구·폴링 중단·Blob 해제·비 PDF 거부·세션 만료 흐름을 따른다 | Vitest |
| AC-VDL-19 | 새 버전 등록(SPEC-04) 후 히스토리에 새 버전이 최신으로 반영된다 | Vitest + E2E |
| AC-VDL-20 | 클라우드 실제 브라우저에서 히스토리, 한글 파일명 다운로드, 4개 형식 미리보기, 변환 실패 시 다운로드 대체가 동작한다 | 클라우드 E2E |
| AC-VDL-21 | GitHub Actions CI가 LibreOffice 실제 변환 테스트를 포함해 통과한다 | CI |
| AC-VDL-22 | Windows에서 LibreOffice 실제 변환 테스트를 포함한 Backend `clean build`와 Frontend lint·test·build가 통과한다 | Windows 자동 (사용자) |
| AC-VDL-23 | 기존 기능이 회귀하지 않는다(SPEC-03·04 테스트 수정 없음) | 회귀 + CI |

- 판정 환경: AC-VDL-01~19·23은 클라우드 실행 + 최신 커밋 CI, AC-VDL-20은 클라우드 실제 브라우저, AC-VDL-21은 CI, AC-VDL-22는 Windows(사용자). 미실행 검증은 통과로 기록하지 않으며, AC-VDL-21·22가 미실행이면 Done이 아니다.

## 14. 위험

| ID | 위험 | 대응 |
| --- | --- | --- |
| R1 | 대용량·복잡한 Office 문서는 60초 안에 변환되지 않음([실측] 8.8MB DOCX) | 실패 안내 + 원본 다운로드 |
| R2 | 서버에서 신뢰할 수 없는 문서를 해석. OS 격리 래퍼가 없는 환경(Windows 등)은 프로필 설정·작업 디렉터리 격리만 적용 | ADR-0006 잔여 위험 명시. 운영은 래퍼 또는 컨테이너 권장 |
| R3 | Backend 강제 종료 시 래퍼 없는 환경에서 LibreOffice 프로세스 잔존 | 정상 종료 시 정리, 시작 시 작업 디렉터리 정리, 래퍼 사용 권장 |
| R4 | 변환 품질(글꼴·레이아웃 차이, 한글 글꼴) | 실행 환경 한글 글꼴 설치, "미리보기는 참고용" 안내 |
| R5 | CI 시간 증가·LibreOffice 설치 실패·러너 비특권 namespace 제한 | 설치 단계 실패는 CI 실패로 드러남, 래퍼는 가능할 때만 적용 |
| R6 | `api-client`의 `Accept: application/json`으로 인한 `406` | 전용 함수, `Accept` 조합 테스트 |
| R7 | 브라우저 내장 PDF 뷰어의 Blob iframe·`sandbox` 동작 차이, headless 확인 한계, 모바일 Safari | E2E로 확인, 실패 시 PDF.js 별도 승인 요청 |
| R8 | 브라우저 PDF 뷰어가 악성 PDF(특히 PDF 원본)를 해석 | 브라우저 격리, Blob 형식 고정, 서버 응답 `attachment`·`sandbox` |
| R9 | 캐시 정리 경쟁(전송 중 삭제, Windows 열린 파일) | 참조 수 보호, 실패 시 다음 정리에서 재시도 |
| R10 | Next.js 프록시 30초 제한 | 폴링으로 요청을 짧게 유지, 다운로드는 E2E 확인 |
| R11 | 메모리 기반 작업·실패·캐시 색인은 다중 인스턴스에 맞지 않음 | MVP 단일 인스턴스, 필요 시 상태 테이블·공유 잠금 |
| R12 | 배포 환경 미정 | 인터페이스 분리, ADR-0006 배포 요건 |
| R13 | 테스트 컨텍스트 DB 연결 수([기존] SPEC-04 §11.5) | 신규 통합 테스트 컨텍스트 수 최소화·`@DirtiesContext` 유지 |

### 14.1 Audit Log 기록 지점 (SPEC-13 참고)

- 후보: 다운로드·미리보기 성공. SPEC-05는 INFO 로그만 남긴다(원본 파일명 제외).

## 15. 후속 SPEC과의 관계

| 후속 | SPEC-05가 제공 | 후속에서 결정 |
| --- | --- | --- |
| SPEC-06 Review 요청 | `versionNumber` 식별, 목록·미리보기 | 검토 대상 버전 지정, 역할별 조회 권한 변화 여부 |
| SPEC-07·08 Feedback | 버전 미리보기 | Feedback 위치(페이지) 연결 여부 |
| SPEC-13 Audit Log | 기록 지점 | 다운로드·미리보기 기록 여부 |
| Post-MVP Diff·AI 요약 | 버전 PDF 캐시, 파일 읽기 | 비교·요약 방식 |

## 16. 결정 사항

### 16.1 결정 (2026-11-04, 사용자)

| ID | 결정 |
| --- | --- |
| Q1 | 미리보기를 이 SPEC에 포함, 4개 형식 목표. PDF는 브라우저 렌더러, Office는 서버 PDF 변환 후 같은 뷰어. 미리보기와 다운로드는 별도 기능, 원본 불변, 변환 실패 시 다운로드 유지 |
| Q2 | Frontend 다운로드는 Fetch + Blob |
| Q3 | 원본 누락·손상은 `500 DOCUMENT_FILE_UNAVAILABLE` |
| Q4 | Windows 자동 테스트·빌드 필수. 미리보기를 실제 브라우저 E2E 검증 범위에 포함 |
| Q5 | `latestVersion`에 업로더 미추가 |
| P1~P7 | 승인(미리보기와 충돌하지 않는 범위): 버전 번호 식별, DESC·페이지네이션 없음, 다운로드 `attachment`·RFC 5987·`CSP sandbox`, 트랜잭션 종료 후 스트리밍, 크기 비교만, 모든 상태 허용, 버전 단건 API 없음 |
| A1 | LibreOffice 프로세스 직접 실행, `DocumentPreviewConverter` 인터페이스 분리 (ADR-0006) |
| A2 | 최초 미리보기 요청 시 비동기 변환, `202`, Frontend 폴링 |
| A3 | 버전별 PDF 캐시 재사용 + 설정 가능한 전체 용량 상한 + 오래 사용하지 않은 캐시 정리. 원본 삭제 금지, 진행 중 변환·전송과 충돌 방지 |
| A4 | GitHub Actions CI와 Windows에서 LibreOffice 실제 변환 테스트를 완료 필수로 채택 |
| 추가 | 변환마다 독립 임시 디렉터리·사용자 프로필, 셸 결합 없이 인수 전달, 최소 권한·가능한 환경의 네트워크 제한(잔여 위험 ADR 명시), 60초·동시 1·대기열 10·100MiB 유지, 실패·재시작 정리, 60초 초과 시 실패 안내 + 다운로드, Blob iframe 보안·호환성 E2E 검증, 미리보기 권한·경로 비노출·캐시 접근 제어·중복 요청 방지 테스트, 4개 형식 PDF 유효성 검증 |

### 16.2 남은 미결정 사항

- 정책 미결정 없음.
- 구현 착수 시 승인 필요: Apache PDFBox `testImplementation` 추가(§12.3, ADR-0006 결정 5).
- 구현 중 확인한 기술 사항 (2026-11-04):
  - §6.4 `Accept`: 미리 지정한 `Content-Type`으로 응답하므로 `Accept: application/json`·`*/*`·브라우저 기본값·없음 모두 파일을 반환하고, 오류는 JSON이다(통합 테스트). `Accept: text/html`만 보내는 요청의 오류 응답은 [기존] 전역 동작대로 본문을 만들지 못한다(SPEC-05 범위 밖, 브라우저 기본 `Accept`에는 `*/*`가 포함됨).
  - §7.3 LibreOffice 설정 키: `org.openoffice.Office.Common/Security/Scripting`의 `MacroSecurityLevel`=3·`DisableMacrosExecution`=true·`BlockUntrustedRefererLinks`=true, `org.openoffice.Office.Writer/Content/Update/Link`=2(갱신 안 함), `org.openoffice.Office.Calc/Content/Update/Link`=1(갱신 안 함). LibreOffice 24.2 스키마에서 확인했고, 변환 후 프로필에 값이 유지됨을 확인했다.
  - §7.4: iframe `sandbox` 미사용, 뷰어 옵션 `#navpanes=0&view=FitH` (위).
  - §10.3 CI 비특권 namespace: `ubuntu-latest`에서 `kernel.apparmor_restrict_unprivileged_userns=0` 설정 후 `unshare -rn` 사용 가능, CI 미리보기 테스트는 `DOCFLOW_PREVIEW_LAUNCHER=unshare,-rn`으로 실행된다(§18.2).
  - §12.3 테스트 픽스처: 최소 OOXML 대신 python-docx·openpyxl·python-pptx로 만든 작은 실제 파일(`backend/src/test/resources/preview/`, 형식별 표식 텍스트)을 쓴다. 손상 DOCX는 LibreOffice가 종료 코드 0으로 끝나지만 결과 파일이 없어 `FAILED`로 판정한다.
  - §12.5 headless PDF 뷰어: Blob iframe 안에 Chromium 내장 PDF 뷰어(`embed type=application/pdf`)가 생성되는 것은 매번 확인되지만, 문서 상세 안 iframe의 화면 캡처는 헤드리스 합성 한계로 일부만 그려지거나 실행마다 다르다(§18.4).

## 17. 구현 계획 및 체크리스트

1. [x] 설계 결정 Q1~Q5·P1~P7·A1~A4 확정, Draft v0.3·ADR-0006 작성 (2026-11-04)
2. [x] GitHub Issue #21, 브랜치 `feat/21/document-version-download` 생성 (2026-11-04)
3. [x] 구현 착수 승인, PDFBox 테스트 의존성 승인 (2026-11-04)
4. [x] Backend 1: `FileStorage` 읽기, 목록·다운로드 API + 단위·통합·실제 HTTP 테스트
5. [x] Backend 2: 변환기·캐시·미리보기 서비스·API + 단위·실제 변환 통합 테스트
6. [x] CI: LibreOffice·한글 글꼴 설치, 네트워크 격리 래퍼, 실제 변환 테스트 통과
7. [x] Frontend: 히스토리·다운로드·미리보기 + Vitest
8. [x] 클라우드 회귀·실제 브라우저 E2E → §18
9. [ ] Windows 검증(사용자, §18.6) → §18
10. [x] 문서(README 설치 안내·`docs/architecture.md`·ADR-0006 구현 결과)
11. [ ] PR (`Closes #21`), 병합은 사용자

## 18. 검증 결과

실행한 결과만 기록한다.

### 18.1 요약

| 항목 | Claude Code 클라우드 | GitHub Actions CI | Windows 로컬 (사용자) |
| --- | --- | --- | --- |
| Backend build·test | 성공, 360개 통과(기존 299 + 신규 61), 실제 LibreOffice 변환 포함 | 성공 (§18.2) | 미실행 |
| Frontend lint·`tsc`·test·build | 성공, Vitest 228개(기존 198 + 신규 30) | 성공 (§18.2) | 미실행 |
| 실제 브라우저 E2E (§12.5) | 19/19 통과 (§18.4, 시각 확인 한계 있음) | — | — |

### 18.2 실행 환경

- 클라우드: Linux, OpenJDK 21(toolchain 21, CI는 JDK 25), PostgreSQL 16.15, LibreOffice 24.2.7, 한글 글꼴(WenQuanYi), Node 22, Chromium(Playwright, 전체 Chromium new headless), 변환 격리 `unshare -rn` 사용 가능.
- CI(`ubuntu-latest`): LibreOffice Writer·Calc·Impress + `fonts-noto-cjk` 설치 단계(약 18초), `unshare -rn` 사용 가능하도록 AppArmor 사용자 namespace 제한 해제 후 `DOCFLOW_PREVIEW_LAUNCHER=unshare,-rn` 설정(로그에서 확인). 실제 변환 테스트는 격리 래퍼 안에서 실행되었다.
- Backend 테스트 수 증가: 단위(파일명 헤더, 저장소 읽기, 캐시, 미리보기 서비스, 변환기), 통합(조회·다운로드, 실제 HTTP, 미리보기 실제 변환, 엔진 없음).

### 18.3 AC별 결과

| AC | 근거 | 결과 |
| --- | --- | --- |
| AC-VDL-01 | `DocumentVersionQueryIntegrationTest.listsVersionsNewestFirstWithUploaderAndNoInternals`, E2E 1·8 | 통과 |
| AC-VDL-02 | 목록·다운로드·미리보기 통합 테스트의 내부 정보 단언, 엔진 없음 테스트(엔진 경로 미노출) | 통과 |
| AC-VDL-03 | `downloadsOriginalBytesWithSafeHeaders`(4개 형식), `downloadsPreviousVersion`, E2E 2 | 통과 |
| AC-VDL-04 | `DocumentFileResponseTest`(한글·`"`·`;`·`%`·이모지), 통합 헤더 단언 | 통과 |
| AC-VDL-05 | `accessAndOwnershipAreCheckedForListAndDownload`, `previewChecksAccessEvenWhenCached`, E2E 9 | 통과 |
| AC-VDL-06 | `everyStatusAllowsListAndDownload`(5개 상태) | 통과 |
| AC-VDL-07 | `DocumentVersionDownloadHttpIntegrationTest.streams20MiBDownloadWithMatchingHash`, E2E 7(20MiB UI·19MiB Next.js 경유) | 통과 |
| AC-VDL-08 | `missingOrResizedStoredFileIsUnavailable`(JSON, 원인 로그) | 통과 |
| AC-VDL-09 | `clientAbortDuringDownloadIsNotLoggedAsError`(예외 처리를 빼면 실패함을 확인) | 통과 |
| AC-VDL-10 | `previewsAllFourFormatsAsValidPdfWithoutTouchingOriginals`(PDF 원본 바이트 일치), 서비스 단위 테스트 | 통과 |
| AC-VDL-11 | 같은 테스트: DOCX·XLSX·PPTX `202` 후 `200`, PDFBox로 1페이지 이상·형식별 표식 텍스트 확인, 두 번째 요청 캐시, 변환 3회. `LibreOfficePreviewConverterTest` 실제 변환 3종 | 통과 |
| AC-VDL-12 | 같은 테스트: 원본 SHA-256·DB 행 불변, 결과는 `previews/cache`에만 | 통과 |
| AC-VDL-13 | `unconvertibleDocumentFailsButOriginalDownloadStillWorks`(422 후 다운로드), `DocumentPreviewEngineUnavailableIntegrationTest`(503, PDF 미리보기·다운로드 정상), 서비스 단위(결과 무효·크기 초과·시간 초과 재시도), 변환기(실제 시간 초과 후 프로세스 없음, 가짜 엔진 하위 프로세스 종료) | 통과 |
| AC-VDL-14 | `concurrentPreviewRequestsTriggerOneConversion`(실제 엔진, 변환 1회), 서비스 단위(동시 1개·대기열 초과 503) | 통과 |
| AC-VDL-15 | `PreviewCacheTest`(LRU 90%, 미사용 정리, 사용 중·방금 등록 보호, 원본 보호, 재시작 색인) | 통과 |
| AC-VDL-16 | 변환기 단위(인수 배열·프로필·최소 환경), 격리 래퍼 실제 변환(클라우드·CI), 래퍼 실패 시 사용 불가(대체 실행 없음), 서비스 시작 시 작업 디렉터리 정리 | 통과 |
| AC-VDL-17 | 통합(헤더 유지) + E2E 3·4: 4개 형식 모두 Blob iframe에 Chromium 내장 PDF 뷰어 생성, 응답 `X-Frame-Options: DENY`·`nosniff`·`CSP sandbox` 유지, `SecurityConfig` 변경 없음. iframe `sandbox`는 사용하지 않음(§7.4) | 통과 (시각 확인 한계 §18.4) |
| AC-VDL-18 | Vitest: `DocumentVersionHistory`·`DocumentVersionPreview`·`DocumentDetailVersionHistory`·`version-files` 테스트 | 통과 |
| AC-VDL-19 | Vitest(등록 후 목록 재조회), E2E 8 | 통과 |
| AC-VDL-20 | E2E 19/19 (§18.4) | 통과 (시각 확인 한계 §18.4) |
| AC-VDL-21 | CI(LibreOffice 설치·격리 래퍼·실제 변환 테스트 포함) | 통과 |
| AC-VDL-22 | Windows | **미실행 (사용자, §18.6)** |
| AC-VDL-23 | 기존 Backend 299개·Frontend 198개 수정 없이 통과(기존 파일 변경 없음) | 통과 |

### 18.4 클라우드 실제 브라우저 E2E (2026-11-04)

경로: Chromium(Playwright, 전체 Chromium new headless, UTF-8 로케일) → Next.js `next start` → Spring Boot jar(`unshare -rn` 격리, LibreOffice) → PostgreSQL. 결과 19/19:

1. 히스토리 표시(v1·최신·파일명·업로더)
2. 한글 파일명 다운로드: `suggestedFilename` = "요구사항 명세서.pdf", SHA-256 일치
3. PDF·DOCX·XLSX·PPTX 미리보기: Office는 "준비하는 중" 문구 후 표시(약 2초), 4개 형식 모두 Blob iframe에 Chromium 내장 PDF 뷰어(`embed type=application/pdf`) 생성, 다시 열면 캐시로 0.1초 이내 표시
4. 미리보기 응답 `X-Frame-Options: DENY`·`nosniff`·`CSP: default-src 'none'; sandbox`·`application/pdf`, 변환 중 `202` 관찰
5. iframe `sandbox` 비교: 속성 없음만 렌더링, 빈 값·`allow-scripts`·`allow-same-origin`·`allow-scripts allow-same-origin`은 렌더링되지 않음
6. 변환 실패 문서: 실패 안내 + 원본 다운로드(파일명·내용 일치)
7. 20MiB UI 다운로드 SHA-256 일치, 19MiB Next.js 경유 다운로드 SHA-256 일치
8. 새 버전 등록 후 히스토리 v2 최신 반영
9. 비구성원 화면 "프로젝트를 찾을 수 없습니다." + 캐시가 있는 버전의 미리보기 API `404`
10. Office 캐시는 `previews/cache`에만, 작업 디렉터리 없음, 원본 파일 존재

**시각 확인 한계와 발견 사항:**

- 헤드리스 Chromium은 문서 상세 안의 PDF 뷰어 iframe을 화면 캡처에 일부만 그리며 결과가 실행마다 달랐다. 그래서 E2E 판정은 매번 같은 신호(Blob iframe + 내장 PDF 뷰어 생성)로 하고 픽셀 통계는 기록만 했다. 같은 Blob을 같은 폭(624px)의 단독 iframe(`#navpanes=0&view=FitH`)과 최상위 열기로 표시했을 때는 변환 문서의 한글 텍스트까지 완전히 렌더링되는 것을 화면으로 확인했다. **문서 상세 안에서 본문 글자까지 보이는지는 실제 데스크톱 브라우저(Windows Chrome·Edge)에서 확인이 필요하다(§18.6).**
- 클라우드 컨테이너에 로케일이 없으면 Chromium이 비 ASCII 다운로드 파일명을 "download"로 바꿨다. UTF-8 로케일에서는 원본 파일명이 유지된다. 앱 문제가 아닌 실행 환경 특성이다.
- 좁은 영역에서 썸네일 패널이 열려 있으면 Chromium이 본문 페이지를 그리지 않아 뷰어 옵션을 추가했다(§7.4, 커밋 `29d02e0`).

### 18.5 미실행·남은 항목

- AC-VDL-22 Windows 검증(사용자): §18.6.
- 실제 데스크톱 브라우저에서 문서 상세 안 미리보기 본문 표시 확인(§18.4 한계). Windows 수동 확인 권장 항목으로 §18.6에 포함한다.
- Firefox·Safari·모바일 브라우저 미확인(R7).

### 18.6 Windows 검증 명령과 기대 결과 (사용자)

1. LibreOffice 설치와 확인 (PowerShell):

   ```powershell
   winget install --id TheDocumentFoundation.LibreOffice -e   # 또는 공식 설치 파일
   & "C:\Program Files\LibreOffice\program\soffice.com" --version   # LibreOffice 버전 출력
   ```

   기본 경로가 아니면 `$env:DOCFLOW_PREVIEW_SOFFICE_PATH = "<soffice.com 경로>"`. Windows에서는 콘솔용 `soffice.com`을 써야 한다(`soffice.exe`는 출력이 없어 엔진 점검이 실패하고 Office 미리보기가 503이 된다).
2. Backend 전체 빌드 (전용 테스트 DB):

   ```powershell
   cd backend
   $env:DB_URL = "jdbc:postgresql://localhost:5432/docflow_test"; $env:DB_USERNAME = "docflow"; $env:DB_PASSWORD = "<비밀번호>"
   .\gradlew.bat clean build --rerun-tasks --console=plain
   ```

   기대: `BUILD SUCCESSFUL`. 테스트 결과(`build\test-results\test\*.xml`) 합계 360개, 실패·오류 0. Windows에서는 Linux 전용 테스트 3개(`LibreOfficePreviewConverterTest`의 가짜 엔진 하위 프로세스 종료, 격리 래퍼 실패, `unshare` 격리)가 `skipped`로 집계된다(실제 LibreOffice 변환·시간 초과 테스트는 실행됨).
3. 미리보기 테스트만 다시 실행해 결과 확인:

   ```powershell
   .\gradlew.bat test --tests "com.docflow.document.preview.*" --tests "com.docflow.document.DocumentPreviewIntegrationTest" --tests "com.docflow.document.DocumentPreviewEngineUnavailableIntegrationTest" --rerun --console=plain
   Get-Process soffice* -ErrorAction SilentlyContinue   # 출력 없음 (남은 LibreOffice 프로세스 없음)
   ```

   기대: `BUILD SUCCESSFUL`, 실패·오류 0.
4. Frontend: `npm ci`, `npm run lint`, `npx tsc --noEmit`, `npx vitest run`(228개 통과), `npm run build`.
5. 권장 수동 확인(브라우저): Backend·Frontend 실행 후 Chrome 또는 Edge에서 문서 상세 → 각 형식 "미리보기" → 본문 글자가 보이는지, "다운로드" 시 한글 파일명으로 저장되는지.

### 18.7 Windows 미리보기 테스트 실패 분석과 수정 (2026-11-04)

**보고된 현상 (사용자, Windows):** LibreOffice 26.8.2.2, `DOCFLOW_PREVIEW_SOFFICE_PATH=...\program\soffice.com`으로 503은 해소. `DocumentPreviewIntegrationTest` 4개가 `DocumentTestSupport.cleanStorage()`의 `AccessDeniedException`으로 실패. 테스트 후 soffice 프로세스 없음. 남은 경로는 `previews/work/{uuid}/Documents`(일반 디렉터리, `ReadOnly, Directory`).

**코드로 확인한 원인 (복합):**

1. 결과 공개와 정리 순서: 변환 작업이 결과(캐시 등록 또는 실패 기록)를 먼저 공개하고 그 다음에 작업 디렉터리를 지웠다. 테스트는 `200`/`422`를 받은 즉시 끝나고 `cleanUp`이 저장 루트를 지우므로, 서비스 스레드의 정리와 경쟁한다. Linux에서는 삭제가 빠르고 열린 파일·속성이 삭제를 막지 않아 드러나지 않았다.
2. 작업 디렉터리 안의 `Documents`: 변환 프로세스의 `USERPROFILE`을 작업 디렉터리로 바꾸므로(ADR-0006 최소 환경), Windows의 LibreOffice가 사용자 알려진 폴더(`Documents`)를 작업 디렉터리 안에 만든다. Windows는 알려진 폴더에 읽기 전용 속성을 붙이고, Java `Files.delete`(`RemoveDirectory`)는 읽기 전용 디렉터리 삭제를 거부한다. 서비스의 정리(`FileSystemUtils.deleteRecursively`)도 같은 이유로 실패해 디렉터리가 남았고, 이어서 테스트의 `cleanStorage()`가 실패했다. Linux에는 이 폴더가 생기지 않고 속성도 삭제를 막지 않는다.
3. 프로세스 종료 대기: 엔진 종료 후 남은 하위 프로세스를 강제 종료만 하고 끝날 때까지 기다리지 않았다. Windows는 열린 파일이 있는 디렉터리를 지울 수 없으므로, 하위 프로세스가 파일 핸들을 놓기 전에 정리하면 실패할 수 있다(이번 보고에서는 테스트 후 프로세스가 없어 2가 직접 원인으로 보이나, 같은 경로의 잠재 원인).

**최소 수정 (`fix/#21`):**

- 작업 결과는 작업 디렉터리를 지운 뒤에 공개한다(결과 PDF를 작업 디렉터리 밖으로 옮긴 후 정리 → 캐시 등록/실패 기록 → 작업 해제).
- 작업 디렉터리 삭제(`PreviewWorkspace`): 작업 루트 안에서만, 심볼릭 링크를 따라가지 않고 삭제하며, 접근 거부가 나면 그 항목의 읽기 전용 속성만 해제하고 한 번 더 시도한다. 그래도 실패하면 예외를 던지고 서비스는 원인과 함께 WARN 로그를 남긴다. 시작·종료 시 정리와 엔진 점검 디렉터리 정리도 같은 방식을 쓴다.
- 엔진 종료 후 남은 하위 프로세스는 종료까지 최대 5초 기다린다(이 변환 작업의 프로세스만).
- 테스트: 읽기 전용 알려진 폴더를 만드는 가짜 엔진으로 "결과가 보이는 시점에 작업 루트가 비어 있음"을 검증하고, `PreviewWorkspace` 단위 테스트를 추가했다. 실제 변환 테스트·보안 검증은 그대로다. 클라우드 Backend 365개 통과.
- 기본 탐색: Windows 기본 경로·PATH 탐색을 `soffice.exe`에서 `soffice.com`으로 바꿨다. 보고된 503은 `soffice.exe`의 `--version` 점검 실패로 판단한다(`soffice.com` 지정 시 해소).
- 남은 확인: Windows 재실행(§18.6 명령).

# DocFlow 아키텍처 개요

> 기준: 6주차 프로젝트 초기화 (2026-10-14), SPEC-03 문서 등록 반영 (2026-10-28), SPEC-04 문서 버전 등록, SPEC-05 버전 조회·다운로드·미리보기 반영 (2026-11-04). 요구사항과 범위는 [`spec.md`](../spec.md) Draft v0.2를 따른다.
> 결정 근거는 [`docs/adr/`](./adr)를 참고한다.

## 1. 시스템 구성

```text
Browser ──(/api/*, /backend/health)──▶ Next.js 16 (:3000) ──(rewrite)──▶ Spring Boot 4.1 (:8080) ──(JDBC)──▶ PostgreSQL 16 (:5432)
        ◀── JSESSIONID, XSRF-TOKEN 쿠키는 localhost:3000에 저장 ──                     │
                                                                                         └──(파일 I/O)──▶ 로컬 파일 저장소 (DOCFLOW_STORAGE_DIR)
```

- **Frontend**: Next.js 16 + React 19 + TypeScript + Tailwind CSS 4
- **Backend**: Java 25 + Spring Boot 4.1 (Spring MVC, Spring Data JPA, Flyway, Actuator)
- **Database**: PostgreSQL 16 (로컬은 Docker Compose)
- 추가 인프라(Redis, Kafka, Message Queue, Microservices)와 AI 서비스(Python/FastAPI)는 **포함하지 않는다.** AI는 Post-MVP다.

## 2. 저장소 구조

```text
docflow/
├── .github/
│   ├── workflows/ci.yml       # Backend build/test, Frontend lint/test/build
│   └── pull_request_template.md
├── backend/                   # Spring Boot (Gradle Groovy DSL)
│   └── src/main/
│       ├── java/com/docflow/
│       │   ├── global/        # config(Security, JPA Auditing), error(공통 오류 응답), security(401/403 JSON), common(BaseTimeEntity)
│       │   ├── user/          # domain(User), repository, service(UserAccountService)
│       │   ├── auth/          # controller, service, security(AuthenticatedUser, UserDetailsService), dto, dev(local Seeder)
│       │   ├── project/       # controller(Project, ProjectMember), service(ProjectService, ProjectPermissionService, ProjectMemberService), domain(Project, ProjectMember, ProjectRole, ProjectText), repository, dto
│       │   └── document/      # controller, service(DocumentService, StoredFileCompensation), domain(Document, DocumentVersion, DocumentStatus), repository, dto, storage(FileStorage, LocalFileStorage, StorageKeyGenerator), validation(PDF·OOXML·파일명)
│       └── resources/
│           ├── application.yml
│           └── db/migration/  # V1__baseline, V2__create_users, V3__create_projects, V4__create_project_members, V5__create_documents
├── frontend/                  # Next.js (npm)
│   └── src/
│       ├── app/               # /, /login, /projects, /projects/new, /projects/[projectId], /projects/[projectId]/documents/{new,[documentId]}, layout(AuthProvider)
│       ├── lib/api-client.ts  # 동일 출처 fetch, CSRF 헤더·재시도, 오류 파싱
│       └── features/
│           ├── auth/          # AuthProvider, api, LoginForm, RequireAuth, LogoutButton
│           ├── projects/      # api, types, validation(§5.5 입력 규칙), ProjectList, ProjectCreateForm, ProjectDetail, ProjectMembers, AddMemberForm
│           ├── documents/     # api(FormData 등록·새 버전), version-files(버전 목록·다운로드·미리보기 fetch), types, validation, errors, DocumentList, DocumentCreateForm, DocumentDetail, DocumentVersionUploadForm, DocumentVersionHistory, DocumentVersionPreview
│           └── backend-health/
├── docs/
│   ├── architecture.md
│   └── adr/0001~0005
├── specs/                     # README.md, 00-auth.md (SPEC-AUTH), 01-project-create.md (SPEC-01), 02-project-members.md (SPEC-02), 03-document-create.md (SPEC-03), 04-document-version-create.md (SPEC-04)
├── docker-compose.yml         # PostgreSQL 16
├── .env.example               # 환경변수 예시 (.env는 Git 제외)
├── spec.md
└── CLAUDE.md
```

## 3. Backend 계층 규칙 (기능 구현 시 적용)

패키지는 Domain 중심, 도메인 내부는 계층으로 나눈다([ADR-0002](./adr/0002-package-structure.md)).

| 계층 | 책임 | 금지 사항 |
| --- | --- | --- |
| Controller | HTTP 요청·응답, 입력 형식 검증 | Repository 직접 호출, Entity 반환 |
| Service | 권한 검증 → 상태 전이 검증 → 비즈니스 로직, `@Transactional` 경계 | 다른 도메인 Repository 직접 접근(지양) |
| Domain | Entity, 상태 enum과 허용 전이 규칙 | — |
| Repository | Spring Data JPA 영속성 | 비즈니스 규칙 |
| DTO | Request/Response (Java record) | — |

상태 전이 규칙(`spec.md` §7)은 Backend에서만 강제한다. Frontend는 표시와 사용성 보조만 한다.

## 4. Frontend 구조 규칙

- `app/`: 라우팅과 레이아웃만 둔다.
- `features/<feature>/`: 기능별 API 호출(`api.ts`), 타입(`types.ts`), 컴포넌트(`components/`), 테스트를 함께 둔다.
- 화면은 최소한 Loading / Empty / Error / Success 상태를 구분한다(`spec.md` §11). 현재 연결 확인 화면은 Loading / Success(UP) / DOWN / Error를 구분한다.
- Backend 호출은 Next.js rewrites를 통해 동일 출처로 한다. 등록된 경로는 `/api/:path*`(API)와 `/backend/health`(개발용 연결 확인)다.
- API 호출은 `lib/api-client.ts`를 사용한다. 변경 요청에 CSRF 헤더를 붙이고, `403 CSRF_INVALID`일 때만 토큰 갱신 후 1회 재시도한다. 본문이 `FormData`이면 JSON으로 바꾸지 않고 `Content-Type`도 지정하지 않는다(브라우저가 multipart boundary를 정한다). 재시도 시 같은 `FormData`를 다시 보낸다.
- 인증 상태는 `AuthProvider`가 `GET /api/auth/me` 응답으로만 판단하며, 브라우저 저장소에 사용자 정보나 토큰을 저장하지 않는다.
- 기능 API가 `401`을 반환하면 해당 화면이 `useAuth().refresh()`를 호출하고, `RequireAuth`가 `/login?next=...&reason=expired`로 이동시킨다(SPEC-01 §8.4).
- `cacheComponents: true`에서 동적 세그먼트(`/projects/[projectId]`)는 `useParams`뿐 아니라 `RequireAuth`의 `usePathname`도 suspend되므로, `RequireAuth`를 포함한 하위 트리 전체를 `Suspense`로 감싼다. `ProjectDetail`만 감싸면 `npm run build`가 실패함을 확인했다. 문서 등록·상세 경로도 같은 방식이다.
- `cacheComponents`에서 이전 경로는 언마운트되지 않고 `<Activity>`로 숨겨진다. 생성·등록 폼은 성공 후 숨겨질 때 초기화하며, 파일 입력은 `key`를 바꿔 다시 마운트한다.
- 클라이언트 입력 검증(`features/projects/validation.ts`)은 Backend `ProjectText`와 같은 문자 범주를 명시적으로 사용한다. JavaScript `trim()`·`\s`는 Java와 기준이 달라 사용하지 않으며, 서버 검증이 최종 기준이다.

## 5. 데이터와 스키마

- 스키마는 Flyway migration으로만 변경한다(`ddl-auto=validate`).
- `V1__baseline.sql`(기준점), `V2__create_users.sql`(SPEC-AUTH `users`), `V3__create_projects.sql`(SPEC-01 `projects`), `V4__create_project_members.sql`(SPEC-02 `project_members`)이 있다. `ddl-auto=validate`가 `User`·`Project`·`ProjectMember` Entity와 실제 스키마를 검증한다.
- `projects.owner_id`는 `users.id` FK(`ON DELETE RESTRICT`)이며, 목록 조회용 인덱스 `(owner_id, created_at DESC, id DESC)`와 공백 이름 방지 `CHECK`가 있다.
- `project_members`(SPEC-02)는 MEMBER만 저장한다(Owner는 `projects.owner_id`). `project_id` FK(`ON DELETE CASCADE`), `user_id` FK(`ON DELETE RESTRICT`), `UNIQUE (project_id, user_id)`(`uk_project_members_project_user`), `user_id` 인덱스가 있다. 구성원 제거는 행을 물리 삭제한다.
- `documents`(SPEC-03): `project_id`·`created_by` FK(`RESTRICT`), 상태 `CHECK`(5개 값, 기본 `DRAFT`), 제목 공백 금지 `CHECK`, 목록 인덱스 `(project_id, created_at DESC, id DESC)`.
- `document_versions`(SPEC-03): `document_id`·`uploaded_by` FK(`RESTRICT`), `UNIQUE (document_id, version_number)`, `UNIQUE (storage_key)`, 버전 ≥ 1·크기 > 0·SHA-256 소문자 hex 64자·파일명 공백 금지 `CHECK`. SPEC-03은 v1을, SPEC-04는 v2 이상을 만든다. 최신 버전은 `MAX(version_number)`로 계산하며 포인터 컬럼이 없다.
- Entity ID는 `BIGINT GENERATED ALWAYS AS IDENTITY`로 확정했다(ADR-0005).

### 문서 파일 저장과 업로드 흐름 (SPEC-03)

```text
Browser ─ FormData(title, description, file) ─▶ Next.js rewrite (본문 복제 상한 25MiB)
  ─▶ Tomcat multipart (파일 20MB, 요청 21MB → 초과 시 413 FILE_TOO_LARGE)
  ─▶ DocumentController ─▶ DocumentService
       1. 프로젝트 접근 확인(404) → 문서 등록 권한(ProjectPermissionService)
       2. 파일명 정규화·확장자 확인
       3. 저장 루트 tmp/에 임시 파일 복사 (실제 크기·SHA-256 계산, 20,971,520바이트 초과 413, 0바이트 400)
       4. 형식 검증 (PDF 최소 구조 / OOXML [Content_Types].xml, JDK StAX + DTD·외부 엔티티 차단, ZIP 자원 상한)
       5. 최종 파일 배타적 생성: documents/<UUID>를 CREATE_NEW로 생성 → 복사 → force → close (기존 파일은 덮어쓰지 않음)
       6. DB 트랜잭션(TransactionTemplate): Document(DRAFT) + DocumentVersion(v1) INSERT
       7. 트랜잭션 완료 콜백: 롤백 확정 → 이 요청이 만든 파일 삭제 / 결과 불명확 → 파일 보존·WARN 로그
```

- 저장소 인터페이스는 `FileStorage`, 구현은 로컬 파일시스템 `LocalFileStorage`다(ADR-0001 Q2). 저장 루트는 `DOCFLOW_STORAGE_DIR`(기본 `./storage`, `backend/`에서 실행 시 `backend/storage`, Git 제외)이다.
- 저장 키는 서버가 만든 `documents/<UUID>`이며 원본 파일명을 경로에 쓰지 않는다. 저장 키와 서버 경로, SHA-256, Content Type은 API 응답에 넣지 않는다.
- DB 트랜잭션은 파일이 최종 위치에 놓인 뒤에만 시작하므로 "DB 행은 있는데 파일이 없는" 상태는 생기지 않는다. 반대로 프로세스 강제 종료나 보상 삭제 실패 시 고아 파일이 남을 수 있으며 자동 정리는 없다(MVP 잔여 위험).
- 파일 수신·검증·배치는 `DocumentFileIntake`, 보상 등록 후 트랜잭션 실행은 `StoredFileTransaction`이 담당하며 문서 등록과 새 버전 등록이 함께 쓴다(Spring Bean이 아닌 일반 객체, SPEC-04 결정 I1).
- **알려진 제한사항(SPEC-03 결정 D4-1, 미해결):** Tomcat은 요청 상한(21MB)을 넘는 요청을 본문 수신 전에 `413`으로 응답하고 연결을 닫는다. 이때 Next.js rewrite 프록시가 업로드 중인 클라이언트 요청을 끊어, 브라우저는 `413` 대신 연결 오류(`Failed to fetch`)를 받을 수 있다. 정상 화면은 20MB 초과 파일을 전송 전에 막고, 연결 오류 시 파일 크기 확인 안내를 표시한다. 모든 초과 요청에서 `413` 전달을 보장하지 않는다.

### 새 버전 등록 흐름 (SPEC-04)

```text
DocumentVersionController ─▶ DocumentVersionService
  1. 프로젝트 접근 확인(404) → 새 버전 등록 권한(requireVersionUploader, OWNER·MEMBER)
  2. 문서 조회(projectId + documentId, 404) → 상태 사전 확인(DRAFT·CHANGES_REQUESTED 외 409)
  3. 파일 수신·검증·CREATE_NEW 배치 (DocumentFileIntake, 문서 등록과 동일)
  4. DB 트랜잭션(StoredFileTransaction, 보상 등록)
     4-1. 문서 행 잠금: SELECT … FROM documents WHERE id=? AND project_id=? FOR NO KEY UPDATE (join 없음, users 행 미잠금)
     4-2. 상태 재검증(409 → 롤백 → 보상 삭제)
     4-3. MAX(version_number) + 1 → DocumentVersion INSERT
     4-4. documents.updated_at 갱신(flush) → 커밋 → 201 + 갱신된 DocumentResponse
```

- 같은 문서의 동시 등록은 문서 행 잠금으로 직렬화되어 번호가 중복·누락 없이 할당된다. 잠금은 파일 배치가 끝난 뒤의 짧은 DB 작업 동안만 잡으며 잠금 대기 상한은 두지 않는다(OQ-4). 잠금을 기다리는 요청은 커넥션을 하나씩 점유한다.
- 문서 상태는 바꾸지 않는다. 예상하지 못한 `uk_document_versions_document_version` 위반은 원인 로그(`projectId`·`documentId`·`versionNumber`·제약 이름·`storageKey`)를 남기고 `500`으로 응답한다.

### 버전 조회·다운로드·미리보기 흐름 (SPEC-05)

```text
DocumentVersionController
  GET …/versions                       → DocumentVersionQueryService: 접근(404) → 문서(404) → 버전 목록(업로더 join, 최신 먼저)
  GET …/versions/{n}/download          → 접근·문서·버전 확인(readOnly 트랜잭션 종료) → 파일 크기 확인(누락·불일치 500 DOCUMENT_FILE_UNAVAILABLE)
                                          → attachment·RFC 5987 파일명·CSP sandbox로 스트리밍
  GET …/versions/{n}/preview           → DocumentPreviewService
       PDF 원본 → 원본 스트리밍
       Office  → previews/cache/<sha256(storageKey)>.pdf 있으면 스트리밍
               → 없으면 작업 등록(같은 버전은 1개만, 동시 1·대기열 10, 가득 차면 503) → 202 + Retry-After
               [작업] previews/work/<UUID>/ 에 input.<ext> 복사 → (래퍼 +) soffice --convert-to pdf, 독립 프로필, 60초 제한
                      → %PDF·100MiB 확인 → 캐시로 원자적 이동 → 작업 디렉터리 삭제 / 실패·시간 초과 → 422 기록
```

- 변환 엔진은 `DocumentPreviewConverter` 인터페이스 뒤의 `LibreOfficePreviewConverter`이며 LibreOffice를 자식 프로세스로 실행한다([ADR-0006](./adr/0006-document-preview-conversion.md)). 셸을 거치지 않는 인수 배열, 작업별 디렉터리·사용자 프로필, 매크로·링크 갱신 비활성화, 최소 환경 변수, 시간 초과 시 프로세스 트리 종료를 적용한다. Linux에서는 `docflow.preview.launcher`(예: `unshare,-rn`)로 네트워크를 차단할 수 있고, 지정한 래퍼가 동작하지 않으면 격리 없이 실행하지 않는다.
- 캐시는 원본(`documents/`)과 분리된 `previews/cache/`에 두고 API의 접근 확인을 통과한 요청에만 전달한다. 전체 용량 상한(기본 2GiB, LRU)과 30일 미사용 정리를 적용하며 전송 중인 캐시와 원본은 삭제하지 않는다. 작업·실패 상태와 캐시 색인은 메모리에 있으며 단일 인스턴스를 가정한다.
- Frontend는 `fetch`로 받은 PDF를 `application/pdf`로 고정한 Blob URL(`#navpanes=0&view=FitH`)로 같은 화면의 iframe에 표시한다. Blob URL에는 응답 헤더가 적용되지 않으므로 `X-Frame-Options: DENY` 등 기존 보안 설정을 바꾸지 않는다. iframe `sandbox`는 Chromium 내장 PDF 뷰어를 막으므로 쓰지 않는다(SPEC-05 §7.4).

## 6. 보안 관련 현재 상태

- Spring Security 서버 세션 인증을 사용한다(SPEC-AUTH, [ADR-0005](./adr/0005-session-authentication.md)). JWT는 사용하지 않는다.
- 인가 규칙: `/actuator/health`, `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/logout`만 인증 없이 허용하고, 나머지 `/api/**`는 로그인이 필요하며, 그 외 경로는 모두 거부한다.
- CSRF: `csrf.spa()` (쿠키 `XSRF-TOKEN` → 헤더 `X-XSRF-TOKEN`). 로그인 성공 시 세션 ID 변경과 CSRF 토큰 교체를 수행하고 SecurityContext를 세션에 명시적으로 저장한다.
- 세션: 서버 메모리, 미사용 30분 만료, 쿠키 `HttpOnly`·`SameSite=Lax`, `Secure`는 `SESSION_COOKIE_SECURE`로 제어.
- Actuator는 `health` 전체 상태만 노출하고 상세 정보는 숨긴다(ADR-0003 §4).
- 비밀정보는 `.env`(Git 제외)로만 주입한다. 개발 계정 비밀번호(`DEV_SEED_PASSWORD`)도 같다.
- 프로젝트 접근(SPEC-01 §7.8, SPEC-02 §7):
  - 구성원(OWNER 또는 MEMBER)만 접근할 수 있다. `ProjectService.getAccessibleProject()`가 단일 진입점이며, 구성원이 아닌 사용자와 없는 프로젝트는 같은 `404 PROJECT_NOT_FOUND`로 응답한다. 목록 조회도 같은 조건을 쓴다.
  - 작업별 권한은 `ProjectPermissionService`에서만 판단한다. 구성원 추가·제거는 OWNER 전용이며 MEMBER는 `403 ACCESS_DENIED`를 받는다.
  - 프로젝트 목록·상세 응답의 `myRole`(`OWNER`/`MEMBER`)로 Frontend가 Owner 전용 UI를 표시하며, 최종 판단은 Backend가 한다.
- 구성원 API(SPEC-02 §6): `GET /api/projects/{projectId}/members`(구성원), `POST /api/projects/{projectId}/members` `{ email }`(OWNER, `201`), `DELETE /api/projects/{projectId}/members/{userId}`(OWNER, `204`).
  - 응답에는 이메일을 포함하지 않으며(`userId`, `displayName`, `role`, `addedAt`), 입력 이메일은 오류 응답과 애플리케이션 로그에 남기지 않는다.
  - 동시 추가로 인한 `uk_project_members_project_user` 위반은 트랜잭션 롤백 후 `409 PROJECT_MEMBER_ALREADY_EXISTS`로 변환하고, 그 밖의 DB 오류는 `500`으로 처리한다(`ProjectMemberService`, `TransactionTemplate`).
- 문서 API(SPEC-03 §6): `POST /api/projects/{projectId}/documents`(multipart `title`·`description`·`file`, 구성원, `201` + `Location`), `GET /api/projects/{projectId}/documents`(목록), `GET /api/projects/{projectId}/documents/{documentId}`(상세).
  - OWNER·MEMBER 모두 허용하고, 비구성원은 `404 PROJECT_NOT_FOUND`다. 문서는 항상 경로의 `projectId`와 `documentId`를 함께 조건으로 조회해 다른 프로젝트 문서 ID는 `404 DOCUMENT_NOT_FOUND`로 응답한다(IDOR 방어).
  - 형식·파일명·빈 파일 오류는 `400 VALIDATION_FAILED` + `fieldErrors[file]`, 크기 초과는 `413 FILE_TOO_LARGE`, 저장·DB 오류는 내부 경로 없는 `500 INTERNAL_ERROR`다.
  - CSRF 헤더 없이 크기를 초과한 요청은 CSRF 판단 전에 multipart 해석이 일어나 Spring Boot 기본 오류 형식의 `413`을 받는다(정상 SPA 요청은 해당 없음).
- 버전 조회 API(SPEC-05 §6): `GET …/documents/{documentId}/versions`(목록), `GET …/versions/{versionNumber}/download`(원본), `GET …/versions/{versionNumber}/preview`(PDF `200` / 변환 중 `202` / 실패 `422 DOCUMENT_PREVIEW_FAILED` / 엔진 없음·대기열 초과 `503 DOCUMENT_PREVIEW_UNAVAILABLE`). 구성원만, 다른 문서의 버전 번호는 `404 DOCUMENT_VERSION_NOT_FOUND`, 저장 키·서버 경로·캐시 경로는 응답에 넣지 않는다.
- 새 버전 API(SPEC-04 §6): `POST /api/projects/{projectId}/documents/{documentId}/versions`(multipart `file`만, 구성원, `201` + `Location`은 문서 상세). 허용되지 않은 상태는 `409 DOCUMENT_STATUS_CONFLICT`(본문에 상태 값 없음), 그 밖의 오류 계약은 문서 등록과 같다.
- MVP 한계: `/me` 이외의 보호 API는 요청마다 계정 활성 여부를 다시 확인하지 않는다. 로그인 실패 횟수 제한이 없다.

## 7. 다음 단계에서 추가될 요소 (미생성)

| 시점 | 추가 요소 |
| --- | --- |

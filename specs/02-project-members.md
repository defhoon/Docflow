# SPEC-02: 프로젝트 구성원 관리

- 상태: **v1.1 설계 확정 (2026-10-27 사용자 승인) — Backend 구현 승인, Frontend 구현 미승인**
- Issue: [#13](https://github.com/defhoon/Docflow/issues/13)
- 브랜치: `feat/13/project-members`
- 관련 문서: [`spec.md`](../spec.md) §4.1, §5, §9, §12, §16.2 / [`specs/01-project-create.md`](./01-project-create.md) §7.4, §7.7, §7.8, §13 / [`specs/00-auth.md`](./00-auth.md) / [ADR-0002](../docs/adr/0002-package-structure.md), [ADR-0005](../docs/adr/0005-session-authentication.md) / [`CLAUDE.md`](../CLAUDE.md) §3, §4
- 작성일: 2026-10-27
- 변경 이력:
  - v0.1 (2026-10-27): 설계 초안(대화), Q1~Q12 제시
  - v0.2 (2026-10-27): Q3 확정(이메일 비노출) 반영, Q13·Q14 추가
  - v1 (2026-10-27): Q1~Q14 확정 반영, UNIQUE 위반의 409 변환·트랜잭션 설계, 계정 존재 노출 위험과 완화 방안, 동명이인 위험 기록. 추가 결정 D1~D3 반영, 409 변환은 관찰 가능한 동작만 확정하고 구현 방식(`TransactionTemplate` 등)은 권장 후보로 기록. 문서 작성
  - v1.1 (2026-10-27): 설계 승인. 비구성원 404 보장의 "유효한 요청" 조건(D4), 제거에 따른 권한 변경 시점(D5), 이메일 원문 로그 미기록(D6) 명시

표기: **[기존]** 이미 구현·확정된 내용, **[결정 Qn]** 사용자가 확정한 결정, **[제안]** 이 SPEC의 설계안.

---

## 1. 목적 및 배경

- `spec.md` §4.1의 "프로젝트 생성 및 구성원 관리" 중 구성원 관리 부분이다(`spec.md` §9 SPEC-02).
- SPEC-01은 Owner만 자신의 프로젝트에 접근하도록 구현했다([기존] `findByIdAndOwnerId`, `findAllByOwnerId…`).
- SPEC-01 §7.8은 "프로젝트 접근"과 "작업별 권한"의 분리, 접근 조건을 Owner + 구성원으로 확장하는 일을 SPEC-02로 넘겼다.
- 이후 문서 등록(SPEC-03)과 검토 요청(SPEC-06, "Reviewer는 프로젝트 구성원")이 이 SPEC의 구성원 개념을 사용한다.

## 2. 목표

1. Owner가 이미 가입한 사용자를 프로젝트 구성원(MEMBER)으로 추가·제거할 수 있다.
2. 구성원은 프로젝트와 구성원 목록을 조회할 수 있고, 구성원이 아닌 사용자에게는 프로젝트의 존재가 드러나지 않는다.
3. 작업별 권한(OWNER 전용 작업)을 한 곳에서 판단하는 구조를 만들어 SPEC-03 이후에 재사용한다.
4. 구성원끼리 서로의 이메일을 알 수 없다.

## 3. 범위

### In Scope

| 항목 | 근거 |
| --- | --- |
| Owner가 가입 사용자를 이메일(완전 일치)로 MEMBER 추가 | [결정] 확정 정책, [결정 Q1] |
| Owner가 MEMBER 제거 (`userId` 기준) | [결정] 확정 정책 |
| Owner·MEMBER의 프로젝트 목록·상세 조회 (접근 조건 확장) | SPEC-01 §7.8 |
| Owner·MEMBER의 구성원 목록 조회 | [결정] 확정 정책 |
| 프로젝트 목록·상세 응답에 `myRole` 추가 | [결정 Q4] |
| 상세 화면의 구성원 섹션 (목록, Owner 전용 추가·제거) | [결정 Q9] |
| Flyway V4 `project_members` | [제안] |

### Out of Scope

| 항목 | 근거 |
| --- | --- |
| MEMBER 자발적 탈퇴 | [결정] 확정 정책 |
| 초대 메일·초대 수락 절차, 미가입 사용자 초대 | [결정] 기존 가입 사용자만 |
| 부분 일치 사용자 검색, 후보 조회 API | [결정 Q1] |
| 구성원 이메일 표시 | [결정 Q3] |
| Owner 이전, 공동 Owner, 역할 변경 | [제안] 제외 |
| Author/Reviewer 역할 | `spec.md` §16.2 미결정. SPEC-03·06에서 결정 |
| 구성원 수 상한 | [결정 Q8] 없음 |
| 추가한 사용자(`added_by`) 기록, 구성원 변경 AuditLog | [결정 Q7] SPEC-13 |
| 요청 횟수 제한(rate limiting) | [결정 Q2] 위험으로 기록, 향후 완화 (§12) |
| 프로젝트 수정·삭제, 목록 페이지네이션·검색 | SPEC-01과 동일 |

## 4. 사용자 시나리오

### 4.1 구성원 추가 (Owner)

1. Author(Owner)가 프로젝트 상세 화면의 구성원 섹션에서 Reviewer의 이메일을 입력하고 "추가"를 누른다.
2. 서버가 가입된 활성 사용자를 찾아 MEMBER로 저장한다.
3. 구성원 목록에 Reviewer의 표시 이름과 역할(MEMBER)이 나타난다. 이메일은 표시되지 않는다.

### 4.2 MEMBER의 조회

1. Reviewer가 로그인하면 `/projects` 목록에 Author의 프로젝트가 나타난다(역할: Member).
2. 상세 화면에서 프로젝트 정보와 구성원 목록(표시 이름, 역할)을 볼 수 있다. 추가·제거 UI는 보이지 않는다.

### 4.3 구성원 제거 (Owner)

1. Author가 Reviewer 행의 "제거"를 누르면 확인 대화상자가 표시된다.
2. 확인하면 서버가 해당 `userId`의 구성원 행을 삭제한다.
3. Reviewer는 다음 요청부터 목록에서 프로젝트가 사라지고, 상세 URL은 "프로젝트를 찾을 수 없습니다."가 된다.

### 4.4 구성원이 아닌 사용자

- 프로젝트 ID를 알아도 목록·상세·구성원 API 모두 존재하지 않는 프로젝트와 같은 `404`를 받는다([기존] SPEC-01 §7.4).

## 5. 도메인 및 데이터 모델

### 5.1 역할 모델 [결정]

| 역할 | 저장 위치 | 설명 |
| --- | --- | --- |
| `OWNER` | [기존] `projects.owner_id` | 프로젝트 생성자. 프로젝트당 1명, 변경 불가 |
| `MEMBER` | `project_members` 행 | Owner가 추가한 사용자 |

- **"프로젝트 구성원" = OWNER + MEMBER** [결정 Q10]. 이후 SPEC(예: SPEC-06 "Reviewer는 프로젝트 구성원")에서도 이 정의를 사용한다. 실제 검토 권한은 해당 SPEC에서 결정한다.
- `project_members`에는 MEMBER만 저장하고 `role` 컬럼을 두지 않는다. 역할은 `owner_id` 일치 여부와 행 존재 여부로 계산한다.
- Owner를 MEMBER로 저장하지 않는 규칙은 다른 테이블을 참조하므로 DB `CHECK`로 표현할 수 없다. 애플리케이션(Service)에서 막는다. Owner 변경 기능이 없으므로 이후에 이 규칙이 깨지는 경로는 없다.

### 5.2 Flyway V4 [제안]

```sql
-- SPEC-02 (Issue #13): 프로젝트 구성원(MEMBER). Owner는 projects.owner_id로 관리하며 이 테이블에 저장하지 않는다.
CREATE TABLE project_members (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  BIGINT      NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id)
);

-- 내 프로젝트 목록(구성원으로 참여한 프로젝트) 조회용
CREATE INDEX ix_project_members_user ON project_members (user_id);
```

- `UNIQUE (project_id, user_id)`가 만드는 인덱스는 "프로젝트별 구성원 목록"과 "접근 확인" 조회에도 사용된다.
- `ON DELETE CASCADE`(project): 프로젝트 삭제 기능이 없으므로 현재 동작하지 않는다. 삭제가 도입되면 그 SPEC에서 재검토한다.
- `ON DELETE RESTRICT`(user): 사용자 삭제 기능이 없다. SPEC-01의 `owner_id`와 같은 정책이다.
- `updated_at`은 두지 않는다. 구성원 행은 생성·삭제만 하며 수정하지 않는다. 따라서 Entity는 `BaseTimeEntity`를 상속하지 않고 `created_at`만 매핑한다([제안], 구현 시 확인).
- 구성원 행 삭제는 물리 삭제다 [결정 Q6]. 이력은 SPEC-13 AuditLog의 범위다.

### 5.3 Entity·Repository [제안]

- `ProjectMember`: `id`, `project`(`@ManyToOne LAZY`), `user`(`@ManyToOne LAZY`), `createdAt`. 정적 생성 메서드 `create(project, user)`.
- `ProjectMemberRepository`:
  - `existsByProjectIdAndUserId(projectId, userId)`
  - `findAllByProjectIdOrderByCreatedAtAscIdAsc(projectId)` (구성원 목록, `user` fetch join으로 N+1 방지)
  - `deleteByProjectIdAndUserId(projectId, userId)` → 삭제 건수 반환
- `ProjectRepository` 변경 (접근 조건 확장, §7.2):
  - 목록: Owner이거나 MEMBER인 프로젝트, `created_at DESC, id DESC`
  - 단건: `id`가 일치하고 Owner이거나 MEMBER인 프로젝트
  - 두 조회는 같은 조건식을 사용한다(SPEC-01 §7.8 원칙 4).
- 사용자 조회는 [기존] `UserAccountService`를 통해서만 한다(ADR-0002).

### 5.4 입력 검증 [결정 Q8]

- 추가 요청의 `email`은 SPEC-AUTH 로그인과 같은 규칙을 사용한다.
  - DTO compact constructor에서 `strip()`
  - `@NotBlank` "이메일을 입력해 주세요.", `@Email` "이메일 형식이 올바르지 않습니다.", `@Size(max = 255)` "이메일은 255자 이하로 입력해 주세요."
- 조회는 [기존] `UserAccountService.findByEmail`(trim + 소문자 정규화, `lower(email)` 유니크 인덱스)로 한다. 정확히 일치하는 계정만 찾는다.
- 구성원 수 상한은 없다.

## 6. Backend API 명세

### 6.1 공통 [기존 재사용]

- 인증: Spring Security 세션. 모든 엔드포인트는 로그인 필요(`/api/**` 규칙). `SecurityConfig` 변경 없음.
- CSRF: 변경 요청(`POST`, `DELETE`)은 `X-XSRF-TOKEN` 헤더 필요.
- 오류 형식: `{ code, message, fieldErrors? }`.
- 숫자가 아닌 `projectId`·`userId` 경로 변수: [기존] `400 BAD_REQUEST` (SPEC-01 Q14 처리 재사용).

### 6.2 엔드포인트

| 메서드·경로 | 권한 | 성공 |
| --- | --- | --- |
| `GET /api/projects/{projectId}/members` | 구성원 (OWNER, MEMBER) | `200`, `MemberResponse[]` |
| `POST /api/projects/{projectId}/members` | OWNER | `201`, `MemberResponse`. `Location` 헤더 없음 [결정 Q14] |
| `DELETE /api/projects/{projectId}/members/{userId}` | OWNER | `204` |
| [변경] `GET /api/projects` | 로그인 사용자 | `200`, 항목에 `myRole` 추가 |
| [변경] `GET /api/projects/{projectId}`, [변경] `POST /api/projects` 응답 | 구성원 / 로그인 사용자 | 응답에 `myRole` 추가 (`POST`는 항상 `OWNER`) [결정 Q4, D1] |

### 6.3 요청·응답 본문

```jsonc
// POST /api/projects/{projectId}/members
{ "email": "reviewer@docflow.local" }

// MemberResponse — 이메일을 포함하지 않는다 [결정 Q3]
{ "userId": 2, "displayName": "Reviewer", "role": "MEMBER", "addedAt": "2026-10-27T09:00:00Z" }

// GET /api/projects/{projectId}/members — Owner가 첫 항목, MEMBER는 추가 순(created_at ASC, id ASC)
[
  { "userId": 1, "displayName": "Author",   "role": "OWNER",  "addedAt": null },
  { "userId": 2, "displayName": "Reviewer", "role": "MEMBER", "addedAt": "2026-10-27T09:00:00Z" }
]

// GET /api/projects 항목, GET /api/projects/{id} 응답 — 기존 필드 유지 + myRole
{ "...": "기존 필드", "myRole": "OWNER" }
```

- `addedAt`: Owner는 구성원 행이 없으므로 `null`. API 데이터로만 유지하며 화면에는 표시하지 않는다 [결정 Q13].
- `MemberResponse`에는 `email`, `passwordHash`, `enabled` 등 다른 필드를 넣지 않는다.
- `myRole` 추가는 기존 필드를 바꾸지 않는 하위 호환 변경이다 [결정 Q4].
- 정의되지 않은 요청 필드(`userId`, `role` 등)는 무시한다([기존] Jackson 설정, SPEC-01 Q8과 같은 원칙). 역할은 항상 `MEMBER`다.

### 6.4 오류 코드

| 코드 | HTTP | 메시지 | 상황 |
| --- | --- | --- | --- |
| [기존] `PROJECT_NOT_FOUND` | 404 | 프로젝트를 찾을 수 없습니다. | 존재하지 않거나 구성원이 아닌 프로젝트 |
| [기존] `ACCESS_DENIED` | 403 | 접근 권한이 없습니다. | MEMBER가 OWNER 전용 작업 요청 |
| [기존] `VALIDATION_FAILED` | 400 | (공통) | 이메일 누락·형식 오류·길이 초과 |
| [기존] `BAD_REQUEST` | 400 | (공통) | 숫자가 아닌 경로 변수 |
| [신규] `USER_NOT_FOUND` | 404 | 해당 이메일로 가입한 사용자를 찾을 수 없습니다. | 미가입 또는 비활성 계정 (응답 동일) |
| [신규] `PROJECT_MEMBER_ALREADY_EXISTS` | 409 | 이미 프로젝트 구성원입니다. | 이미 MEMBER, Owner 자신 추가 [결정 Q5], 동시 추가 경합 |
| [신규] `PROJECT_MEMBER_NOT_FOUND` | 404 | 프로젝트 구성원을 찾을 수 없습니다. | 제거 대상이 이 프로젝트의 MEMBER가 아님 |
| [신규] `PROJECT_OWNER_NOT_REMOVABLE` | 400 | 프로젝트 Owner는 제거할 수 없습니다. | `userId`가 Owner |

- 오류 응답의 `message`·`fieldErrors`에 입력한 이메일을 포함하지 않는다 [결정 Q3].
- **로그:** 추가 요청의 이메일 원문을 애플리케이션 로그에 의도적으로 기록하지 않는다 [결정 D6]. 오류·디버그 로그에는 `projectId`, 세션 사용자 ID, 대상 `userId`(조회된 경우)만 남긴다. 프레임워크가 남기는 요청 로그 설정은 바꾸지 않으며, 요청 본문 로깅을 새로 켜지 않는다.
- `409`는 이 SPEC에서 처음 사용한다. [기존] `GlobalExceptionHandler`의 `BusinessException` 처리로 별도 핸들러 없이 반환된다(구현 시 확인).

### 6.5 처리 순서

```text
CSRF → 인증 → 경로 변수 타입(400) → 본문 검증(400) → 프로젝트 접근(404) → OWNER 권한(403) → 대상 사용자 조회(404) → 저장(409)
```

- 경로 변수 타입과 본문 검증은 Spring MVC가 Service 호출 전에 수행한다. Service 안에서는 접근(404)을 권한(403)보다 먼저 확인하므로, **형식이 올바른 요청**에서 비구성원은 항상 `404`를 받고 존재 여부를 알 수 없다(SPEC-01 §7.8 원칙 1, [결정 D4]).
- 본문 검증에 실패하면 접근 확인보다 먼저 `400`이 반환된다(비구성원이 잘못된 본문을 보내도 `400`). 이 응답은 프로젝트 존재 여부와 무관하게 같으므로 존재 노출 문제가 없으며, 이 순서를 허용한다 [결정 D2].

## 7. 인증·인가 및 트랜잭션

### 7.1 권한 표

| 작업 | OWNER | MEMBER | 비구성원 |
| --- | --- | --- | --- |
| 프로젝트 목록에 표시 | O | O | X |
| 프로젝트 상세 조회 | O | O | `404` |
| 구성원 목록 조회 | O | O | `404` |
| 구성원 추가 | O | `403` | `404` |
| 구성원 제거 | O | `403` | `404` |

### 7.2 접근 확인과 권한 판단 [제안]

- [변경] `ProjectService.getAccessibleProject(projectId, userId)`: Owner이거나 MEMBER이면 통과, 아니면 `404 PROJECT_NOT_FOUND`. 단일 쿼리로 판단해 미존재와 비구성원의 처리 경로를 같게 한다([기존] SPEC-01 §7.4).
- [신규] `ProjectPermissionService` (`project.service` 패키지):
  - `ProjectRole resolveRole(Project project, Long userId)`: `owner_id` 일치 → `OWNER`, 구성원 행 존재 → `MEMBER`. 접근 확인을 통과한 프로젝트에만 사용한다.
  - `void requireOwner(Project project, Long userId)`: `OWNER`가 아니면 `403 ACCESS_DENIED`.
  - 작업별 권한 규칙은 이 컴포넌트에만 둔다. Controller·Repository·Frontend에는 두지 않는다(SPEC-01 §7.8 원칙 2).
- 역할은 서버 데이터로만 판단한다. 요청의 역할·Owner 값은 사용하지 않는다(원칙 3).
- 목록 조회(`findMine`)와 `getAccessibleProject`는 같은 조건을 사용한다(원칙 4).
- 메서드 보안(`@PreAuthorize`)이나 권한 프레임워크는 도입하지 않는다(원칙 6).
- 제거된 사용자: 제거 트랜잭션이 **커밋된 이후 시작되는 요청부터** 목록에서 빠지고 상세·구성원 API는 `404`다 [결정 D5]. 커밋 전에 이미 시작된 요청은 그 요청의 조회 시점 데이터로 처리될 수 있다. 세션을 끊지는 않는다.
- 비활성 계정: 추가 대상에서 제외(`USER_NOT_FOUND`). 이미 MEMBER인 사용자가 비활성화된 경우의 처리는 SPEC-AUTH R6 한계를 따른다(이 SPEC에서 별도 처리 없음).

### 7.3 구성원 추가 트랜잭션과 409 변환 [제안]

**정상 경로 (사전 확인)**

1. 접근 확인 → `requireOwner`
2. `UserAccountService.findByEmail` → 없거나 비활성 → `404 USER_NOT_FOUND`
3. 대상이 Owner → `409 PROJECT_MEMBER_ALREADY_EXISTS`
4. `existsByProjectIdAndUserId` → 있으면 `409`
5. `ProjectMember` 저장 → `201`

**경합 경로 (UNIQUE 위반)**

- 같은 사용자를 동시에 추가하면 두 요청이 모두 4번을 통과할 수 있다. 이때 늦은 INSERT가 `uk_project_members_project_user` 위반으로 실패한다.

**확정 동작 (관찰 가능한 결과)** [결정]

| 상황 | 응답 | DB 상태 |
| --- | --- | --- |
| `uk_project_members_project_user` 위반 (경합) | `409 PROJECT_MEMBER_ALREADY_EXISTS` | 실패한 요청의 변경은 모두 롤백, 구성원 행은 1건 |
| 그 밖의 DB 오류 (FK 위반 등) | `500 INTERNAL_ERROR` (409로 바꾸지 않음) | 롤백, 행 수 불변 |
| 두 경우 공통 | 응답 본문은 `code`·`message`뿐. SQL·제약 이름·예외 클래스는 서버 로그에만 | — |

- `UnexpectedRollbackException` 등 트랜잭션 처리 오류가 응답으로 나가지 않는다(경합 시 `500`이 아니라 `409`).
- UNIQUE 위반 여부는 **대상 제약(`uk_project_members_project_user`) 기준**으로 판단한다. 같은 23505(unique_violation)라도 다른 UNIQUE 제약이면 409로 바꾸지 않는다.

**구현 시 고려 사항** [제안 — 구체적인 방식은 Backend 구현 단계에서 검증 후 결정]

- **발생 시점:** `ProjectMember` ID는 `IDENTITY`이므로 Hibernate가 `save()`(=`persist`) 시점에 즉시 INSERT한다([기존] SPEC-01 §7.7). 현재 스키마에 지연(DEFERRABLE) 제약이 없으므로 위반은 `save()` 중에 발생한다. ID 전략·flush 방식이 바뀌면 커밋 시점으로 옮겨질 수 있으므로, 두 경우 모두 위 확정 동작을 지켜야 한다.
- **같은 트랜잭션 안에서 잡지 않는 이유:** Repository 메서드에서 예외가 전파되면 Spring은 참여 중인 트랜잭션을 rollback-only로 표시한다. 같은 `@Transactional` 메서드 안에서 잡고 정상 반환하면 커밋 시 `UnexpectedRollbackException`이 발생한다. PostgreSQL도 오류가 난 트랜잭션의 이후 명령을 거부한다(aborted transaction). 따라서 409 변환은 트랜잭션이 롤백된 뒤, 트랜잭션 바깥에서 하는 것이 안전하다.
- **권장 후보:** 추가 유스케이스의 진입 메서드에는 `@Transactional`을 두지 않고, 1~5단계를 `TransactionTemplate`(또는 별도 빈의 `@Transactional` 메서드)으로 실행한 뒤 진입 메서드에서 `DataIntegrityViolationException`을 잡아 판단한다. 커밋 중 발생한 위반도 이 지점에서 잡힌다. 응답 DTO는 트랜잭션 안에서 만든다(`open-in-view: false`).
- **판단 근거 후보:** 예외 원인 체인에서 Hibernate `ConstraintViolationException`의 제약 이름(대소문자 무시). 예외 클래스(`DuplicateKeyException` 여부 등)만으로는 판단하지 않는다. Spring·Hibernate 버전에 따라 변환 클래스가 다를 수 있다. 실제 원인 체인 구조는 구현 시 통합 테스트로 확인한다.
- **사전 확인을 두는 이유:** 대부분의 중복 요청을 DB 오류 없이 처리하고 오류 로그를 줄인다. 경합은 DB 제약이 최종적으로 막는다.

### 7.4 구성원 제거 트랜잭션 [제안]

- `@Transactional` 메서드 하나에서: 접근 확인 → `requireOwner` → 대상이 Owner면 `400 PROJECT_OWNER_NOT_REMOVABLE` → `deleteByProjectIdAndUserId` → 삭제 0건이면 `404 PROJECT_MEMBER_NOT_FOUND`.
- 대상은 경로의 `userId`와 경로의 `projectId`로만 정한다. 다른 프로젝트의 구성원 행은 조건에 맞지 않으므로 삭제되지 않는다.
- 같은 MEMBER를 동시에 두 번 제거하면 하나는 `204`, 다른 하나는 삭제 0건으로 `404`다. DB 오류는 발생하지 않는다.
- 권한 변경은 커밋 이후 시작되는 요청부터 적용된다(§7.2, [결정 D5]). 응답 `204`는 커밋 후에 반환되므로, `204`를 받은 뒤 시작된 요청에서는 제거된 사용자가 항상 `404`를 받는다.

### 7.5 계정 존재 노출 위험 [결정 Q2]

- 추가 요청의 결과(`201`/`409` 대 `404 USER_NOT_FOUND`)로 "이 이메일이 가입되어 있는가"를 알 수 있다.
- 누구나 프로젝트를 만들 수 있으므로, 로그인한 사용자는 자기 프로젝트에서 이메일을 하나씩 시도해 가입 여부를 확인할 수 있다.
- **MVP에서 수용하는 범위:** 완전 일치 조회만 허용하므로 목록을 통째로 얻을 수는 없다. 응답에는 이메일을 다시 돌려주지 않고, 미가입과 비활성은 같은 응답이다.
- **향후 완화 방안 (이 SPEC에서는 구현하지 않음):**
  1. 사용자·IP 단위 요청 횟수 제한(구성원 추가, 로그인 공통)
  2. 실패한 추가 시도의 AuditLog 기록과 모니터링(SPEC-13)
  3. 초대 방식 전환: 가입 여부와 관계없이 같은 "초대를 보냈습니다" 응답을 주고 수락으로 구성원이 되는 흐름

### 7.6 CSRF·세션 [기존 재사용]

- `POST`, `DELETE`는 [기존] CSRF 보호 대상이며 Frontend는 [기존] `lib/api-client.ts`를 사용한다.
- 세션이 만료되면 `401 AUTH_REQUIRED`이며 Frontend는 [기존] 세션 만료 흐름을 따른다.

## 8. Frontend 화면 및 상태 흐름

### 8.1 화면 [결정 Q9]

| 위치 | 변경 |
| --- | --- |
| `/projects` 목록 | 각 항목에 역할(Owner/Member) 표시 (`myRole`) |
| `/projects/[projectId]` 상세 | "구성원" 섹션 추가 |

- 새 라우트는 만들지 않는다. [기존] `Suspense` → `RequireAuth` → `ProjectDetail` 구조를 유지한다(SPEC-01 §8.2).

### 8.2 구성원 섹션

| 대상 | 표시 |
| --- | --- |
| 모든 구성원 | 구성원 목록: **표시 이름과 역할만** [결정 Q3, Q13]. 이메일과 추가 시각은 표시하지 않는다 |
| Owner만 | 이메일 입력 + "추가" 버튼, MEMBER 행마다 "제거" 버튼 |

- Owner 전용 UI의 표시 여부는 `myRole`로 정한다. 이는 사용성 목적이며 최종 판단은 Backend가 한다.
- **제거 확인 대화상자** [결정 Q13]: "{displayName}님을 프로젝트에서 제거할까요?" 확인 시에만 `DELETE` 요청. 요청은 대상 행의 `userId`로 보낸다.
- 추가 폼:
  - 클라이언트 검증: 빈 값, 255자 초과, 기본 이메일 형식(서버 검증이 최종 기준).
  - 제출 중 버튼 비활성화, 중복 제출 방지.
  - 성공하면 입력값을 비우고 목록을 다시 불러온다.
  - [기존] SPEC-01 교훈: `cacheComponents`의 `<Activity>`로 화면이 보존되므로, 재진입 시 제출 상태와 이전 입력이 남지 않게 한다.
- 오류 표시:

| 응답 | 표시 |
| --- | --- |
| `404 USER_NOT_FOUND` | "해당 이메일로 가입한 사용자를 찾을 수 없습니다." (입력 필드 오류) |
| `409 PROJECT_MEMBER_ALREADY_EXISTS` | "이미 프로젝트 구성원입니다." |
| `400 VALIDATION_FAILED` | 서버 `fieldErrors` 표시 |
| `403 ACCESS_DENIED` | "권한이 없습니다." + 상세·구성원 다시 불러오기(역할 변경 가능성) |
| `404 PROJECT_MEMBER_NOT_FOUND` | "이미 제거된 구성원입니다." + 목록 다시 불러오기 |
| `404 PROJECT_NOT_FOUND` | [기존] "프로젝트를 찾을 수 없습니다." 화면 |
| `401` | [기존] `useAuth().refresh()` → 로그인 화면 |
| 5xx·네트워크 | [기존] 일반 오류 문구 |

### 8.3 동명이인 위험 [결정 Q13]

- 이메일과 추가 시각을 화면에 표시하지 않으므로, `displayName`이 같은 구성원은 화면에서 구분할 수 없다.
- 제거 요청은 행에 연결된 `userId`로 보내므로 **선택한 행과 다른 사용자가 제거되지는 않는다.** 위험은 Owner가 같은 이름의 다른 행을 잘못 고르는 것이다.
- MVP에서는 이 위험을 수용하고 후속 개선 사항으로 기록한다(§12 R6, §13).

## 9. 예외 및 오류 처리 (`spec.md` §12 검토)

| 시나리오 | Backend | Frontend |
| --- | --- | --- |
| 미인증 | `401 AUTH_REQUIRED` | 로그인 화면 |
| 비구성원의 상세·구성원 API | `404 PROJECT_NOT_FOUND` (미존재와 같은 본문) | 404 화면 |
| MEMBER의 추가·제거 | `403 ACCESS_DENIED`, 데이터 변경 없음 | 권한 없음 문구 |
| 존재하지 않는 프로젝트 | `404 PROJECT_NOT_FOUND` | 404 화면 |
| 이메일 누락·형식·길이 오류 | `400 VALIDATION_FAILED` | 필드 오류 |
| 미가입·비활성 이메일 | `404 USER_NOT_FOUND` (같은 응답) | 필드 오류 |
| 이미 MEMBER, Owner 자신 추가 | `409 PROJECT_MEMBER_ALREADY_EXISTS` | 오류 문구 |
| 같은 사용자 동시 추가 (중복 요청·경합) | 하나 `201`, 나머지 `409` (§7.3) | 제출 중 비활성화 |
| Owner 제거 시도 | `400 PROJECT_OWNER_NOT_REMOVABLE` | (정상 UI에서는 발생하지 않음) |
| 이 프로젝트의 MEMBER가 아닌 `userId` 제거 | `404 PROJECT_MEMBER_NOT_FOUND` | 목록 다시 불러오기 |
| 같은 MEMBER 동시 제거 | 하나 `204`, 나머지 `404` | 목록 다시 불러오기 |
| 숫자가 아닌 경로 변수 | `400 BAD_REQUEST` | 404 화면 |
| CSRF 실패 | `403 CSRF_INVALID` | [기존] 1회 재시도 |
| UNIQUE 외 DB 오류 (FK 위반 등) | `500 INTERNAL_ERROR`, 롤백, 상세는 로그에만 | 서버 오류 문구 |
| 네트워크 실패 | — | 일반 오류 문구 + 다시 시도 |

- 상태 전이·파일 업로드는 이 SPEC에 해당하지 않는다.

## 10. 테스트 전략

### 10.1 Backend 단위 테스트

- `ProjectPermissionService`: OWNER·MEMBER·없음 판정, `requireOwner`의 403
- 409 변환 판정 로직: 대상 제약 이름 → 409, 다른 제약 이름·이름 없음 → 변환하지 않음
- `AddMemberRequest` 검증: 빈 값, 공백만, 형식 오류, 256자, 앞뒤 공백 제거

### 10.2 Backend 통합 테스트 (실제 PostgreSQL, 보안 설정 활성)

| 대상 | 내용 |
| --- | --- |
| 추가 | 201, 응답에 `email` 필드 없음, DB 행 생성, 대소문자·공백이 다른 이메일도 같은 사용자 |
| 조회 확장 | MEMBER의 목록에 프로젝트 포함(`myRole=MEMBER`), 상세 200, 구성원 목록 200(Owner 첫 항목) |
| 권한 | MEMBER의 추가·제거 403 + 행 수 불변, 비구성원의 모든 구성원 API 404(미존재와 본문 동일) |
| 이메일 비노출 | 구성원 목록·추가 응답에 `email` 키 없음, 오류 응답 본문에 입력 이메일 문자열 없음 |
| 미가입·비활성 | 둘 다 404 `USER_NOT_FOUND`, 응답 본문 동일 |
| 중복 | 이미 MEMBER → 409, Owner 자신 → 409 |
| **동시 추가** | 같은 사용자를 두 스레드에서 `CountDownLatch`로 동시에 추가 → 결과는 `201` 1건 + `409` 1건, 행 1건. 사전 확인 경로와 UNIQUE 경로 중 어느 쪽으로 처리돼도 결과가 같아야 한다. 여러 번 반복 실행해 결과를 확인한다. 비트랜잭션 테스트, 별도 커넥션으로 행 수 확인 |
| **UNIQUE 변환 경로 결정적 검증** | 사전 확인을 우회해 실제 `uk_project_members_project_user` 위반(23505)을 발생시킨다 → `409`이고 `UnexpectedRollbackException`·`500`이 아님, 행 1건. 우회 방법(저장 단계 직접 호출, 첫 행을 JDBC로 먼저 커밋 등)은 구현 방식에 맞춰 정한다 |
| **다른 DB 오류 구분** | 사전 확인 이후 대상 사용자 행이 없는 상태에서 저장해 FK 위반을 일으킨다 → `500 INTERNAL_ERROR`(409 아님), 행 수 불변, 응답에 SQL·제약 이름 없음. 방법은 구현 방식에 맞춰 정한다 |
| 제거 | 204 + 행 삭제, 제거된 사용자의 목록 미포함·상세 404 |
| **제거 대상 정확성** | 같은 `displayName`의 MEMBER 2명 중 한 명의 `userId`로 제거 → 그 사용자 행만 삭제되고 다른 사용자 행은 유지 |
| 제거 실패 | Owner `userId` → 400, 다른 프로젝트 구성원 `userId` → 404 + 그 프로젝트 행 유지, 구성원이 아닌 `userId` → 404 |
| 공통 | 미인증 401, CSRF 없음 403(행 수 불변), 숫자가 아닌 `projectId`·`userId` 400 |
| 스키마 | V4 FK·UNIQUE 제약 직접 INSERT 검증, `ddl-auto=validate` |
| **SPEC-01 회귀** | [기존] `ProjectApiIntegrationTest` 유지: 비구성원 상세 404(본문 동일), 다른 사용자 프로젝트 목록 미포함. `myRole` 추가에 맞춰 기대값만 보완 |

### 10.3 Frontend 테스트 (Vitest)

| 대상 | 내용 |
| --- | --- |
| API 함수 | 경로·메서드·본문(`{ email }`만), `DELETE`의 `userId` 경로 |
| 구성원 목록 | 표시 이름·역할만 표시, 이메일·추가 시각 미표시, 로딩·오류 상태 |
| 권한별 UI | Owner: 추가 폼·제거 버튼 표시 / MEMBER: 미표시 |
| 추가 | 클라이언트 검증, 제출 중 비활성화·중복 제출 방지, 성공 시 입력 초기화·목록 갱신, 404·409·400 오류 표시 |
| 제거 | 확인 대화상자, 취소 시 요청 없음, 확인 시 해당 행의 `userId`로 요청(동명이인 2명 중 선택한 행), 404 시 목록 갱신 |
| 세션·권한 | 401 → `refresh()`, 403 → 다시 불러오기 |
| `<Activity>` 재진입 | 추가 성공 후 화면을 숨겼다 다시 보이면 폼이 비어 있고 제출 가능 |
| 목록 | `myRole` 표시 |
| 회귀 | 기존 Frontend 테스트 유지, `npm run build` 성공 |

### 10.4 수동 검증 (Windows 로컬, 사용자)

1. Author 로그인 → 프로젝트 상세 → Reviewer 이메일로 추가 → 목록에 표시 이름·역할만 보임
2. Reviewer 로그인 → 목록에 Author 프로젝트(Member) → 상세·구성원 목록 조회 가능, 추가·제거 UI 없음, 이메일 미표시
3. Author가 Reviewer 제거(확인 대화상자) → Reviewer는 목록에서 사라지고 URL 직접 접근 시 "찾을 수 없습니다"
4. 미가입 이메일·이미 구성원·자기 자신 추가 시 오류 문구 확인
5. 추가·제거 요청의 `X-XSRF-TOKEN` 헤더와 `201`/`204` 확인

### 10.5 검증 환경과 주체

| 검증 | 주체 |
| --- | --- |
| Backend 단위·통합 (JDK 25, PostgreSQL) | GitHub Actions CI (클라우드에는 JDK 25 없음) |
| Frontend lint·test·build | Claude Code 클라우드, CI |
| 수동 E2E (§10.4) | 사용자 (Windows 로컬) |

## 11. Acceptance Criteria

| ID | 기준 | 확인 방법 | 검증 |
| --- | --- | --- | --- |
| **AC-MEMBER-01** | Owner는 가입된 활성 사용자를 이메일로 MEMBER로 추가할 수 있다 | `201`, `MemberResponse`, `role=MEMBER`, `Location` 없음, 행 생성. 대소문자·앞뒤 공백이 다른 이메일도 같은 사용자 | 통합 |
| **AC-MEMBER-02** | MEMBER는 프로젝트 목록·상세를 조회할 수 있다 | 목록에 포함(`myRole=MEMBER`), 상세 `200`, 순서는 `created_at DESC, id DESC` 유지 | 통합 |
| **AC-MEMBER-03** | 구성원은 구성원 목록을 조회할 수 있다 | `200`, Owner 첫 항목(`addedAt=null`), MEMBER는 추가 순 | 통합 |
| **AC-MEMBER-04** | 구성원 목록·추가 응답에 이메일이 없다 | 응답 JSON에 `email` 키 없음, 항목은 `userId`·`displayName`·`role`·`addedAt`뿐 | 통합 |
| **AC-MEMBER-05** | 오류 응답에 입력한 이메일이 포함되지 않는다 | `404`·`409`·`400` 응답 본문에 입력 이메일 문자열 없음 | 통합 |
| **AC-MEMBER-06** | MEMBER는 구성원을 추가·제거할 수 없다 | `403 ACCESS_DENIED`, 행 수 불변 | 통합 |
| **AC-MEMBER-07** | 비구성원은 프로젝트 존재를 알 수 없다 (유효한 요청 기준) | 형식이 올바른 요청(유효한 경로 변수, 검증을 통과하는 본문)에서 상세·구성원 목록·추가·제거 모두 `404 PROJECT_NOT_FOUND`, 미존재 프로젝트와 본문 동일, 목록 미포함. 본문 검증에 실패한 요청은 §6.5에 따라 `400`이 먼저 반환되며 이 보장의 대상이 아니다 | 통합 |
| **AC-MEMBER-08** | 미가입·비활성 이메일은 같은 응답이다 | 둘 다 `404 USER_NOT_FOUND`, 본문 동일, 행 생성 없음 | 통합 |
| **AC-MEMBER-09** | 이미 MEMBER인 사용자와 Owner 자신은 추가할 수 없다 | `409 PROJECT_MEMBER_ALREADY_EXISTS`, 행 수 불변 | 통합 |
| **AC-MEMBER-10** | 같은 사용자의 동시 추가는 하나만 성공한다 | `201` 1건 + `409` 1건, 행 1건, `500` 없음 | 통합 (동시성) |
| **AC-MEMBER-11** | UNIQUE 위반은 409로, 다른 DB 오류는 500으로 구분된다 | 사전 확인을 우회한 실제 UNIQUE 위반 → `409`(`UnexpectedRollbackException` 없음), FK 위반 → `500 INTERNAL_ERROR`, 둘 다 롤백·내부 정보 미노출 | 통합 |
| **AC-MEMBER-12** | Owner는 MEMBER를 `userId`로 제거할 수 있다 | `204`, 행 삭제, 제거된 사용자의 목록 미포함·상세 `404` | 통합 |
| **AC-MEMBER-13** | 제거는 지정한 `userId`의 구성원에게만 적용된다 | 같은 `displayName`의 MEMBER 2명 중 한 명 제거 시 다른 한 명 유지. 다른 프로젝트 구성원의 `userId`로는 `404`, 그 프로젝트 행 유지 | 통합 |
| **AC-MEMBER-14** | Owner는 제거할 수 없고, 구성원이 아닌 사용자 제거는 404다 | Owner → `400 PROJECT_OWNER_NOT_REMOVABLE`, 비구성원 → `404 PROJECT_MEMBER_NOT_FOUND` | 통합 |
| **AC-MEMBER-15** | 인증·CSRF·입력 오류가 기존 계약대로 처리된다 | 미인증 `401`, CSRF 없음 `403`(행 불변), 이메일 오류 `400 VALIDATION_FAILED`, 숫자가 아닌 경로 변수 `400` | 통합 |
| **AC-MEMBER-16** | Flyway V4 스키마가 적용된다 | `ddl-auto=validate` 기동, 직접 INSERT 시 중복은 UNIQUE 위반, 없는 `project_id`·`user_id`는 FK 위반 | 통합 |
| **AC-MEMBER-17** | SPEC-01 동작이 회귀하지 않는다 | [기존] 프로젝트 테스트 통과, 비구성원 상세 `404`(본문 동일), 다른 사용자 프로젝트 목록 미포함 | 통합 |
| **AC-MEMBER-18** | 구성원 화면은 표시 이름과 역할만 보여 준다 | 이메일·추가 시각 미표시, 목록에 `myRole` 표시 | Vitest |
| **AC-MEMBER-19** | 추가·제거 UI는 Owner에게만 보이고, 제거는 확인 후 해당 `userId`로 요청한다 | MEMBER에게 미표시, 확인 대화상자, 취소 시 요청 없음, 동명이인 중 선택한 행의 `userId`로 요청 | Vitest |
| **AC-MEMBER-20** | 추가 폼은 검증·중복 제출 방지·오류 표시·재진입 초기화를 한다 | 클라이언트 검증, 제출 중 비활성화, `404`·`409`·`400` 표시, 성공 후 초기화, `<Activity>` 재진입 시 새 폼 | Vitest |
| **AC-MEMBER-21** | 구성원 API의 `401`은 기존 세션 만료 흐름을 따른다 | `useAuth().refresh()` → `/login?...&reason=expired` | Vitest |
| **AC-MEMBER-22** | 실제 브라우저에서 추가·조회·제거 흐름이 동작한다 | §10.4 1~5 | 수동 (Windows 로컬) |
| **AC-MEMBER-23** | 기존 테스트와 CI가 보안 설정 변경 없이 통과한다 | CI Backend·Frontend 성공, `SecurityConfig` 인가 규칙 변경 없음 | CI |

### 11.1 SPEC-01 Acceptance Criteria와의 관계 [결정 Q12]

- SPEC-01 AC-PROJECT-09("목록에는 자신이 Owner인 프로젝트만")는 SPEC-02 이후 "자신이 Owner 또는 MEMBER인 프로젝트"로 확장된다.
- SPEC-01 AC-PROJECT-11·12("Owner는 상세 조회 가능", "다른 사용자의 프로젝트는 미존재와 구별 불가")는 각각 "구성원은 상세 조회 가능", "비구성원의 상세 조회는 미존재와 구별 불가"로 의미가 확장된다.
- SPEC-01 문서는 수정하지 않고 이 절에 기록한다. 기존 테스트는 Owner·비구성원 시나리오이므로 그대로 유효하다.

## 12. 기술적 고려사항 및 위험

| ID | 위험 / 고려사항 | 대응 |
| --- | --- | --- |
| R1 | 같은 트랜잭션 안에서 UNIQUE 위반을 잡아 `UnexpectedRollbackException`·500 발생 | 롤백 후 트랜잭션 바깥에서 변환하는 방식 권장(§7.3, 구현 시 확정), 결정적 통합 테스트(AC-MEMBER-11) |
| R2 | UNIQUE 외 DB 오류를 409로 잘못 변환해 실제 장애를 숨김 | 제약 이름 기준으로만 변환, FK 위반 500 테스트(AC-MEMBER-11) |
| R3 | 이메일로 계정 가입 여부 확인 가능 (계정 존재 노출) | [결정 Q2] MVP 수용, 완전 일치만 허용, 미가입·비활성 동일 응답. 향후: 요청 횟수 제한, 실패 AuditLog, 초대 방식 (§7.5) |
| R4 | 목록 조회와 접근 확인 조건 불일치 (목록에 보이는데 상세 404) | 같은 조건식 사용, 통합 테스트로 확인 |
| R5 | 목록·구성원 조회의 N+1 | 구성원 목록 `user` fetch join, 프로젝트 목록 응답에는 Owner 정보를 넣지 않음([기존]) |
| R6 | 동명이인 구성원을 화면에서 구분할 수 없음 | [결정 Q13] MVP 수용. 제거는 `userId` 기준이라 데이터 오류는 없음. 후속 개선 후보: 표시 이름 중복 시 구분 표시, 구성원 프로필 정보 |
| R7 | MEMBER가 열어 둔 화면에서 제거된 뒤 계속 사용 | 제거 커밋 이후 시작되는 요청에서 `404`, Frontend는 404 화면 표시. 커밋 전에 시작된 요청은 정상 처리될 수 있음(수용). 세션은 유지 |
| R8 | 이미 MEMBER인 사용자가 비활성화된 경우 | SPEC-AUTH R6 한계 유지 (세션 만료까지 조회 가능). 별도 처리 없음 |
| R9 | 동시성 테스트의 불안정성 | 결과(201 1건 + 409 1건, 행 1건)만 검증하고 처리 경로는 검증하지 않음. UNIQUE 경로는 별도 결정적 테스트로 검증 |
| R10 | `myRole` 추가로 기존 Frontend 타입·테스트 영향 | 기존 필드 유지(하위 호환), 타입·테스트 보완 |
| R11 | 제거된 MEMBER가 이후 SPEC에서 남긴 데이터(문서·리뷰) | [결정 Q11] SPEC-03·06에서 제거 시 제약 결정 |

## 13. 후속 SPEC과의 관계

| 후속 SPEC | 이 SPEC이 제공하는 것 | 후속에서 결정할 것 |
| --- | --- | --- |
| SPEC-03/04 Document·Version | 구성원 정의(OWNER+MEMBER), `getAccessibleProject`, `ProjectPermissionService` | 문서 등록·수정 권한(MEMBER 허용 여부), 구성원 제거 시 작성 문서 처리 |
| SPEC-06 이후 Review·Feedback | "프로젝트 구성원" 판정 | Reviewer 지정 대상, Author/Reviewer 역할 모델(`spec.md` §16.2), 제거된 Reviewer의 진행 중 리뷰 처리 |
| SPEC-13 Audit Log | 구성원 추가·제거 이벤트 발생 지점 | 추가자(`added_by`)·제거 이력, 실패한 추가 시도 기록 [결정 Q7] |
| 후속 개선 (미정) | — | 동명이인 구분 표시(R6), 요청 횟수 제한·초대 방식(R3) |

## 14. 결정 사항 및 미결정 사항

### 14.1 결정 사항 (2026-10-27, 사용자 확정)

| ID | 질문 | 결정 | 반영 위치 |
| --- | --- | --- | --- |
| 정책 | 기본 정책 | 가입 사용자를 이메일로 추가, `owner_id` 유지·`project_members`에는 MEMBER만, MEMBER는 조회만, 추가·제거는 OWNER만, 자발적 탈퇴 제외 | §3, §5.1, §7.1 |
| Q1 | 이메일 검색 방식 | 완전 일치만. 별도 후보 조회 API 없이 `POST {email}`로 추가하고 결과로 이름 표시 | §3, §5.4, §6.2 |
| Q2 | 계정 존재 노출 | MVP 수용, 위험과 향후 완화 방안 기록 | §7.5, §12 R3 |
| Q3 | 구성원 이메일 표시 | OWNER·MEMBER 모두 볼 수 없음. 목록·추가 응답에서 `email` 제외, 화면은 `displayName`·`role`, 식별·제거는 `userId`, 이메일은 추가 입력에만 사용 | §6.3, §6.4, §8.2, AC-MEMBER-04, AC-MEMBER-05, AC-MEMBER-18 |
| Q4 | `myRole` | 목록·상세 응답에 추가 | §6.2, §6.3 |
| D1 | `POST /api/projects` 응답의 `myRole` | 추가 (항상 `OWNER`) | §6.2 |
| D2 | 본문 검증 `400`이 접근 확인 `404`보다 먼저 나는 순서 | 허용 (Spring MVC 검증 순서) | §6.5 |
| D3 | 파일명 | `specs/02-project-members.md`로 통일 (`specs/README.md` 예시 포함) | 이 문서, `specs/README.md` |
| D4 | 비구성원 404 보장 범위 | 형식이 올바른 요청 기준. 본문 검증 실패는 `400`이 먼저(D2와 일치) | §6.5, AC-MEMBER-07 |
| D5 | 제거에 따른 권한 변경 시점 | 제거 트랜잭션 커밋 이후 시작되는 요청부터 적용 | §7.2, §7.4, §12 R7 |
| D6 | 이메일 로그 | 추가 요청의 이메일 원문을 애플리케이션 로그에 의도적으로 기록하지 않음 | §6.4 |
| Q5 | Owner 자신 추가 | `409 PROJECT_MEMBER_ALREADY_EXISTS` | §6.4, §7.3 |
| Q6 | 구성원 제거 방식 | 물리 삭제. `spec.md` §16.2 "삭제 기능 포함 여부"는 문서·프로젝트 삭제 질문으로 해석 | §5.2, §7.4 |
| Q7 | `added_by` | 기록하지 않음 (SPEC-13) | §3, §13 |
| Q8 | 상한·이메일 검증 | 상한 없음, SPEC-AUTH 이메일 검증·정규화 재사용 | §5.4 |
| Q9 | UI 위치 | 상세 화면 안의 섹션 | §8.1 |
| Q10 | 구성원 정의 | OWNER + MEMBER | §5.1 |
| Q11 | 제거된 MEMBER의 데이터 | SPEC-03·06에서 결정 | §12 R11, §13 |
| Q12 | SPEC-01 AC와의 관계 | SPEC-02에 의미 확장을 기록, SPEC-01 문서는 수정하지 않음 | §11.1 |
| Q13 | 동명이인 | 화면은 `displayName`·`role`만, `addedAt`은 API에만, 제거 확인 대화상자, 위험을 후속 개선으로 기록, `userId` 기준 제거 테스트 | §6.3, §8.2, §8.3, §12 R6, AC-MEMBER-13, AC-MEMBER-19 |
| Q14 | 추가 응답 `Location` | 두지 않음 (단건 조회 API 없음) | §6.2 |

### 14.2 미결정 사항

없음. 아래는 구현 중 확인할 기술 사항이며 정책 결정이 아니다. 결과가 이 SPEC과 다르면 보고 후 SPEC을 먼저 갱신한다.

- 409 변환의 구체적인 구현 방식(`TransactionTemplate`, 별도 빈의 `@Transactional` 메서드 등)과 UNIQUE 위반 판단 방법. §7.3의 확정 동작을 통합 테스트로 검증한 뒤 결정한다
- `DataIntegrityViolationException` 원인 체인에서 제약 이름을 얻는 정확한 경로 (Spring Framework 7.0.9 / Hibernate 7.4.5)
- `ProjectMember` Entity의 `created_at` 매핑 방식(`BaseTimeEntity` 미사용 시 Auditing 적용 여부)
- `GlobalExceptionHandler`가 `409` `BusinessException`을 추가 처리 없이 반환하는지

## 15. 구현 계획 및 단계별 체크리스트 (구현 승인 후)

0. [x] 이 SPEC 설계 확정 (v1.1, 2026-10-27 승인)
1. [x] GitHub Issue #13 생성, `feat/13/project-members` 브랜치 생성
2. [x] 문서: 이 SPEC 커밋, `specs/README.md` 파일 트리·표 갱신
3. [ ] Backend 도메인: `V4__create_project_members.sql`, `ProjectMember`, `ProjectMemberRepository`, `ProjectRole` + 단위 테스트
4. [ ] Backend 권한: `ProjectPermissionService`, 접근 조건 확장(`ProjectRepository`, `getAccessibleProject`, `findMine`), `myRole` + SPEC-01 회귀 테스트
5. [ ] Backend API: 구성원 목록·추가·제거, 신규 `ErrorCode`, 409 변환(§7.3 확정 동작, 구현 방식은 검증 후 결정) + 통합 테스트(동시 추가, UNIQUE·FK 구분 포함)
6. [ ] CI 확인 (Backend)
7. [ ] Frontend: `features/projects` 구성원 API·타입·검증, 구성원 섹션, 목록 `myRole` 표시 + Vitest
8. [ ] Frontend lint·test·build (클라우드) → CI 확인
9. [ ] 문서: README, `docs/architecture.md`, 이 SPEC §16 결과 기록
10. [ ] Windows 로컬 수동 검증 (사용자, §10.4)
11. [ ] PR (`Closes #13`), 병합은 사용자

## 16. 검증 결과

**미실행.** 설계 검토 단계이며 구현과 테스트는 시작하지 않았다.

| 항목 | Claude Code 클라우드 | GitHub Actions CI | Windows 로컬 (사용자) |
| --- | --- | --- | --- |
| Backend build·test | 미실행 | 미실행 | 미실행 |
| Frontend lint·test·build | 미실행 | 미실행 | 미실행 |
| 수동 E2E (§10.4) | — | — | 미실행 |

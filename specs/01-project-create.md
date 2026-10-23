# SPEC-01: 프로젝트 생성·조회 및 소유권

- 상태: **설계 확정 (v3, 2026-10-23 사용자 승인) — Backend·Frontend 구현 완료(CI 통과), Windows 로컬 검증·PR 승인 대기**
- Issue: [#6](https://github.com/defhoon/Docflow/issues/6)
- 브랜치: `feat/6/project-create`
- 관련 문서: [`spec.md`](../spec.md) §4.1, §5, §9, §10, §12, §13, §15, §16 / [`specs/00-auth.md`](./00-auth.md) / [ADR-0002](../docs/adr/0002-package-structure.md), [ADR-0003](../docs/adr/0003-development-environment.md), [ADR-0005](../docs/adr/0005-session-authentication.md) / [`CLAUDE.md`](../CLAUDE.md) §3
- 작성일: 2026-10-23
- 변경 이력:
  - v1 (2026-10-23): 초안
  - v2 (2026-10-23): Q1~Q12 권장안 채택 반영, 입력 정규화·검증 규칙(§5.5), 오류 응답 계약 검증(§6.5), 트랜잭션·저장 실패 테스트(§7.7, §10.2), 권한 모델 확장 원칙(§7.8), 정렬 기준·Next.js `Suspense` 요구사항 보완
  - v3 (2026-10-23): Q13~Q15 결정 반영(문자 정책·`MethodArgumentTypeMismatchException` 400·UTF-16 길이), 저장 실패 테스트를 기존 FK 제약 기반으로 변경(테스트 전용 트리거 제거), 설계 확정

> **표기 규칙**
> - **[기존]**: `spec.md`, `specs/00-auth.md`, ADR, 현재 `main` 코드에 이미 있는 내용
> - **[제안]**: 이 SPEC이 새로 정의하는 설계 (승인 전까지 확정 아님)
> - **[결정 Qn]**: 사용자가 2026-10-23에 채택한 §14 결정
>
> **검증 근거 표기**: "(소스 확인)"은 Spring Boot 4.1.1이 관리하는 라이브러리(Spring Framework 7.0.9, Hibernate Validator 9.1.3.Final, Jackson 3.1.5)의 소스 또는 `frontend/node_modules`의 Next.js 16.4.0 번들 문서를 2026-10-23에 직접 확인했다는 뜻이다. 테스트 실행 결과가 아니다.

## 1. 목적 및 배경

### 1.1 기존 명세의 근거 [기존]

- `spec.md` §4.1 MVP In Scope: "프로젝트 생성 및 구성원 관리"
- `spec.md` §9 SPEC 표: `SPEC-01 Project 생성`, `SPEC-02 Project Member 관리`
- `spec.md` §13 MVP Acceptance Criteria: "사용자가 Project를 생성할 수 있다.", "Project에 Document를 등록할 수 있다."
- `spec.md` §10 SPEC-06: "요청자의 프로젝트 접근 권한 확인", "Reviewer는 동일 프로젝트의 구성원" — 이후 기능은 Project를 접근 권한의 단위로 사용한다.
- `spec.md` §12 공통 실패: "프로젝트 구성원이 아닌 사용자", "권한이 없는 사용자", "존재하지 않는 Resource", "잘못된 입력", "Transaction 실패"
- `specs/00-auth.md`: 서버 세션으로 현재 사용자를 식별하며, 요청 본문의 사용자 식별값은 신뢰하지 않는다.

### 1.2 기존 명세에 없는 것

- Project의 필드, 소유자(Owner) 개념, 조회 범위, 접근 권한 규칙은 기존 명세에 정의되어 있지 않다.
- 이 SPEC이 이를 정의한다.

## 2. 현재 문제와 해결 목표

### 현재 문제

- SPEC-AUTH로 사용자를 식별할 수 있지만, 문서·검토를 담을 상위 단위(Project)가 없다.
- `spec.md` §10의 "프로젝트 접근 권한"을 판단할 기준이 없다.

### 해결 목표 [제안]

- 로그인한 사용자가 프로젝트를 만들고, 자신이 접근할 수 있는 프로젝트만 조회한다.
- 프로젝트의 Owner를 `users` 테이블과의 FK 관계로 기록한다.
- 이후 SPEC이 재사용할 **프로젝트 접근 확인 지점**(`getAccessibleProject`)을 Service 계층에 하나로 만든다.

## 3. 범위

### In Scope

| 기능 | 근거 |
| --- | --- |
| 인증된 사용자의 프로젝트 생성 | [기존] `spec.md` §4.1, §9, §13 |
| 생성자를 Owner로 자동 지정 (`owner_id` → `users.id`) | [결정 Q1], [결정 Q8] |
| 내가 접근 가능한 프로젝트 목록 조회 (`created_at DESC, id DESC`) | [제안] 생성 기능을 화면에서 쓰기 위해 필요 |
| 프로젝트 상세 조회 | [제안] 이후 문서 기능의 진입점 |
| 다른 사용자의 프로젝트 접근 차단 | [기존] `spec.md` §12 |
| 프로젝트 목록·생성·상세 화면, `/`에 "내 프로젝트" 링크 | [제안], [결정 Q10] |
| 프로젝트 접근 확인 로직 (SPEC-01 기준: Owner만) | [제안] SPEC-02에서 확장 |

### Out of Scope

| 항목 | 근거 |
| --- | --- |
| 프로젝트 구성원 초대·관리 | [기존] `spec.md` §9 SPEC-02 |
| 프로젝트 삭제 | [기존] `spec.md` §16.2 삭제 기능 미결정 |
| 프로젝트 소유권 이전 | [제안] 제외 |
| 프로젝트 이름·설명 수정 | [결정 Q3] 제외 |
| 프로젝트 상태(Status) 필드 | [결정 Q2] 두지 않음 |
| 목록 페이지네이션·검색 | [결정 Q6] 없음. 검색은 [기존] `spec.md` §4.2 Post-MVP |
| 프로젝트 생성 AuditLog | [결정 Q7] SPEC-13에서 결정 |
| 계정 비활성화 시 프로젝트 API 재검증 | [결정 Q9] SPEC-AUTH 한계 유지 |
| 문서·버전·검토·승인 | [기존] SPEC-03 이후 |
| AI 기능 | [기존] Post-MVP |

`spec.md` §4.1의 "프로젝트 생성 및 구성원 관리"는 `spec.md` §9에서 SPEC-01과 SPEC-02로 나뉘어 있다. 따라서 구성원 관리를 제외해도 기존 명세와 충돌하지 않는다.

## 4. 사용자 시나리오 [제안]

### 4.1 프로젝트 생성

1. 로그인한 사용자가 `/projects`에서 "새 프로젝트"를 누른다.
2. `/projects/new`에서 이름(필수)과 설명(선택)을 입력해 제출한다.
3. 서버가 세션 사용자를 Owner로 지정해 저장한다.
4. 생성된 프로젝트의 상세 화면(`/projects/{id}`)으로 이동한다.

### 4.2 내 프로젝트 목록

1. 로그인한 사용자가 `/projects`에 접근한다.
2. 자신이 Owner인 프로젝트만 `created_at DESC, id DESC` 순서로 표시된다.
3. 프로젝트가 없으면 빈 상태 안내와 "새 프로젝트" 버튼이 표시된다.

### 4.3 다른 사용자의 프로젝트 접근

1. Reviewer 계정 사용자가 Author 프로젝트의 URL(`/projects/{id}`)을 직접 입력한다.
2. 서버는 존재하지 않는 프로젝트와 **같은** `404 PROJECT_NOT_FOUND`를 반환한다.
3. 화면에는 "프로젝트를 찾을 수 없습니다."가 표시된다.

### 4.4 세션 만료

1. 30분 이상 사용하지 않은 뒤 목록을 새로고침하거나 생성을 시도한다.
2. 서버가 `401 AUTH_REQUIRED`를 반환한다.
3. [기존] SPEC-AUTH 만료 흐름에 따라 `/login?next=...&reason=expired`로 이동한다.

## 5. 도메인 및 데이터 모델

### 5.1 User와 Project의 관계 [결정 Q1]

- `Project`는 Owner 한 명(`User`)을 가진다: `projects.owner_id → users.id` (N:1).
- Owner는 생성 시 **세션의 인증 사용자**로 자동 지정되며, 클라이언트가 지정할 수 없다([결정 Q8]).
- SPEC-01의 접근 규칙: **Owner만** 프로젝트를 조회할 수 있다.
- 구성원 관계와 역할은 SPEC-02에서 정의한다.

| 검토한 선택지 | 결과 |
| --- | --- |
| **A. `projects.owner_id` FK만** | **채택.** `spec.md` §16.2 Role 모델이 미결정인 상태에서도 확정할 수 있고, SPEC-02 범위를 침범하지 않는다 |
| B. `project_members`를 지금 만들고 `OWNER` 행 추가 | 제외. Role 컬럼 설계를 앞당겨 확정하게 됨 |
| C. Owner를 문자열(이메일 등)로 저장 | 제외. 무결성 보장 불가 |

### 5.2 Project 필드

| 필드 | 컬럼 | 타입 | 제약 | 근거 |
| --- | --- | --- | --- | --- |
| id | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | PK | [기존] ADR-0005 |
| name | `name` | `VARCHAR(100)` | NOT NULL, `CHECK (length(btrim(name)) > 0)`, 정규화·검증은 §5.5 | [결정 Q4] 100자 |
| description | `description` | `VARCHAR(1000)` | NULL 허용, 정규화·검증은 §5.5 | [결정 Q4] 1000자 |
| owner | `owner_id` | `BIGINT` | NOT NULL, FK → `users(id)` `ON DELETE RESTRICT` | [결정 Q1] |
| createdAt | `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT now() | [기존] `BaseTimeEntity` |
| updatedAt | `updated_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT now() | [기존] `BaseTimeEntity` |

- **이름 중복:** 같은 Owner의 같은 이름을 허용한다. 유니크 제약을 두지 않는다([결정 Q5]).
- **상태 필드:** 두지 않는다([결정 Q2]).

### 5.3 Flyway 마이그레이션 계획 [제안]

- 파일: `backend/src/main/resources/db/migration/V3__create_projects.sql` (구현 승인 후 생성)

```sql
CREATE TABLE projects (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(100)  NOT NULL,
    description  VARCHAR(1000),
    owner_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_projects_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- 내 프로젝트 목록: owner_id 조건 + created_at DESC, id DESC
CREATE INDEX ix_projects_owner_created ON projects (owner_id, created_at DESC, id DESC);
```

- `ddl-auto=validate`로 Entity와 스키마의 일치를 확인한다([기존] ADR-0003).

### 5.4 Entity 설계 [제안]

```text
com.docflow.project.domain.Project extends BaseTimeEntity
  - Long id
  - String name
  - String description          (nullable)
  - User owner                  (@ManyToOne(fetch = LAZY, optional = false), @JoinColumn(name = "owner_id"))
  + static Project create(String name, String description, User owner)
  + boolean isOwnedBy(Long userId)
```

- `Project.create`는 정규화가 끝난 값을 받는다(§5.5). 정규화는 DTO 한 곳에서만 수행해 규칙이 둘로 갈라지지 않게 한다.
- Entity는 API 응답으로 직접 반환하지 않는다([기존] `CLAUDE.md` §3-9).

### 5.5 입력 정규화 및 검증 규칙 [결정 Q4, Q13, Q15]

#### 처리 순서

1. **JSON 역직렬화:** 본문이 JSON이 아니거나 `name`·`description`이 객체·배열이면 `HttpMessageNotReadableException` → `400 VALIDATION_FAILED` (`fieldErrors` 없음, §6.5).
2. **정규화 (DTO compact constructor):** null 안전. 예외를 던지지 않는다.
3. **Bean Validation:** 정규화된 값에 대해 필수 여부(§5.5.2), 허용 문자(§5.5.3), 길이(§5.5.4)를 검사한다. **길이는 정규화 이후 값으로 검사**한다.
4. **저장:** DB `CHECK`·`VARCHAR(n)`은 마지막 방어선이다. 정상 경로에서는 3단계를 통과한 값만 도달한다.

#### 5.5.1 정규화 — 가장자리 공백 제거

- **가장자리 공백 문자:** `Character.isWhitespace(c) || Character.isSpaceChar(c)`를 만족하는 문자.
  - 예: U+0020 스페이스, `\t`, `\n`, `\r`, U+00A0 NBSP, U+2007, U+202F, U+3000 전각 공백, U+2028·U+2029
  - `String.strip()`(`isWhitespace` 기준)만으로는 NBSP·U+2007·U+202F가 제거되지 않으므로 `isSpaceChar`를 함께 사용한다.
- **`name`:** `null`이면 `null`. 아니면 앞뒤의 가장자리 공백 문자를 모두 제거한다.
- **`description`:** `null`이면 `null`. 앞뒤 가장자리 공백 문자를 제거한 결과가 `""`이면 `null`, 아니면 제거한 값.
- 문자열 **내부**는 변경하지 않는다. 연속 공백 축약, 유니코드 정규화(NFC 등)는 하지 않는다.

```java
public record CreateProjectRequest(String name, String description) {
    public CreateProjectRequest {
        name = ProjectText.trimEdges(name);                 // null → null
        description = ProjectText.trimEdgesToNull(description); // null·공백만 → null
    }
}
```

- `ProjectText`는 `project` 패키지의 정적 유틸리티다(이름은 구현 시 확정). 같은 규칙을 Bean Validation 커스텀 검증과 단위 테스트에서 공유한다.

#### 5.5.2 필수 여부

| 입력 (`name`) | 정규화 결과 | 응답 |
| --- | --- | --- |
| 필드 누락 | `null` | `400 VALIDATION_FAILED`, `fieldErrors[name]` "프로젝트 이름을 입력해 주세요." |
| `"name": null` | `null` | 위와 동일 (누락과 `null`은 record에서 구별할 수 없으므로 같은 규칙) |
| `""` | `""` | 위와 동일 |
| 공백 문자만 (`"   "`, `"\t\n"`, `"　"`, `"  "`) | `""` | 위와 동일 |

- `description`은 선택값이다. 누락·`null`·`""`·공백 문자만이면 모두 `null`로 저장하고 `201`로 응답한다.

#### 5.5.3 허용 문자

**`name` — 거부 문자** (하나라도 포함되면 `400`, `fieldErrors[name]` "프로젝트 이름에 사용할 수 없는 문자가 포함되어 있습니다.")

| 범주 | 문자 | 이유 |
| --- | --- | --- |
| 제어 문자 (Unicode `Cc`) | U+0000–U+001F, U+007F–U+009F. **`\t`, `\n`, `\r`, U+0000 포함** | 한 줄 식별자 표시를 깨뜨림. U+0000은 PostgreSQL에 저장 불가 |
| 줄·문단 구분자 (`Zl`, `Zp`) | U+2028, U+2029 | 줄바꿈으로 표시됨 |
| 폭 없는·보이지 않는 문자 | U+200B ZERO WIDTH SPACE, U+2060 WORD JOINER, U+FEFF BOM/ZWNBSP, U+180E MONGOLIAN VOWEL SEPARATOR | 같은 모양의 다른 이름을 만들어 식별을 방해 |
| 양방향 서식 제어 | U+202A–U+202E (embedding/override), U+2066–U+2069 (isolate) | 표시 순서를 바꿔 다른 이름처럼 보이게 함 |

**`name` — 보이는 문자 요구:** 정규화 후, `Zs`·`Zl`·`Zp`·`Cc`·`Cf`가 **아닌** 문자를 1개 이상 포함해야 한다. 예: U+200D ZWJ만으로 된 이름은 거부(위 문구와 동일).

**`name` — 허용 (명시)**
- 한글·영문·숫자·기호·이모지 등 일반 문자
- 내부 공백: U+0020은 물론 U+3000·U+00A0 같은 `Zs` 공백도 이름 **내부**에서는 허용 (가장자리는 5.5.1에서 제거)
- U+200C ZWNJ, U+200D ZWJ, U+200E LRM, U+200F RLM은 이모지 결합·일부 문자 체계·RTL 표기에 필요하므로 허용 (단, 이 문자들만으로 된 이름은 "보이는 문자 요구"로 거부)

**`description` — 거부 문자** (`400`, `fieldErrors[description]` "설명에 사용할 수 없는 문자가 포함되어 있습니다.")
- 제어 문자(`Cc`) 중 `\t`(U+0009), `\n`(U+000A), `\r`(U+000D)을 **제외한** 모든 문자. U+0000 포함.
- 그 밖의 문자(`Zl`·`Zp`, 폭 없는 문자, 양방향 서식 문자 포함)는 설명에서 제한하지 않는다. 자유 서술 필드이며 식별자가 아니기 때문이다.

#### 5.5.4 길이 [결정 Q15]

- 이름 1~100, 설명 0~1000. 정규화 **이후** 값으로 검사한다.
- 단위는 **UTF-16 code unit**이다. Java `String.length()` 기준이며 `@Size`와 같다(소스 확인). Frontend의 JavaScript `string.length`도 같은 단위다.
- **사용자에게 보이는 글자 수와 다를 수 있다.**
  - 보조 문자(대부분의 이모지 등)는 2로 센다.
  - 결합 문자 시퀀스(예: ZWJ로 이어진 가족 이모지)는 구성 code unit을 모두 센다.
  - 따라서 이모지가 많은 이름은 화면상 100자 미만이어도 거부될 수 있다.
- PostgreSQL `VARCHAR(n)`은 문자(code point) 수로 세므로, 애플리케이션 제한이 항상 같거나 더 엄격하다. 길이로 인한 DB 오류(500)는 발생하지 않는다.
- 별도 글자 수 계산 라이브러리는 도입하지 않는다.

#### 5.5.5 테스트 케이스 (단위 테스트와 Frontend 검증에서 공통 사용)

| # | 필드 | 입력 | 기대 |
| --- | --- | --- | --- |
| 1 | name | 누락 / `null` / `""` / `"   "` | `400` 필수 |
| 2 | name | `" 　\t"` | `400` 필수 (정규화 후 `""`) |
| 3 | name | `"  DocFlow 기획  "` | `201`, 저장 `"DocFlow 기획"` |
| 4 | name | `" DocFlow "` | `201`, 저장 `"DocFlow"` |
| 5 | name | `"Doc　Flow"` (내부 전각 공백) | `201`, 그대로 저장 |
| 6 | name | `"Doc\nFlow"`, `"Doc\tFlow"`, `"Doc\u0000Flow"` | `400` 허용 문자 |
| 7 | name | `"Doc​Flow"`, `"﻿DocFlow"` | `400` 허용 문자 |
| 8 | name | `"Doc‮Flow"`, `"Doc⁦Flow"` | `400` 허용 문자 |
| 9 | name | `"‍"` | `400` 허용 문자 (보이는 문자 없음) |
| 10 | name | `"👨‍👩‍👧"` (ZWJ 결합 이모지) | `201` |
| 11 | name | 정규화 후 100 code unit / 101 code unit | `201` / `400` 길이 |
| 12 | name | 앞뒤 공백 3개 포함 103 code unit (정규화 후 100) | `201` |
| 13 | name | 이모지 50개 (100 code unit) / 51개 (102) | `201` / `400` 길이 |
| 14 | description | 누락 / `null` / `""` / `"  \n "` | `201`, `description: null` |
| 15 | description | `"1행\n\t2행\r\n3행"` | `201`, 내부 그대로 |
| 16 | description | `"설명\u0000"`, `"설명\u0007"` | `400` 허용 문자 |
| 17 | description | 정규화 후 1000 / 1001 code unit | `201` / `400` 길이 |

#### 5.5.6 Frontend 검증

- `features/projects/validation.ts`가 5.5.1~5.5.4와 같은 규칙을 JavaScript 정규식(`u` 플래그, `\p{Cc}` 등 Unicode property)으로 구현한다.
- 5.5.5 표의 케이스로 테스트한다.
- **서버 검증이 최종 기준이다.** JavaScript `trim()`·`\s`는 U+FEFF를 공백으로 보는 등 Java와 다르므로 사용하지 않고, 위 문자 범주를 명시적으로 사용한다.

#### 5.5.7 애플리케이션 규칙과 DB `btrim()`의 차이

| 구분 | 공백 기준 | 역할 |
| --- | --- | --- |
| 애플리케이션 (5.5.1~5.5.4) | `isWhitespace` ∪ `isSpaceChar` + 허용 문자 규칙 | **허용·거부의 기준.** `400`과 필드 오류 반환 |
| DB `CHECK (length(btrim(name)) > 0)` | `btrim(text)` 기본값은 **ASCII 스페이스(U+0020)만** 제거 | 애플리케이션을 거치지 않는 직접 INSERT에 대한 최소 방어선 |

- 애플리케이션 규칙이 DB 규칙보다 넓으므로, 애플리케이션을 통과한 이름은 항상 DB `CHECK`도 통과한다.
- DB `CHECK`만으로는 탭·NBSP만 있는 이름을 막지 못한다. 이는 의도된 한계이며, 직접 INSERT는 애플리케이션이 지원하는 경로가 아니다.

## 6. Backend API 명세

### 6.1 공통 [기존 재사용]

- Base path: `/api/projects`
- 요청·응답: `application/json; charset=UTF-8`
- 오류 응답: `ErrorResponse`(`code`, `message`, `fieldErrors?`). 스택 트레이스·SQL·내부 경로는 포함하지 않는다.
- 인증: SPEC-AUTH 인가 규칙에 따라 `/api/**`는 `authenticated`이다. `SecurityConfig`는 변경하지 않는다.
- CSRF: `POST`는 기존 `csrf.spa()` 정책에 따라 `X-XSRF-TOKEN` 헤더가 필요하다.
- 날짜: Spring Boot 4.1.1은 `WRITE_DATES_AS_TIMESTAMPS`를 비활성화한다(소스 확인). 따라서 `Instant`는 ISO-8601 문자열로 직렬화된다. 구현 시 테스트로 고정한다.

### 6.2 엔드포인트 [제안]

| Method / Path | 인증 | CSRF | 요청 | 성공 | 실패 |
| --- | --- | --- | --- | --- | --- |
| `POST /api/projects` | 필요 | **필요** | `CreateProjectRequest` | `201 Created`, `Location: /api/projects/{id}`, `ProjectResponse` | `400 VALIDATION_FAILED`, `401 AUTH_REQUIRED`, `403 CSRF_INVALID`, `415 UNSUPPORTED_MEDIA_TYPE`, `500 INTERNAL_ERROR` |
| `GET /api/projects` | 필요 | 불필요 | — | `200`, `ProjectSummaryResponse[]` (`created_at DESC, id DESC`, 없으면 `[]`) | `401 AUTH_REQUIRED` |
| `GET /api/projects/{projectId}` | 필요 | 불필요 | — | `200`, `ProjectResponse` | `400 BAD_REQUEST` (숫자가 아닌 ID, [결정 Q14]), `401 AUTH_REQUIRED`, `404 PROJECT_NOT_FOUND` |

### 6.3 요청·응답 본문

```text
CreateProjectRequest (record)
  name         string   필수, 허용 문자, 1~100 (UTF-16)  — 정규화 후 검사 (§5.5)
  description  string?  허용 문자, 0~1000 (UTF-16)     — 정규화 후 검사, 공백·빈 값은 null (§5.5)
  * ownerId 등 정의되지 않은 필드는 무시된다 ([결정 Q8], §7.2)

ProjectResponse (record)
  id           number
  name         string
  description  string | null
  owner        { id: number, displayName: string }   — 이메일 미포함
  createdAt    string (ISO-8601)
  updatedAt    string (ISO-8601)

ProjectSummaryResponse (record)
  id           number
  name         string
  createdAt    string (ISO-8601)
```

- 목록 정렬은 **`created_at DESC, id DESC`**다. `created_at`이 같으면 `id`가 큰(나중에 생성된) 프로젝트가 먼저 온다.
- 페이지네이션 없이 전체를 반환한다([결정 Q6]).

### 6.4 오류 코드

| code | HTTP | 상황 | 비고 |
| --- | --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Bean Validation 실패(필수·허용 문자·길이, `fieldErrors` 포함), 잘못된 JSON·본문 누락·타입 불일치(`fieldErrors` 없음) | [기존] |
| `BAD_REQUEST` | 400 | `projectId` 형식 오류 | [기존] 코드. 현재 구현은 500을 반환하므로 구현 단계에서 전용 처리 추가 ([결정 Q14], §6.5) |
| `AUTH_REQUIRED` | 401 | 세션 없음·만료 | [기존] |
| `CSRF_INVALID` | 403 | CSRF 토큰 없음·불일치 | [기존] |
| `PROJECT_NOT_FOUND` | 404 | 프로젝트 없음 **또는** 접근 권한 없음 (같은 본문) | **[결정 Q11] 신규** |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | `Content-Type`이 JSON이 아님 | [기존] |
| `INTERNAL_ERROR` | 500 | DB 저장 실패 등 | [기존] |

프로젝트 기능에서 `403 ACCESS_DENIED`는 사용하지 않는다(§7.4).

### 6.5 오류 응답 계약 검증 (현재 `main` 구현 기준)

`main`(`7e22a8a`)의 `GlobalExceptionHandler`, `ErrorCode`, `ErrorResponse` 코드와 Spring Framework 7.0.9 소스로 확인한 예상 동작이다. 테스트로 실행한 결과가 아니며, 구현 단계의 통합 테스트로 확정한다.

| 상황 | 발생 예외 | 현재 처리 | 현재 응답 | SPEC 요구 | 일치 |
| --- | --- | --- | --- | --- | --- |
| 잘못된 JSON / 본문 누락 / 필드 타입 불일치(객체·배열) | `HttpMessageNotReadableException` | 전용 `@ExceptionHandler` | `400 VALIDATION_FAILED`, `fieldErrors` 없음 | 동일 | ✅ |
| `name` 누락·`null`·빈 문자열·공백 | (정규화 후) `MethodArgumentNotValidException` | 전용 `@ExceptionHandler` | `400 VALIDATION_FAILED`, `fieldErrors[{field:"name", message}]`, 거부된 값 미포함 | 동일 | ✅ |
| 길이 초과·허용되지 않는 문자 등 Bean Validation 실패 | `MethodArgumentNotValidException` | 위와 동일 | 위와 동일 | 동일 | ✅ |
| `Content-Type`이 JSON이 아님 | `HttpMediaTypeNotSupportedException` | 전용 `@ExceptionHandler` | `415 UNSUPPORTED_MEDIA_TYPE` | 동일 | ✅ |
| **숫자가 아닌 `projectId`** (`/api/projects/abc`, `Long` 범위 초과) | `MethodArgumentTypeMismatchException` | **catch-all `Exception` 처리** | **`500 INTERNAL_ERROR` + ERROR 로그** | `400 BAD_REQUEST` | ❌ |

불일치 원인 (소스 확인):
- `MethodArgumentTypeMismatchException` → `TypeMismatchException` → `PropertyAccessException` → `BeansException` 상속 구조이며, `org.springframework.web.ErrorResponse`를 구현하지 않는다.
- 그래서 `GlobalExceptionHandler`의 "Spring MVC 4xx는 상태 유지" 분기에 걸리지 않고 500으로 처리된다.

결정 ([결정 Q14]):

- 구현 단계에서 `GlobalExceptionHandler`에 **`MethodArgumentTypeMismatchException` 전용 `@ExceptionHandler`**를 추가해 `400 BAD_REQUEST`(`ErrorResponse.of(ErrorCode.BAD_REQUEST)`)를 반환한다.
- 기존 공통 오류 응답 형식(`code`, `message`)을 유지하고, 클라이언트 입력 오류이므로 ERROR 로그를 남기지 않는다.
- 다른 예외 처리와 catch-all 동작은 변경하지 않는다.
- 이 변경은 SPEC-01 구현 범위에 포함되는 공통 코드(`global/error`) 변경이다.
- **회귀 확인:** 기존 SPEC-AUTH 테스트(`AuthApiIntegrationTest`, `CsrfCookieFlowIntegrationTest`, `ActuatorExposureTest`, `RestAccessDeniedHandlerTest` 등)가 변경 없이 통과해야 한다. 현재 인증 API에는 경로 변수가 없으므로 동작 변화는 예상되지 않는다.

## 7. 인증 및 인가 정책

### 7.1 현재 사용자 식별 [기존 재사용]

- Controller는 `@AuthenticationPrincipal AuthenticatedUser`에서 `getId()`만 읽어 Service에 전달한다.

### 7.2 Owner 위조 방지 [결정 Q8]

- `CreateProjectRequest`에는 `ownerId` 필드가 없다.
- Spring Boot 4.1.1은 `FAIL_ON_UNKNOWN_PROPERTIES`를 비활성화한다(소스 확인). 따라서 본문에 `ownerId`나 그 밖의 정의되지 않은 필드가 있어도 **무시**되고 요청은 `201`로 성공한다.
- Owner는 항상 Service가 세션 사용자 ID로 조회한 `User`이다. 테스트로 보장한다(AC-PROJECT-03).

### 7.3 소유권 검증 위치 [제안]

- 위치는 `ProjectService`다. Controller와 Repository에는 권한 판단을 두지 않는다([기존] `CLAUDE.md` §3-8, §3-10).
- **접근 확인 진입점:** `getAccessibleProject(projectId, userId)`
  - SPEC-01: `projectRepository.findByIdAndOwnerId(projectId, userId)`. 결과가 없으면 `PROJECT_NOT_FOUND`.
- **목록:** `findAllByOwnerIdOrderByCreatedAtDescIdDesc(userId)` (`created_at DESC, id DESC`).

### 7.4 401 / 403 / 404 구분

| 상황 | 응답 | 근거 |
| --- | --- | --- |
| 세션 없음·만료 | `401 AUTH_REQUIRED` | [기존] SPEC-AUTH |
| CSRF 실패 | `403 CSRF_INVALID` | [기존] SPEC-AUTH |
| 존재하지 않는 프로젝트 | `404 PROJECT_NOT_FOUND` | [결정 Q11] |
| 존재하지만 접근 권한이 없는 프로젝트 | `404 PROJECT_NOT_FOUND` (**같은 본문**) | [제안] IDOR·존재 노출 방지 |

- **IDOR 방지:**
  1. 조회 조건에 `owner_id`를 포함해 DB에서부터 걸러 낸다.
  2. 권한 없음과 미존재를 같은 응답으로 처리한다.
  3. 두 경우 모두 같은 단일 쿼리 경로를 사용해 응답 시간 차이를 줄인다.
- ID는 순차 BIGINT이지만 위 방식으로 존재 여부와 내용은 노출되지 않는다. UUID 공개 ID는 도입하지 않는다([기존] ADR-0005).

### 7.5 CSRF [기존 재사용]

- `POST /api/projects`는 기존 CSRF 보호 대상이다. Frontend는 기존 `lib/api-client.ts`를 그대로 사용한다.

### 7.6 비활성 계정 [결정 Q9]

- SPEC-AUTH R6 한계를 유지한다. 로그인 중 비활성화된 계정은 세션이 만료될 때까지 프로젝트를 생성·조회할 수 있다.

### 7.7 트랜잭션, flush 시점, 동시 요청 [제안]

#### 생성 트랜잭션

- `ProjectService.create`는 `@Transactional` 메서드 하나에서 다음을 수행한다. 실패하면 전체 롤백된다.
  1. Owner 참조 획득: `userRepository.getReferenceById(sessionUserId)` (추가 SELECT 없음)
  2. `Project.create`
  3. `projectRepository.save`
- **Owner 존재 보장:**
  - Owner 존재는 사전 조회가 아니라 DB FK(`owner_id → users.id`)로 보장한다.
  - 세션은 유효하지만 사용자 행이 없는 비정상 상태(사용자 삭제 기능은 없음)에서는 FK 위반으로 `500`이 된다.
  - 응답 DTO의 `owner.displayName`은 저장 후 같은 트랜잭션 안에서 참조를 초기화해 읽는다.
- **flush 시점:**
  - `GenerationType.IDENTITY`는 ID를 DB가 생성하므로, Hibernate가 `save()`(=`persist`) 시점에 즉시 INSERT를 실행해 생성 키를 받는다.
  - 따라서 `NOT NULL`·`CHECK`·FK 위반은 **`save()` 호출 중**에 발생한다.
  - 현재 스키마(V1~V3)에는 지연(DEFERRABLE) 제약이 없으므로 **커밋 시점에만 드러나는 DB 오류는 없다.**
- **예외 경로:**
  - `save()` 중 DB 오류 → Spring Data 예외 변환 → `DataIntegrityViolationException` 등 `DataAccessException` 계열 → `GlobalExceptionHandler` catch-all → `500 INTERNAL_ERROR`.
  - 응답 본문은 `code`·`message`뿐이며, 예외 상세(SQL, 제약 이름)는 서버 로그에만 남는다([기존]).
- **원자성 검증 범위:**
  - SPEC-01의 생성은 **단일 INSERT**다. 따라서 "여러 변경 중 일부만 남는" 부분 커밋 위험은 없고, 검증 목표는 "실패한 INSERT가 행을 남기지 않는다"이다.
  - 여러 변경의 원자성 검증(예: `spec.md` §10 SPEC-06 "Review 생성과 Document 상태 변경 중 하나라도 실패하면 전체 미저장")은 해당 SPEC에서 같은 방식(§10.2)으로 수행한다.
- **조회:** `@Transactional(readOnly = true)`

#### 동시 요청 [결정 Q5]

- 같은 사용자의 동시 생성 요청은 서로 독립적으로 성공한다. 이름 유니크 제약이 없으므로 충돌하지 않는다.
- 이중 제출은 Frontend의 버튼 비활성화로 줄인다. 서버 멱등성 키는 도입하지 않는다(§12 R3).

### 7.8 권한 모델 확장 원칙 [제안 — 이번 SPEC은 원칙만 기록]

SPEC-02 이후에는 "프로젝트에 접근할 수 있는가"와 "특정 작업을 할 수 있는가"가 달라진다. 이번 SPEC에서는 아래 1행만 구현하고, 나머지는 해당 SPEC에서 결정한다.

| 권한 범주 | SPEC-01 | 결정할 SPEC | 비고 |
| --- | --- | --- | --- |
| **프로젝트 조회·접근** | Owner만 (`getAccessibleProject`) | SPEC-02: Owner + 구성원으로 확장 | 실패 시 `404 PROJECT_NOT_FOUND` |
| 프로젝트 구성원 관리 | 없음 | SPEC-02 | Owner 전용 여부는 미결정 |
| 문서 등록·수정 | 없음 | SPEC-03/04 | `spec.md` §16.2 Role 모델 결정에 의존 |
| 검토·승인 | 없음 | SPEC-06, SPEC-10~12 | [기존] `spec.md` §10: Reviewer는 프로젝트 구성원. Review 단위 역할 여부는 §16.2 미결정 |

**원칙**

1. **2단계 검사:**
   - 모든 프로젝트 하위 기능은 먼저 `getAccessibleProject(projectId, userId)`로 접근 가능 여부를 확인한다. 실패하면 `404`.
   - 그다음 작업별 권한을 확인한다. 실패하면 `403 ACCESS_DENIED`.
   - 접근할 수 없는 사용자에게는 존재 여부를, 접근 가능한 사용자에게는 작업 거부 사유를 구분해 전달하기 위함이다.
2. **한 곳에서 판단:** 작업별 권한 판단은 Service 계층의 전용 컴포넌트에 모은다(예: SPEC-02에서 `ProjectPermissionService` 검토). Controller·Repository·Frontend에 권한 규칙을 흩어 두지 않는다.
3. **역할은 서버 데이터로만 판단:** 클라이언트가 보낸 역할·Owner 값은 사용하지 않는다.
4. **조회 범위와 접근 판단의 일치:** 목록 조회 조건과 `getAccessibleProject` 조건은 같은 규칙으로 유지한다. 목록에 보이는데 상세에서 404가 나는 불일치를 막기 위함이다.
5. **Frontend의 권한 표시는 보조 수단:** 버튼 숨김 등은 사용성 목적이며, 최종 판단은 Backend가 한다([기존] SPEC-AUTH 원칙).
6. **현 단계 범위:** 메서드 보안 어노테이션(`@PreAuthorize`) 도입이나 권한 프레임워크 추가는 이번 SPEC에서 하지 않는다. 필요성이 확인되면 해당 SPEC에서 대안과 함께 제안한다.

## 8. Frontend 화면 및 상태 흐름

### 8.1 라우트 [결정 Q10]

| 경로 | 화면 | 보호 |
| --- | --- | --- |
| `/projects` | 내 프로젝트 목록 + "새 프로젝트" 버튼 | `RequireAuth` |
| `/projects/new` | 프로젝트 생성 폼 | `RequireAuth` |
| `/projects/[projectId]` | 프로젝트 상세 (이름, 설명, Owner 표시 이름, 생성일) | `Suspense` → `RequireAuth` |
| `/` | 기존 화면 유지 + "내 프로젝트" 링크 추가 | 기존 |

- 정적 세그먼트 `new`는 동적 세그먼트 `[projectId]`보다 우선 매칭된다(Next.js 라우팅 규칙).

### 8.2 구조 (기존 features 구조 재사용)

```text
frontend/src
├── app/projects/page.tsx                 # RequireAuth + ProjectList
├── app/projects/new/page.tsx             # RequireAuth + ProjectCreateForm
├── app/projects/[projectId]/page.tsx     # Suspense + RequireAuth + ProjectDetail
└── features/projects/
    ├── api.ts                            # createProject, fetchProjects, fetchProject (lib/api-client 사용)
    ├── types.ts                          # Project, ProjectSummary
    ├── validation.ts                     # §5.5와 같은 클라이언트 검증
    └── components/ProjectList.tsx, ProjectCreateForm.tsx, ProjectDetail.tsx (+ *.test.tsx)
```

- **재사용:** 기존 `lib/api-client.ts`, `features/auth`의 `RequireAuth`·`useAuth`를 그대로 쓴다. 새 상태 관리·데이터 페칭·UI 라이브러리는 추가하지 않는다. 기존 `useEffect` + `fetch` 패턴으로 충분하다.
- **Next.js 16 동적 라우트 요구사항** (실제 프로젝트 설정과 번들 문서로 확인):
  - `frontend/next.config.ts`는 `cacheComponents: true`, Next.js 버전은 16.4.0이다.
  - 번들 문서 `use-params.md`, `use-pathname.md`에 따르면, `cacheComponents` 사용 시 `generateStaticParams`로 정해지지 않은 동적 세그먼트에서는 `useParams`와 **`usePathname`도** 요청 시점까지 값을 알 수 없어 suspend된다. `Suspense` 경계가 없으면 빌드가 실패한다.
  - 기존 `RequireAuth`가 `usePathname`을 사용하므로, `/projects/[projectId]`에서는 **`RequireAuth`를 포함한 하위 트리 전체**를 `Suspense`로 감싼다. `ProjectDetail`은 `useParams`로 `projectId`를 읽는다.
  - `/projects`, `/projects/new`는 정적 경로라 이 요구사항이 적용되지 않는다.
  - 구현 시 `npm run build`로 확인한다.

### 8.3 화면 상태

| 화면 | Loading | Empty | Error | Success |
| --- | --- | --- | --- | --- |
| 목록 | "프로젝트를 불러오는 중..." | "아직 프로젝트가 없습니다." + 생성 버튼 | "프로젝트를 불러오지 못했습니다." + 다시 시도 | 이름·생성일 목록 (서버 순서 `created_at DESC, id DESC` 그대로), 상세 링크 |
| 생성 | 제출 중 버튼 비활성화 | — | 필드 오류(클라이언트 + 서버 `fieldErrors`), 서버·네트워크 오류 문구 | `/projects/{id}`로 이동 (`router.push`) |
| 상세 | "불러오는 중..." | — | `404`·`400`: "프로젝트를 찾을 수 없습니다." + 목록 링크 / 그 외: 오류 + 다시 시도 | 상세 표시 |

- `spec.md` §15의 Permission Error 상태는 이 SPEC에서 404 화면으로 대체된다(§7.4).
- 자동 재시도는 [기존] `api-client` 정책(`CSRF_INVALID` 1회)만 따른다.

### 8.4 세션 만료 연동 [제안]

- 프로젝트 API가 `401`을 반환하면 해당 컴포넌트가 `useAuth().refresh()`를 호출한다.
- [기존] `AuthProvider`는 "authenticated였는데 `/me`가 401"이면 `sessionExpired=true`, `anonymous`로 바꾼다.
- 이어서 `RequireAuth`가 `/login?next=...&reason=expired`로 이동한다.
- `AuthProvider` 변경은 필요 없을 것으로 예상하며, 구현 시 확인한다.

## 9. 예외 및 오류 처리

| 시나리오 | Backend | Frontend |
| --- | --- | --- |
| 미로그인 사용자의 생성 요청 | `401 AUTH_REQUIRED` | 로그인 화면 |
| 미로그인 사용자의 목록·상세 조회 | `401 AUTH_REQUIRED` | 로그인 화면 |
| 다른 사용자의 프로젝트 상세 조회 | `404 PROJECT_NOT_FOUND` (미존재와 같은 본문) | "프로젝트를 찾을 수 없습니다." |
| 본문에 `ownerId` 위조 | 무시, Owner = 세션 사용자, `201` | — |
| `name` 누락·`null`·`""`·공백만 | `400 VALIDATION_FAILED`, `fieldErrors[name]` | 필드 오류 (제출 전 클라이언트 검증) |
| 정규화 후 `name` 101자 이상 / `description` 1001자 이상 | `400 VALIDATION_FAILED` | 필드 오류 |
| 이름에 제어 문자·줄바꿈·탭·폭 없는 문자·양방향 서식 문자 포함, 또는 보이는 문자 없음 / 설명에 `\t`·`\n`·`\r` 외 제어 문자 포함 | `400 VALIDATION_FAILED`, 해당 `fieldErrors` ([결정 Q13], §5.5.3) | 필드 오류 (제출 전 클라이언트 검증) |
| 잘못된 JSON·본문 누락 | `400 VALIDATION_FAILED` (`fieldErrors` 없음) | 일반 오류 문구 |
| JSON이 아닌 `Content-Type` | `415 UNSUPPORTED_MEDIA_TYPE` | 일반 오류 문구 (정상 화면에서는 발생하지 않음) |
| 존재하지 않는 프로젝트 | `404 PROJECT_NOT_FOUND` | 404 화면 |
| 숫자가 아닌 `projectId` | `400 BAD_REQUEST` ([결정 Q14], 구현 시 전용 처리 추가) | "프로젝트를 찾을 수 없습니다." |
| CSRF 토큰 없는 생성 요청 | `403 CSRF_INVALID` | [기존] 토큰 갱신 후 1회 재시도 |
| 같은 사용자의 동시 생성 요청 | 각각 `201` (별도 프로젝트) | 제출 중 버튼 비활성화 |
| DB 저장 실패 (`save()` 중 제약 위반, 예: 세션 사용자의 행이 없어 FK 위반) | `500 INTERNAL_ERROR`, 롤백, 상세는 로그에만 | "서버 오류가 발생했습니다." (자동 재시도 없음) |
| 세션 만료 후 요청 | `401 AUTH_REQUIRED` | 만료 안내 후 로그인 화면 |
| Backend 중단 | 연결 실패 | Network Error + 다시 시도 |

## 10. 테스트 전략

### 10.1 Backend 단위 테스트

| 테스트 | 목적 |
| --- | --- |
| `ProjectText`·`CreateProjectRequest` 정규화: `null` 안전, 가장자리 공백(NBSP·U+3000 포함) 제거, 내부 보존, 설명 빈 값 → `null` | 정규화 규칙 (§5.5.1) |
| `CreateProjectRequest` Bean Validation: §5.5.5 표 #1~#17 전부 | 필수·허용 문자·길이(UTF-16, 정규화 후) 규칙 고정 |
| `Project.create`, `Project.isOwnedBy` | 도메인 규칙 |

### 10.2 Backend 통합 테스트

`@SpringBootTest` + MockMvc + 실제 PostgreSQL을 사용한다([기존] CI 재사용). 인증은 실제 로그인 세션 또는 `AuthenticatedUser`를 사용하며, 보안 설정을 끄지 않는다. **트랜잭션을 검증하는 테스트 클래스에는 `@Transactional`을 붙이지 않는다**(테스트 트랜잭션이 커밋·롤백 동작을 가리기 때문). 데이터는 `JdbcTemplate`으로 정리한다.

| 테스트 | 목적 |
| --- | --- |
| 생성 → `201`, `Location`, 응답 Owner = 세션 사용자, DB `owner_id` 확인 | 정상 흐름 |
| 본문 `ownerId`가 다른 사용자여도 Owner = 세션 사용자, `201` | Owner 위조 방지 [결정 Q8] |
| 미인증 `POST`/목록/상세 → `401` JSON, `Location` 없음 | 인증 재사용 |
| CSRF 없는 `POST` → `403 CSRF_INVALID`, 행 수 불변 | CSRF 재사용 |
| `name` 누락·`null`·`""`·공백 → `400`, `fieldErrors[0].field = "name"`, 행 수 불변 | 입력 검증 |
| 정규화 후 길이 초과 → `400`. 앞뒤 공백 포함 103자(정규화 후 100자) → `201` | 정규화 후 길이 검사 |
| 잘못된 JSON·본문 누락 → `400 VALIDATION_FAILED`, `fieldErrors` 없음 | 오류 계약 (§6.5) |
| 숫자가 아닌 `projectId`·`Long` 범위 초과 → `400 BAD_REQUEST`, 공통 오류 형식, ERROR 로그 없음 | 오류 계약 [결정 Q14] |
| 허용되지 않는 문자(§5.5.5 #6~#9, #16) → `400`, 해당 `fieldErrors`, 행 수 불변 | 문자 정책 [결정 Q13] |
| 목록: 본인 것만, `[]`, 정렬 `created_at DESC, id DESC` (같은 `created_at` 2건은 `id DESC`) | 조회 범위·정렬 |
| 상세: 본인 `200`, 타인 `404`, 미존재 `404`, **두 404 응답 본문 동일** | IDOR·존재 노출 방지 |
| 응답 `owner`에 `id`, `displayName`만. 날짜는 ISO-8601 문자열 | 정보 노출·직렬화 |
| **DB 저장 실패:** 아래 "저장 실패 재현과 롤백 검증" 참고 | 트랜잭션·오류 처리 |
| 같은 사용자의 동시 생성: `ProjectService.create`를 두 스레드에서 `CountDownLatch`로 동시에 호출 → 둘 다 성공, `id` 상이, 행 2건 | 동시성 |
| Flyway V3 적용(`ddl-auto=validate` 기동), `JdbcTemplate` 직접 INSERT로 `' '` 이름 → `CHECK` 위반, 존재하지 않는 `owner_id` → FK 위반 | 스키마 제약 |
| 기존 SPEC-AUTH 테스트 전체 (`GlobalExceptionHandler` 변경 후 포함) | 회귀 방지 |

#### 저장 실패 재현과 롤백 검증 (AC-PROJECT-15)

**원칙:**
- Repository·Service를 Mock으로 바꿔 예외를 던지지 않는다. 실제 INSERT·flush·롤백이 일어나지 않아 검증이 약해지기 때문이다.
- 테스트 전용 트리거나 별도 인프라도 추가하지 않는다. **기존 스키마 제약**으로 실제 PostgreSQL 오류를 일으킨다.

**검토 결과:** 정상 입력 경로에서는 애플리케이션 검증(§5.5)이 DB 제약보다 넓다. 따라서 `CHECK`·`VARCHAR` 위반을 API로 일으킬 수 없다. 반면 **FK(`owner_id → users.id`)** 는 기존 테스트 인프라만으로 위반시킬 수 있다.

1. **재현 (단일 INSERT 실패, save 시점):**
   - 테스트용 사용자를 만들고 로그인 세션을 얻는다.
   - `JdbcTemplate`으로 그 사용자 행을 삭제한다(`ON DELETE RESTRICT`이지만 프로젝트가 없으므로 삭제 가능).
   - 세션은 메모리에 남아 있으므로 `POST /api/projects`는 인증을 통과한다.
   - `getReferenceById`로 얻은 Owner 참조로 INSERT가 `save()` 시점에 실행되고, PostgreSQL이 FK 위반을 반환한다.
2. **응답 검증:**
   - `500`, `code = INTERNAL_ERROR`, 공통 메시지인지 확인한다.
   - 본문 문자열에 `SQL`, `ERROR:`, `fk`·`constraint`·`violates`, 테이블명(`projects`, `users`), 예외 클래스명(`DataIntegrityViolationException` 등)이 **없는지** 확인한다.
3. **DB 상태 검증 (롤백):**
   - 테스트 클래스에 `@Transactional`을 붙이지 않는다. 그래서 요청은 실제로 커밋·롤백된다.
   - 요청 전후에 `JdbcTemplate`으로 `SELECT count(*) FROM projects`를 실행해 행 수가 같음을 확인한다. 이 조회는 요청 트랜잭션과 다른 커넥션의 커밋된 상태다.
4. **flush·commit 시점:**
   - IDENTITY 전략이라 실패는 `save()`에서 즉시 발생한다(§7.7).
   - 지연 제약이 없으므로 커밋 시점 실패 테스트는 두지 않는다. 지연 제약이 도입되는 SPEC에서 추가한다.
5. **정리:** `@AfterEach`에서 `JdbcTemplate`으로 테스트 데이터를 삭제한다.

**여러 변경의 원자성:** SPEC-01은 단일 INSERT라 해당 없음(§7.7). 이후 SPEC에서도 같은 원칙(실제 제약 위반 + 비트랜잭션 테스트 + 커밋된 DB 상태 조회)을 우선 적용한다. 기존 제약으로 재현할 수 없을 때만 대안을 검토한다.

### 10.3 Frontend 테스트 (Vitest)

| 테스트 | 목적 |
| --- | --- |
| `ProjectList`: loading / empty / error(다시 시도) / success (서버 순서 유지) | 상태 구분 |
| `validation.ts`: §5.5.5 표 #1~#17 (Java 규칙과 동일 결과) | 클라이언트 검증 규칙 |
| `ProjectCreateForm`: 클라이언트 검증 오류 표시, 제출 중 비활성화, 서버 `fieldErrors` 표시, 성공 시 `router.push`, 5xx·네트워크 오류 문구 | 생성 흐름 |
| `ProjectDetail`: success / 404·400 → "찾을 수 없습니다" / 기타 오류 | 상세 |
| 프로젝트 API `401` → `useAuth().refresh()` 호출 | 세션 만료 연동 |
| `features/projects/api.ts` 경로·메서드·본문 | API 계약 |
| `npm run build` 성공 (`/projects/[projectId]`의 `Suspense` 요구사항) | Next.js 16 빌드 제약 |
| 기존 39건 유지 | 회귀 방지 |

### 10.4 수동 검증 (Windows 로컬, 사용자)

1. Author 로그인 → 프로젝트 생성 → 상세로 이동 → 목록 맨 위에 표시
2. Reviewer 로그인 → 목록에 Author 프로젝트 없음 → Author 프로젝트 URL 직접 입력 시 "찾을 수 없습니다"
3. 생성 요청의 `X-XSRF-TOKEN` 헤더와 `201` 응답 확인
4. 짧은 세션 timeout으로 만료 후 생성 시도 → 로그인 화면으로 이동

### 10.5 검증 환경과 주체

| 검증 | 주체 |
| --- | --- |
| Backend 단위·통합 (JDK 25) | GitHub Actions CI (클라우드에는 JDK 25 없음) |
| Frontend lint·test·build | Claude Code 클라우드, CI |
| 수동 E2E (§10.4) | 사용자 (Windows 로컬) |

## 11. Acceptance Criteria

| ID | 요구사항 | 기대 결과 | 검증 방법 |
| --- | --- | --- | --- |
| **AC-PROJECT-01** | 인증된 사용자가 유효한 이름(설명 선택)과 CSRF 토큰으로 프로젝트를 생성한다 | `201`, `Location: /api/projects/{id}`, 본문에 `id`·`name`·`description`·`owner`·`createdAt`·`updatedAt` | 통합 테스트 |
| **AC-PROJECT-02** | 생성된 프로젝트의 Owner는 세션 사용자다 | DB `projects.owner_id` = 로그인 사용자 `users.id`, 응답 `owner.id` 동일 | 통합 테스트 |
| **AC-PROJECT-03** | 본문에 다른 사용자의 `ownerId`를 넣어도 Owner를 지정할 수 없다 [결정 Q8] | `201`, `ownerId`는 무시되고 DB·응답의 Owner = 세션 사용자 | 통합 테스트 |
| **AC-PROJECT-04** | 이름·설명은 앞뒤 공백 제거 후 저장되고, 설명이 누락·`null`·빈 값·공백만이면 `null`로 저장된다. 정규화는 `null` 입력에서도 예외를 내지 않는다 | 저장값·응답값이 §5.5 표와 일치 | 단위 + 통합 테스트 |
| **AC-PROJECT-05** | `name`이 누락·`null`·`""`·공백 문자만(스페이스·탭·줄바꿈·NBSP·`U+3000`)이거나 §5.5.3의 거부 문자를 포함하거나 보이는 문자가 없으면 거부된다. `description`은 `\t`·`\n`·`\r` 외 제어 문자(`U+0000` 포함)를 거부한다 | §5.5.5 #1, #2, #6~#9, #16 모두 `400 VALIDATION_FAILED`, 해당 `fieldErrors`, 응답에 입력값 미포함, DB 행 수 불변. #3~#5, #10, #15는 `201` | 단위 + 통합 테스트 |
| **AC-PROJECT-06** | 길이 제한은 정규화 이후 값의 UTF-16 code unit 수로 검사한다 (이름 100, 설명 1000) [결정 Q15] | §5.5.5 #11~#13, #17: 100 → `201`, 101 → `400`, 앞뒤 공백 포함 103(정규화 후 100) → `201`, 이모지 50개 → `201`·51개 → `400`, 설명 1000 → `201`·1001 → `400` | 단위 + 통합 테스트 |
| **AC-PROJECT-07** | 세션 없이 생성·목록·상세를 요청하면 거부된다 | 각각 `401 AUTH_REQUIRED` JSON, `Location` 헤더 없음 | 통합 테스트 |
| **AC-PROJECT-08** | CSRF 토큰 없이 생성하면 거부된다 | `403 CSRF_INVALID`, DB 행 수 불변 | 통합 테스트 |
| **AC-PROJECT-09** | 목록에는 자신이 Owner인 프로젝트만 `created_at DESC, id DESC` 순서로 반환된다 | 다른 사용자 프로젝트 미포함. `created_at`이 다른 3건은 최신순. `created_at`이 같은 2건(테스트에서 `JdbcTemplate`으로 동일 시각 설정)은 `id`가 큰 쪽이 먼저 | 통합 테스트 |
| **AC-PROJECT-10** | 프로젝트가 없으면 빈 배열을 반환한다 | `200`, `[]` | 통합 테스트 |
| **AC-PROJECT-11** | Owner는 자신의 프로젝트 상세를 조회할 수 있다 | `200`, `ProjectResponse` | 통합 테스트 |
| **AC-PROJECT-12** | 다른 사용자의 프로젝트 상세 조회는 존재하지 않는 프로젝트 조회와 구별되지 않는다 | 둘 다 `404 PROJECT_NOT_FOUND`, 응답 본문 문자열 동일 | 통합 테스트 |
| **AC-PROJECT-13** | 숫자가 아니거나 `Long` 범위를 넘는 프로젝트 ID는 클라이언트 오류로 처리되며, 이 변경이 기존 인증 API에 영향을 주지 않는다 [결정 Q14] | `/api/projects/abc` → `400 BAD_REQUEST`, 공통 오류 형식, ERROR 로그 없음. 기존 SPEC-AUTH 테스트 전부 통과 | 통합 테스트 + 회귀 테스트 |
| **AC-PROJECT-14** | 응답에 Owner 이메일·비밀번호 해시 등 불필요한 사용자 정보가 포함되지 않는다 | `owner`에는 `id`, `displayName`만 존재 | 통합 테스트 |
| **AC-PROJECT-15** | DB 저장 실패 시 변경이 남지 않고 내부 정보가 노출되지 않는다 | 기존 FK 제약 위반(세션 사용자의 행 삭제 후 생성)으로 실제 PostgreSQL 오류를 `save()` 시점에 재현 → `500 INTERNAL_ERROR`. 비트랜잭션 테스트에서 별도 커넥션 조회로 `projects` 행 수 불변 확인. 응답에 SQL·`ERROR:`·제약명·테이블명·예외 클래스명 없음. Repository·Service Mock과 테스트 전용 트리거 미사용 | 통합 테스트 (§10.2) |
| **AC-PROJECT-16** | 같은 사용자의 동시 생성 요청 2건은 모두 처리된다 | 두 요청 모두 성공, 서로 다른 `id`, 행 2건 | 통합 테스트 |
| **AC-PROJECT-17** | `projects` 테이블이 Flyway V3로 생성되고 제약이 적용된다 | `ddl-auto=validate` 기동 성공. 직접 INSERT 시 `' '` 이름은 `CHECK` 위반, 존재하지 않는 `owner_id`는 FK 위반 | 통합 테스트 |
| **AC-PROJECT-18** | 목록 화면은 로딩·빈 목록·오류·성공 상태를 구분한다 | 상태별 문구·버튼 표시, 오류 시 다시 시도, 서버 순서 유지 | Vitest |
| **AC-PROJECT-19** | 생성 화면은 §5.5와 같은 클라이언트 검증을 수행하고 제출 중 중복 제출을 막는다 | §5.5.5 케이스에서 서버와 같은 허용·거부 결과, 잘못된 입력은 요청 없이 필드 오류, 제출 중 버튼 비활성화 | Vitest |
| **AC-PROJECT-20** | 생성 성공 시 상세 화면으로 이동하고, 서버 검증 오류는 해당 필드에 표시된다 | `router.push('/projects/{id}')`, `fieldErrors` 표시 | Vitest |
| **AC-PROJECT-21** | 상세 화면은 `404`(및 `400`)를 "프로젝트를 찾을 수 없습니다."로 표시하며, 권한 없음과 구별하지 않는다 | 문구 + 목록 링크 | Vitest |
| **AC-PROJECT-22** | 프로젝트 API가 `401`을 반환하면 기존 세션 만료 흐름으로 로그인 화면에 이동한다 | `useAuth().refresh()` 호출 → `/login?next=...&reason=expired` | Vitest + 수동 |
| **AC-PROJECT-23** | 미인증 사용자가 `/projects`, `/projects/new`, `/projects/{id}`에 접근하면 로그인 화면으로 이동하고, `/projects/[projectId]`는 `Suspense` 경계와 함께 빌드된다 | `/login?next=...`, `npm run build` 성공 | Vitest + build + 수동 |
| **AC-PROJECT-24** | 실제 브라우저에서 Author가 만든 프로젝트를 Reviewer가 볼 수 없다 | Reviewer 목록에 없음, URL 직접 접근 시 404 화면 | 수동 (Windows 로컬, 사용자) |
| **AC-PROJECT-25** | 기존 테스트(SPEC-AUTH 포함)와 CI가 보안 설정 변경 없이 통과한다 | CI Backend·Frontend success, `SecurityConfig` 인가 규칙 변경 없음 | CI |

## 12. 기술적 고려사항 및 위험

| ID | 위험 / 고려사항 | 대응 |
| --- | --- | --- |
| R1 | 목록 조회 시 `owner` 지연 로딩으로 N+1 발생 | 목록 응답에 `owner`를 넣지 않음 (`ProjectSummaryResponse`) |
| R2 | Next.js 16 `cacheComponents`에서 동적 라우트의 `useParams`·`usePathname`(`RequireAuth`)이 `Suspense` 없이 빌드 실패 | `/projects/[projectId]`에서 `RequireAuth` 상위에 `Suspense`, `npm run build`로 확인 |
| R3 | 이중 제출로 같은 프로젝트 2개 생성 | 버튼 비활성화로 완화, 서버 멱등성 키 미도입 (MVP 수용) |
| R4 | 순차 ID 추측 | Owner 조건 조회 + 동일 404 |
| R5 | SPEC-02 확장 시 접근 확인 누락 | `getAccessibleProject` 단일 진입점, §7.8 원칙 |
| R6 | 비활성 계정의 기존 세션으로 프로젝트 생성 가능 | [결정 Q9] SPEC-AUTH 한계 유지 |
| R7 | 클라우드에 JDK 25 없음 | CI·Windows 로컬로 검증, 주체 구분 보고 |
| R8 | 저장 실패 테스트에서 삭제한 사용자·데이터가 다른 테스트에 영향 | 별도 클래스, 테스트 전용 사용자 사용, `@AfterEach`에서 정리 |
| R9 | 숫자가 아닌 경로 변수가 현재 `500`으로 처리됨 (§6.5) | [결정 Q14] `MethodArgumentTypeMismatchException` 전용 처리 추가 + SPEC-AUTH 회귀 테스트 |
| R10 | 애플리케이션 공백 규칙과 DB `btrim`(ASCII 스페이스)의 차이, Java·JavaScript 공백 기준 차이 | 애플리케이션(Java) 검증을 기준으로 삼고 DB `CHECK`는 최소 방어선, Frontend는 명시적 문자 범주 사용 (§5.5.6, §5.5.7) |
| R11 | `U+0000` 등 제어 문자가 DB 오류(500)로 이어질 가능성 | [결정 Q13] 이름·설명 모두 애플리케이션에서 사전 거부 |
| R12 | UTF-16 길이 기준이 사용자에게 보이는 글자 수와 다름 | [결정 Q15] 문서화, 오류 문구는 "100자 이하" 유지 (§5.5.4) |
| R13 | 세션 사용자의 행이 없는 비정상 상태에서 생성 시 `500` | 사용자 삭제 기능이 없어 정상 운영에서는 발생하지 않음. 사용자 삭제 기능 도입 시 재검토 |

## 13. 후속 SPEC과의 관계

| 후속 SPEC | 이 SPEC이 제공하는 것 | 후속에서 결정·추가할 것 |
| --- | --- | --- |
| SPEC-02 Project Member | `projects`, Owner, `getAccessibleProject`, §7.8 원칙 | 구성원 테이블, 초대 방식, 역할(`spec.md` §16.2), 접근 조건 확장, 구성원 관리 권한, `403` 기준 |
| SPEC-03/04 Document·Version | 프로젝트 ID, 접근 확인 진입점 | `documents.project_id` FK, 문서 등록·수정 권한, 파일 저장소 |
| SPEC-06 이후 Review·Feedback | 프로젝트 단위 접근 경계 | 검토·승인 권한, Reviewer 구성원 검증 ([기존] `spec.md` §10) |
| SPEC-13 Audit Log | 프로젝트 생성 이벤트 발생 지점 | 프로젝트 생성 기록 여부 ([결정 Q7]) |

## 14. 결정 사항 및 미결정 사항

### 14.1 결정 사항 (2026-10-23, 사용자 채택 — Q1~Q15)

| ID | 질문 | 결정 | 반영 위치 |
| --- | --- | --- | --- |
| Q1 | Owner·구성원 표현 | `projects.owner_id` FK만 | §5.1, §5.3 |
| Q2 | 상태 필드 | 두지 않음 | §3, §5.2 |
| Q3 | 이름·설명 수정 | SPEC-01에서 제외 | §3 |
| Q4 | 최대 길이 | 이름 100자, 설명 1000자 (정규화 후) | §5.2, §5.5, AC-06 |
| Q5 | 같은 Owner의 이름 중복 | 허용 | §5.2, §7.7, AC-16 |
| Q6 | 목록 페이지네이션 | 없음 | §3, §6.3 |
| Q7 | 생성 AuditLog | SPEC-13에서 결정 | §3, §13 |
| Q8 | 정의되지 않은 필드(`ownerId` 등) | 무시하며, `ownerId`를 보내도 Owner는 반드시 세션 사용자 | §6.3, §7.2, AC-03 |
| Q9 | 비활성 계정 재확인 | SPEC-AUTH 한계 유지 | §3, §7.6 |
| Q10 | 로그인 후 `/` | 기존 유지 + "내 프로젝트" 링크 | §8.1 |
| Q11 | 오류 코드 | `PROJECT_NOT_FOUND` 신규 | §6.4, §7.4 |
| Q12 | 파일명 | `specs/01-project-create.md` | 이 문서 |
| Q13 | 이름·설명 문자 정책 | 이름: 제어 문자(`\t`·`\n`·`\r`·U+0000 포함)·`Zl`/`Zp`·폭 없는 문자(U+200B, U+2060, U+FEFF, U+180E)·양방향 서식 제어(U+202A–202E, U+2066–2069) 거부, 보이는 문자 1개 이상 필수, 가장자리 공백(NBSP·U+3000 포함) 제거, 내부 공백·ZWJ/ZWNJ/LRM/RLM 허용. 설명: `\t`·`\n`·`\r` 외 제어 문자 거부 | §5.5.1~5.5.3, §5.5.5, AC-05, AC-19 |
| Q14 | 숫자가 아닌 `projectId` | `MethodArgumentTypeMismatchException` 전용 처리 → `400 BAD_REQUEST`, 공통 형식 유지, SPEC-AUTH 회귀 테스트 | §6.2, §6.4, §6.5, AC-13 |
| Q15 | 길이 계산 단위 | UTF-16 code unit (`@Size`, JavaScript `length`와 동일), 보이는 글자 수와 다를 수 있음을 문서화, 추가 라이브러리 없음 | §5.5.4, AC-06 |

### 14.2 미결정 사항

없음. 구현 중 범위 변경이나 중요한 설계 문제가 생기면 사용자에게 보고한 뒤 이 문서를 먼저 갱신한다.

## 15. 구현 계획 및 단계별 체크리스트 (승인 후)

0. [x] 이 SPEC 설계 확정 (Q1~Q15, 2026-10-23)
1. [x] GitHub Issue #6 생성, `feat/6/project-create` 브랜치 생성
2. [x] 문서: 이 SPEC 확정 커밋(`ecf865a`), `specs/README.md` SPEC 표에 행 추가, `CLAUDE.md` §6 현재 단계 갱신, `specs/00-auth.md` 상태 줄(병합 완료) 갱신 여부 확인
3. [x] `GlobalExceptionHandler`에 `MethodArgumentTypeMismatchException` → `400 BAD_REQUEST` 처리 + 테스트, SPEC-AUTH 회귀 확인 ([결정 Q14])
4. [x] Backend 도메인: `V3__create_projects.sql`, `Project`, `ProjectRepository`, `ProjectText`, `CreateProjectRequest` 정규화·검증(§5.5) + 단위 테스트
5. [x] Backend API: `ProjectService`(`create`, `findMine`, `getAccessibleProject`), `ProjectController`, 응답 DTO, `ErrorCode.PROJECT_NOT_FOUND` + 통합 테스트(FK 위반 기반 저장 실패 테스트 포함)
6. [x] CI 확인 (Backend) — `20853cf`, GitHub Actions run 39883535943 성공
7. [x] Frontend: `features/projects`, `/projects`, `/projects/new`, `/projects/[projectId]`(`Suspense`), `/` 링크 + Vitest
8. [x] Frontend lint·test·build (클라우드) → CI 확인 — `dc6e8f5`, GitHub Actions run 39886025416 성공
9. [x] 문서: README, `docs/architecture.md`, 이 SPEC §16 결과 기록
10. [ ] Windows 로컬 수동 검증 (사용자, §10.4)
11. [ ] PR (`Closes #6`), 병합은 사용자

## 16. 검증 결과

**Backend·Frontend: GitHub Actions CI 통과, Frontend는 클라우드에서도 직접 실행 (2026-10-23).** Windows 로컬 검증(§10.4)은 미실행이다.

- 커밋 `20853cf`, GitHub Actions run `39883535943`: Backend(build & test, PostgreSQL 16 서비스)와 Frontend(lint·test·build) Job 모두 성공. SPEC-AUTH 기존 테스트 포함 전체 Backend 테스트가 함께 실행되었다.
- CI의 PostgreSQL 로그에서 의도한 DB 오류가 실제로 발생했음을 확인했다: `projects_owner_id_fkey` 위반(AC-PROJECT-15 저장 실패, AC-PROJECT-17 미존재 Owner), `ck_projects_name_not_blank` 위반(AC-PROJECT-17).
- 클라우드 환경에는 JDK 25가 없어 Backend를 컴파일·실행하지 못했다. Backend 검증은 CI 결과만을 근거로 한다.
- Backend 테스트: `ProjectTextTest`, `CreateProjectRequestValidationTest`(§5.5.5 #1~#17), `ProjectTest`, `ProjectApiIntegrationTest`(AC-PROJECT-01~14, 오류 계약 §6.5), `ProjectPersistenceFailureIntegrationTest`(AC-PROJECT-15), `ProjectConcurrencyIntegrationTest`(AC-PROJECT-16), `ProjectSchemaIntegrationTest`(AC-PROJECT-17).
- Frontend (커밋 `dc6e8f5`, GitHub Actions run `39886025416` 성공):
  - Claude Code 클라우드(Node.js 22.22.0)에서 직접 실행: `npm run lint` 오류·경고 0건, `npm run test` 12개 파일 90건 통과(기존 39건 + 신규 51건), `npm run build` 성공. CI에서도 같은 결과(90건 통과, build 성공).
  - 신규 테스트: `validation.test.ts`(§5.5.5 #1~#17, Java 문자 기준 일치), `api.test.ts`(경로·메서드·본문·CSRF 헤더), `ProjectList`·`ProjectCreateForm`·`ProjectDetail` 컴포넌트 테스트(AC-PROJECT-18~22, 언마운트 후 응답 무시, 이전 요청 응답 무시), `routes.test.tsx`(실제 `AuthProvider`·`RequireAuth`·`api-client`로 AC-PROJECT-22·23).
  - `/projects/[projectId]`에서 `Suspense`를 `ProjectDetail`만 감싸도록 바꾸면 `usePathname()`(RequireAuth) 때문에 `npm run build`가 실패함을 실험으로 확인했다(§8.2, R2). 현재 구조(`Suspense` → `RequireAuth` → `ProjectDetail`)는 빌드에 성공한다.
  - 클라우드 `npm run build`에서 Next.js가 AI 에이전트 환경을 감지해 "알려진 보안 취약점이 있는 버전" 업그레이드 안내로 첫 실행을 중단했다. 재실행 시 경고만 출력하고 성공했으며 CI에서는 나타나지 않았다. 버전 변경은 이 SPEC 범위 밖이므로 하지 않았다.
- AC 상태: AC-PROJECT-01~23, 25는 자동 테스트·CI로 확인. **AC-PROJECT-24(실제 브라우저에서 Reviewer 접근 차단)와 §10.4 수동 E2E는 Windows 로컬에서 미실행.**

| 항목 | Claude Code 클라우드 | GitHub Actions CI | Windows 로컬 (사용자) |
| --- | --- | --- | --- |
| Backend build·test | 실행 불가 (JDK 25 없음) | 성공 (`20853cf`) | 미실행 |
| Frontend lint·test·build | 성공 (lint 0건, 90 tests, build) | 성공 (`dc6e8f5`) | 미실행 |
| 수동 E2E (§10.4) | — | — | 미실행 |

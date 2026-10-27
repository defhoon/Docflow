# SPEC-03: 문서 등록

- 상태: **Draft v0.2 (2026-10-27) — 설계 보완 완료, 구현 미승인**
- Issue: [#15](https://github.com/defhoon/Docflow/issues/15)
- 브랜치: `feat/15/document-create`
- 관련 문서: [`spec.md`](../spec.md) §4.1, §5, §7.1, §9, §12, §13, §16 / [`specs/01-project-create.md`](./01-project-create.md) §5.5, §7.4, §7.7, §7.8 / [`specs/02-project-members.md`](./02-project-members.md) §5.1, §7.2, §7.3 / [ADR-0001](../docs/adr/0001-technology-stack.md) Q2, [ADR-0002](../docs/adr/0002-package-structure.md), [ADR-0005](../docs/adr/0005-session-authentication.md) / [`CLAUDE.md`](../CLAUDE.md) §3, §4
- 작성일: 2026-10-27
- 변경 이력:
  - v0.1 (2026-10-27): 설계 분석(대화)에서 확정한 Q1~Q14, 추가 결정 D1~D8 반영. 문서 작성
  - v0.2 (2026-10-27): 최종 설계 리뷰 반영.
    - (B1) PDF 검증 기준과 AC 불일치 해소: 헤더 + 최소 구조(`%%EOF`, `startxref`, 교차 참조 위치) 검증으로 구체화하고 보장하지 않는 범위 명시 (D9)
    - (B2) OOXML Content Type 문자열 검색 제거: JDK 기본 XML 파서(DTD·외부 엔티티·외부 리소스 차단)로 `[Content_Types].xml`의 PartName–ContentType 대응 검증, 주 문서 파트를 끝까지 읽어 크기·CRC 확인 (D8 수정, D10)
    - (R1) ZIP 검사 자원 상한을 업로드 크기와 구분하고, 상한 초과를 검증 실패로 명시, 수치를 잠정값과 근거·검증 방법으로 정리 (D11)
    - (R2) DB 실패 통합 테스트를 세션 사용자 삭제 방식에서 저장 키 UNIQUE 위반(두 번째 INSERT 실패) 방식으로 변경
    - (R3) `spec.md` §16.1 파일 저장소 상태를 ADR-0001·이 SPEC과 일치하도록 "확정"으로 갱신
  - v0.2 보완 (2026-10-27): AC-DOC-12 테스트와 덮어쓰기 금지 정책의 모순 해소.
    - DB 롤백·보상 삭제(시나리오 A)와 저장 키 충돌·덮어쓰기 방지(시나리오 B)를 분리 (§10.2.1)
    - 최종 배치를 `ATOMIC_MOVE`에서 배타적 생성(`CREATE_NEW`)으로 변경: `ATOMIC_MOVE`는 기존 파일을 교체할 수 있음을 확인 (§5.4)
    - 보상 삭제 대상을 "이 요청이 생성에 성공한 파일"로 한정 (§7.2)

표기: **[기존]** 이미 구현·확정된 내용, **[결정 Qn·Dn]** 사용자가 확정한 결정, **[제안]** 이 SPEC의 설계안, **[검증 필요]** 구현 단계에서 실제 실행으로 확인할 사항.

---

## 1. 목적 및 배경

- `spec.md` §9 SPEC-03 "Document 등록"이다. §13 MVP Acceptance Criteria의 "Project에 Document를 등록할 수 있다", "최초 등록 시 DocumentVersion이 생성된다"를 구현한다.
- 파일 업로드와 파일 저장소가 이 SPEC에서 처음 도입된다. 저장소 방향은 ADR-0001 Q2(잠정: 로컬 파일시스템)를 이 SPEC에서 확정한다(§5.4).
- 이후 새 버전 등록(SPEC-04), 버전 조회·다운로드(SPEC-05), 검토 요청(SPEC-06)이 이 SPEC의 Document·DocumentVersion·저장소를 사용한다.

## 2. 목표

1. 프로젝트 구성원이 파일 하나로 문서를 등록하면 Document(DRAFT)와 DocumentVersion v1이 함께 만들어진다.
2. 허용한 4개 형식만 받고, 확장자와 실제 형식이 다르거나 손상된 파일은 거부한다.
3. 파일 저장과 DB 저장이 어긋나도 "파일이 없는 DB 행"은 만들지 않는다.
4. 구성원이 아닌 사용자에게는 프로젝트와 문서의 존재를 드러내지 않는다.

## 3. 범위

### In Scope

| 항목 | 근거 |
| --- | --- |
| 프로젝트에 Document 등록 + DocumentVersion v1 동시 생성 | `spec.md` §13 |
| PDF, DOCX, XLSX, PPTX 업로드와 형식 검증 | [결정 Q4, D8] |
| 최대 파일 크기 20MiB, 초과 시 `413 FILE_TOO_LARGE` | [결정 Q5, D2, D3] |
| 프로젝트별 문서 목록 조회 | [결정 Q13] |
| 문서 상세 메타데이터 조회 | [결정 Q3] |
| 로컬 파일시스템 저장소와 `FileStorage` 추상화 | [결정 Q6, D5] |
| 프로젝트 접근 확인과 문서 등록 권한 | [결정 Q1] |
| Frontend: 문서 목록·등록·상세 화면 | [제안] |

### Out of Scope

| 항목 | 근거 |
| --- | --- |
| 기존 문서에 새 버전 등록 | SPEC-04 |
| 버전 목록, 파일 다운로드, 미리보기 | SPEC-05 이후 [결정 Q3] |
| Review 요청, Reviewer 지정, 상태 전이 | SPEC-06 이후 [결정 Q2, Q14] |
| 문서 제목·설명 수정, 문서 삭제 | [결정 Q10], `spec.md` §16.2 |
| AuditLog | SPEC-13 |
| 악성코드 검사, 저장소 할당량, 고아 파일 자동 정리 | 잔여 위험으로 기록 (§12) |
| 멱등성 키 기반 중복 등록 방지 | 재전송 시 별도 문서 생성 허용 [결정] |
| 목록 페이지네이션·검색 | [결정 Q13] |

## 4. 사용자 시나리오

### 4.1 문서 등록 (OWNER·MEMBER)

1. 구성원이 프로젝트 상세의 "문서" 섹션에서 "문서 등록"을 누른다.
2. 제목(필수), 설명(선택), 파일(필수, PDF·DOCX·XLSX·PPTX, 20MiB 이하)을 입력하고 제출한다.
3. 서버가 파일을 검증·저장하고 Document(DRAFT)와 Version 1을 만든다.
4. 문서 상세 화면으로 이동한다. 제목, 설명, 상태(DRAFT), 작성자 표시 이름, 생성 시각, 최신 버전(1, 원본 파일명, 크기, 업로드 시각)이 표시된다.

### 4.2 문서 목록

- 구성원은 프로젝트 상세에서 문서 목록(제목, 상태, 작성자, 최신 버전 번호, 등록 시각)을 본다. 최신 등록 순이다.

### 4.3 구성원이 아닌 사용자

- 프로젝트 ID나 문서 ID를 알아도 목록·상세·등록 모두 존재하지 않는 경우와 같은 `404`를 받는다.

### 4.4 구성원에서 제거된 사용자 [결정 Q9]

- 제거된 사용자가 등록한 문서는 프로젝트에 남는다. 작성자 표시 이름은 계속 보인다.
- 제거된 사용자는 제거 트랜잭션 커밋 이후 시작되는 요청부터 문서에 접근할 수 없다([기존] SPEC-02 D5).

## 5. 도메인 및 데이터 모델

### 5.1 Document와 DocumentVersion [제안]

| Entity | 설명 |
| --- | --- |
| `Document` | 프로젝트에 속한 문서. 제목·설명·상태·작성자. 파일은 Version이 가진다 |
| `DocumentVersion` | 문서의 특정 버전 파일 메타데이터. SPEC-03에서는 v1만 만든다 |

- **작성자** [결정 Q2]: 최초 등록 사용자. `documents.created_by`에 기록하고 변경하지 않는다. Reviewer 역할 모델은 SPEC-06에서 결정한다(`spec.md` §16.2 일부는 계속 미결정).
- **상태** [결정 Q14]: `spec.md` §7.1의 `DRAFT`, `IN_REVIEW`, `CHANGES_REQUESTED`, `RE_REVIEW`, `APPROVED`. 등록 시 `DRAFT`. SPEC-03은 상태 변경 API를 제공하지 않는다. Java `enum` + `@Enumerated(EnumType.STRING)`.
- **최신 버전** [결정 Q13]: `MAX(version_number)`로 계산한다. `documents`에 최신 버전 포인터 컬럼을 두지 않는다. SPEC-04에서 동시 버전 등록과 함께 재검토한다.
- 패키지: `com.docflow.document`([기존] `spec.md` §11.3·ADR-0002의 예정 도메인). 파일 저장소는 `document.storage` [제안].

### 5.2 Flyway V5 [제안]

```sql
-- SPEC-03 (Issue #15): 문서와 문서 버전
CREATE TABLE documents (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id   BIGINT        NOT NULL REFERENCES projects (id) ON DELETE RESTRICT,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(1000),
    status       VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    created_by   BIGINT        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_documents_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_documents_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'RE_REVIEW', 'APPROVED'))
);

-- 프로젝트별 문서 목록: created_at DESC, id DESC
CREATE INDEX ix_documents_project_created ON documents (project_id, created_at DESC, id DESC);

CREATE TABLE document_versions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id        BIGINT       NOT NULL REFERENCES documents (id) ON DELETE RESTRICT,
    version_number     INT          NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) NOT NULL,
    size_bytes         BIGINT       NOT NULL,
    storage_key        VARCHAR(100) NOT NULL,
    sha256             CHAR(64)     NOT NULL,
    uploaded_by        BIGINT       NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_document_versions_document_version UNIQUE (document_id, version_number),
    CONSTRAINT uk_document_versions_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_document_versions_version_positive CHECK (version_number >= 1),
    CONSTRAINT ck_document_versions_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_document_versions_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_document_versions_filename_not_blank CHECK (length(btrim(original_filename)) > 0)
);
```

- **FK 삭제 정책: 모두 `RESTRICT`.** [기존] `project_members.project_id`는 `CASCADE`지만, 문서와 버전은 디스크 파일과 연결되어 있어 DB 행만 연쇄 삭제하면 파일이 남는다. 프로젝트·문서 삭제를 도입하는 SPEC에서 파일 정리와 함께 결정한다.
- **인덱스:** 목록용 `(project_id, created_at DESC, id DESC)`. `UNIQUE (document_id, version_number)` 인덱스가 문서별 버전 조회와 `MAX` 계산에 쓰인다. `created_by`, `uploaded_by`에는 조회가 없고 사용자 삭제 기능도 없어 인덱스를 두지 않는다.
- **시각:** `documents`는 [기존] `BaseTimeEntity`(`created_at`, `updated_at`). `document_versions`는 수정하지 않으므로 `created_at`만 두고 [기존] `ProjectMember`와 같은 방식(`@CreatedDate` + `AuditingEntityListener`)으로 매핑한다.
- **업무 규칙은 DB에 넣지 않는다:** 20MiB 상한, 허용 형식, 제목 문자 규칙은 애플리케이션이 판단한다. DB `CHECK`는 마지막 방어선이다([기존] SPEC-01 §5.5.7과 같은 원칙). `VARCHAR(n)`은 code point 수로 세므로 애플리케이션의 UTF-16 길이 제한이 항상 같거나 더 엄격하다.
- `content_type`은 서버가 판별한 표준 MIME 값이다(§5.5). 클라이언트가 보낸 `Content-Type`은 저장하지 않는다.
- `uploaded_by`는 v1에서 `created_by`와 같다. SPEC-04에서 다른 사용자가 새 버전을 올릴 수 있게 하기 위한 컬럼이다.

### 5.3 입력 검증 [결정 Q8, D7]

| 필드 | 규칙 |
| --- | --- |
| `title` | [기존] `ProjectText` 정규화(가장자리 공백 제거)·이름 문자 규칙(제어·줄 구분·폭 없는·양방향 문자 거부, 보이는 문자 1개 이상) 재사용. 정규화 후 UTF-16 1~200자 |
| `description` | [기존] `ProjectText` 설명 규칙 재사용(`\t`·`\n`·`\r` 외 제어 문자 거부). 정규화 후 빈 값이면 `null`. 0~1000자 |
| `file` | 필수. 크기·형식은 §5.5, §5.6 |
| 원본 파일명 | §5.7 |

- 같은 프로젝트에서 문서 제목 중복을 허용한다.
- 오류 메시지 예시: "문서 제목을 입력해 주세요.", "문서 제목은 200자 이하로 입력해 주세요.", "문서 제목에 사용할 수 없는 문자가 포함되어 있습니다.", "설명은 1000자 이하로 입력해 주세요.", "파일을 선택해 주세요."
- **구현 차이:** [기존] `ProjectNameCharacters` 등 검증 어노테이션은 메시지가 "프로젝트 이름…"으로 고정되어 있다. 문자 규칙 함수(`ProjectText`)는 재사용하고, 문서용 메시지를 가진 검증기를 둔다(새 문자 규칙은 만들지 않는다).

### 5.4 파일 저장소 [결정 Q6, D5]

```java
// [제안] com.docflow.document.storage
public interface FileStorage {
    /** 검증·해시 계산이 끝난 임시 파일 내용을 서버가 만든 저장 키 위치에 새 파일로 배치한다. 대상이 이미 있으면 실패한다(덮어쓰지 않음). */
    StoredFile store(Path validatedTempFile);
    /** 보상 삭제. 이 요청이 store()로 생성한 저장 키에만 호출한다. 없으면 아무 일도 하지 않는다. */
    void delete(String storageKey);
    /** 업로드 스트림을 받을 임시 파일 (저장 루트와 같은 볼륨) */
    Path createTempFile();
}
```

- **구현:** `LocalFileStorage`. 저장 루트는 환경변수 `DOCFLOW_STORAGE_DIR`.
  - 로컬 개발 기본값을 제공한다(예: `backend/`에서 실행 시 `./storage`, 구현 단계에서 확정). 기본 저장 경로는 Git 추적 대상에서 제외한다(`.gitignore`, 구현 단계).
  - `.env.example`에 변수를 추가한다(구현 단계).
  - 테스트는 테스트별 독립 임시 디렉터리를 주입한다. CI도 같은 방식이다.
- **저장 키:** 서버가 만든 UUID 기반 문자열(예: `documents/3f2a…`, 확장자 없음). 원본 파일명은 저장 경로에 쓰지 않는다. 저장 키 생성은 메서드 하나짜리 구성 요소(`StorageKeyGenerator`, 기본 구현은 UUID)로 분리한다. 운영 코드에는 이 인터페이스와 기본 구현 하나만 추가되며, 통합 테스트(§10.2.1)에서만 고정 키 구현으로 교체한다 [제안]. 다른 방식(DB 트리거, 세션 사용자 행 삭제)은 이미 배제했고, 이보다 단순하게 "두 번째 INSERT만 실제 제약으로 실패"시키는 방법은 없다.
- **덮어쓰기 금지와 배타적 생성:** 최종 파일은 `StandardOpenOption.CREATE_NEW`로 열어 임시 파일 내용을 스트림 복사해 만든다. `CREATE_NEW`는 JDK API 문서상 "파일 존재 확인과 생성이 다른 파일시스템 작업에 대해 원자적"이므로, 같은 저장 키 파일이 이미 있으면 동시 경쟁 상황에서도 기존 파일을 건드리지 않고 `FileAlreadyExistsException`으로 실패한다 → 저장 실패(`500`).
  - `Files.move(…, ATOMIC_MOVE)`는 사용하지 않는다. JDK 문서상 대상이 있을 때 교체 여부가 구현에 따라 다르며, 2026-10-27 클라우드(Linux, JDK 21) 실험에서 **기존 파일을 예외 없이 교체**했다.
  - `Files.exists` 확인 후 이동·복사(옵션 없는 `Files.move`·`Files.copy`)는 실험에서는 기존 파일이 있으면 실패했지만, 확인과 생성 사이의 원자성이 API 문서로 보장되지 않아 사용하지 않는다.
  - 복사 중 장애가 나면 이 요청이 만든 최종 파일을 지운다(아직 DB 행이 없으므로 참조되지 않는다). 프로세스 중단으로 지우지 못하면 고아 파일이 남는다(R1).
  - 비용: 최종 배치가 이름 변경이 아니라 최대 20MiB 복사가 된다(MVP 수용).
- **경로 이탈 방지:** 저장 키 → 경로 변환 후 `normalize()`한 경로가 저장 루트 아래인지 확인한다. 저장 키는 서버만 만들지만 방어적으로 확인한다.
- **임시 파일:** 저장 루트 아래 `tmp/`에 만든다. 검증을 통과하지 못한 파일이 최종 저장 위치에 생기지 않게 하기 위함이다. 최종 배치 후 또는 실패 시 삭제한다.
- **노출 금지** [결정]: 서버 저장 경로와 `storage_key`는 API 응답·오류 메시지에 넣지 않는다.
- 저장소 확정은 ADR-0001 Q2의 상태를 "확정"으로 바꾸어 기록한다(새 ADR을 만들지 않는다).

### 5.5 허용 형식과 검증 [결정 Q4, D6, D8, D9~D11]

| 확장자 | 저장 `content_type` | 검증 방식 | OOXML 주 문서 파트와 필수 Content Type |
| --- | --- | --- | --- |
| `.pdf` | `application/pdf` | PDF 최소 구조 검증 (§5.5.2) | — |
| `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | OOXML 검증 (§5.5.3) | `/word/document.xml` = `application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml` |
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | OOXML 검증 (§5.5.3) | `/xl/workbook.xml` = `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml` |
| `.pptx` | `application/vnd.openxmlformats-officedocument.presentationml.presentation` | OOXML 검증 (§5.5.3) | `/ppt/presentation.xml` = `application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml` |

#### 5.5.1 공통 순서

1. **확장자:** 정규화된 원본 파일명(§5.7)의 마지막 확장자(소문자, `Locale.ROOT`)가 허용 목록에 있어야 한다. `report.pdf.exe`는 `exe`로 거부된다.
2. **실제 크기:** 업로드 스트림을 임시 파일로 복사하며 읽은 바이트 수를 센다. 0바이트는 `400`, 20,971,520바이트 초과는 `413`(§5.6).
3. **형식:** 확장자에 맞는 검증(§5.5.2 또는 §5.5.3)을 임시 파일에 대해 수행한다. 클라이언트가 보낸 `Content-Type`은 사용하지 않는다.
4. **오류:** 형식·구조 검증 실패, 검사 자원 상한 초과는 모두 `400 VALIDATION_FAILED`, `fieldErrors[].field = "file"` [결정 D6]. 업로드 크기 제한(`413`)과 검사 자원 상한(`400`)은 서로 다른 제한이다.
5. **새 의존성 없음:** JDK 기본 기능(`RandomAccessFile`/`SeekableByteChannel`, `java.util.zip.ZipFile`, `java.util.zip.CRC32`, JDK 기본 StAX 파서, `MessageDigest`)으로 구현한다. Apache Tika·POI·PDFBox 등은 도입하지 않는다. 구현 중 JDK만으로 아래 요구를 충족할 수 없으면 의존성을 추가하지 않고 보고한다.

#### 5.5.2 PDF 최소 구조 검증 [결정 D9]

SPEC-03은 PDF의 **헤더와 최소 구조**만 검증한다. 파일의 앞부분 최대 1,024바이트, 뒷부분 최대 1,024바이트, `startxref`가 가리키는 위치의 최대 64바이트만 읽는다(파일 크기와 무관하게 읽는 양이 일정하다).

**확인하는 것 (모두 만족해야 통과)**

| # | 조건 | 의미 |
| --- | --- | --- |
| P1 | 앞 1,024바이트 안에 `%PDF-` 다음에 버전 `1.0`~`1.7` 또는 `2.0`(정규식 `%PDF-(1\.[0-7]|2\.0)`)이 있다. 헤더 시작 위치를 기준 오프셋으로 기록한다 | 헤더 |
| P2 | 뒤 1,024바이트 안에 `%%EOF`가 있다 | 파일 끝 표시 |
| P3 | 뒤 1,024바이트 안에서 마지막 `%%EOF`보다 앞에 `startxref` 키워드와 그 뒤의 10진수 오프셋이 있다 | 교차 참조 위치 |
| P4 | 오프셋 + 기준 오프셋이 파일 크기보다 작다 | 범위 |
| P5 | 그 위치(공백 문자 건너뜀)가 `xref`(교차 참조 표) 또는 `<정수> <정수> obj`(교차 참조 스트림, PDF 1.5+)로 시작한다 | 교차 참조 구조가 실제로 있음 |

- 따라서 `%PDF-` 문자열만 포함한 임의의 텍스트 파일, `%%EOF`나 `startxref`가 없는 파일, `startxref`가 범위를 벗어나거나 교차 참조가 아닌 곳을 가리키는 파일은 거부된다.

**보장하지 않는 것**

- 교차 참조 표·스트림의 각 항목, 객체·스트림의 유효성, 압축 데이터 무결성, 암호화 여부, 렌더링 가능 여부는 검사하지 않는다.
- 따라서 **최소 구조를 갖추고 내부가 일부 손상된 PDF, 구조가 복잡한 PDF(증분 업데이트, 선형화 등)는 손상 여부와 관계없이 통과할 수 있다.** PDF 전체 무결성은 SPEC-03에서 보장하지 않는다.
- 반대로 `startxref` 오프셋이 틀렸지만 PDF 뷰어가 자동 복구해 열 수 있는 PDF는 거부될 수 있다(과잉 거부 가능성). 구현 시 일반 도구(MS Office·LibreOffice·브라우저 인쇄·macOS 미리보기 등)로 만든 정상 PDF 표본으로 확인한다(§12.1).
- 전체 PDF 파싱 라이브러리는 도입하지 않는다.

#### 5.5.3 OOXML 검증 (DOCX·XLSX·PPTX) [결정 D8, D10]

임시 파일을 JDK `java.util.zip.ZipFile`로 연다. 파일 전체를 압축 해제하거나 모든 엔트리를 메모리에 올리지 않는다. 아래 두 엔트리만 읽는다.

1. **ZIP 구조:** 중앙 디렉터리를 열 수 없거나 손상되었으면 거부한다. 암호화된 엔트리가 있으면 거부한다.
2. **엔트리 수:** 상한(§5.5.4) 초과 시 거부한다.
3. **필수 엔트리 존재:** `[Content_Types].xml`과 형식별 주 문서 파트 엔트리(`word/document.xml`, `xl/workbook.xml`, `ppt/presentation.xml`)가 있어야 한다.
4. **`[Content_Types].xml` 파싱 (JDK 기본 XML 파서):**
   - 크기 상한(§5.5.4) 안에서 끝까지 읽어 파싱한다. 상한을 넘으면 거부한다.
   - XML이 형식에 맞지 않거나(well-formed 아님) 루트가 OPC Content Types(`Types`)가 아니면 거부한다.
   - `Default`(`Extension` → `ContentType`)와 `Override`(`PartName` → `ContentType`) 항목을 모은다. PartName과 확장자 비교는 OPC 규칙대로 ASCII 대소문자를 구분하지 않는다.
   - **주 문서 파트의 실효 Content Type**을 구한다: 해당 PartName의 `Override`가 있으면 그 값, 없으면 그 파트 확장자의 `Default` 값.
   - 실효 Content Type이 위 표의 형식별 주 문서 Content Type과 **정확히 일치**해야 한다. 같은 PartName의 `Override`가 둘 이상이면 거부한다.
   - 이로써 다음을 거부한다: 문자열만 포함하고 파트와 연결되지 않은 경우, 주 문서 파트가 다른 형식으로 선언된 경우(예: `.xlsx`인데 Word 주 문서), 매크로 포함 형식(`…document.macroEnabled.main+xml` 등)·서식 파일 형식(`…template.main+xml`)으로 선언된 경우, 주 문서 파트가 일반 `application/xml`로만 선언된 경우.
5. **주 문서 파트 엔트리 읽기:** ZIP 엔트리 이름(앞의 `/` 제외)이 PartName과 일치하는 엔트리를 크기 상한(§5.5.4) 안에서 **끝까지** 압축 해제하며 읽고(내용은 파싱·보관하지 않음), 읽은 바이트 수가 중앙 디렉터리의 선언 크기와 같은지, 직접 계산한 CRC-32가 선언 값과 같은지 확인한다. 상한을 넘거나, 선언 크기보다 많이/적게 나오거나, CRC가 다르거나, 압축 해제 오류가 나면 거부한다.
6. **XML 파서 보안 설정** (JDK 기본 StAX 구현, `XMLInputFactory.newDefaultFactory()`로 생성해 클래스패스의 다른 구현을 쓰지 않는다):
   - `XMLInputFactory.SUPPORT_DTD = false` (DTD 비활성화)
   - `XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES = false` (외부 엔티티 비활성화)
   - `XMLConstants.ACCESS_EXTERNAL_DTD = ""`, `XMLConstants.ACCESS_EXTERNAL_SCHEMA = ""` (외부 리소스 접근 금지)
   - `XMLResolver`는 항상 예외를 던지도록 설정한다(외부 리소스 조회 시 실패).
   - 문서에 `DOCTYPE`(DTD 이벤트)이 나타나면 즉시 거부한다.
   - 파서 입력은 크기 상한을 강제하는 스트림으로 감싼다. JDK XML 처리 한계(`jdk.xml.*` 기본값)도 유지한다.
7. 주 문서 파트 내용, 관계(`_rels/.rels`, `*.rels`), 다른 파트는 파싱·검증하지 않는다.

**보장하지 않는 것**

- `[Content_Types].xml`과 주 문서 파트 하나만으로 **OOXML 패키지 전체의 유효성을 보장하지 않는다.** 관계 파일의 대상, 다른 파트의 존재·내용, 주 문서 XML의 스키마 유효성은 검사하지 않는다. Office가 열지 못하는 파일이 통과할 수 있다.
- 주 문서 파트가 표준 위치(`word/document.xml` 등)가 아닌 비표준 패키지는 거부될 수 있다(과잉 거부 가능성).
- 매크로 바이너리(`vbaProject.bin`)가 포함되어 있어도 주 문서 Content Type이 일반 형식이면 통과한다. 파일을 서버에서 실행·렌더링하지 않는다.

#### 5.5.4 OOXML 검사 자원 상한 [결정 D11 — 잠정값]

| 항목 | 잠정값 | 근거 | 초과 시 |
| --- | --- | --- | --- |
| ZIP 엔트리 수 | 10,000개 | 일반 DOCX·XLSX·PPTX는 파트가 수십~수천 개다(슬라이드·이미지·시트가 많아도 각 파트가 엔트리 하나). 20MiB 안에 정상 문서가 1만 개를 넘는 경우는 드물다고 판단한다 | `400` |
| `[Content_Types].xml` 압축 해제 크기 | 1MiB | 파트 확장자 `Default`와 파트별 `Override` 목록만 담는다. 파트 1만 개에 `Override` 1개당 약 150바이트를 가정해도 약 1.5MB 미만이며, 일반 문서는 수 KB~수십 KB다 | `400` |
| 주 문서 파트 압축 해제 크기 | 128MiB | `word/document.xml`은 본문 전체를 담아 텍스트가 많은 문서에서 커질 수 있다. 20MiB 업로드 상한과 XML의 일반적인 deflate 압축률(대략 5~10배)을 고려해 정상 문서가 거부되지 않을 여유를 둔다. `xl/workbook.xml`, `ppt/presentation.xml`은 시트·슬라이드 목록만 담아 보통 작다 | `400` |
| 검사 전체 압축 해제량 | 위 두 엔트리 합계 (최대 129MiB) | 위 두 엔트리 외에는 압축을 풀지 않는다 | `400` |

- **업로드 파일 크기 제한(20MiB, `413`)과 검사 자원 상한(`400`)은 별개다.** 검사 상한은 작은 ZIP이 크게 풀리는 압축 폭탄을 막기 위한 것이다.
- **상한 초과는 항상 검증 실패다.** 상한까지만 읽고 성공으로 처리하지 않는다. 중앙 디렉터리의 선언 크기가 상한을 넘으면 읽기 전에 거부하고, 선언 크기는 위조될 수 있으므로 실제로 읽은 바이트 수로도 상한을 다시 강제한다.
- **메모리:** `[Content_Types].xml`은 1MiB 이하로 파싱하고, 주 문서 파트는 고정 크기 버퍼로 읽으며 CRC만 계산한다(내용 미보관). 요청당 추가 메모리는 수 MiB 이하다.
- **CPU:** 압축 해제량이 최대 129MiB로 제한되어 검사 시간이 상한을 가진다(구현 시 측정).
- **잠정값인 이유:** 정상 Office 파일 표본으로 분포를 측정하지 않았다. 위 근거는 추정이며 확정 사실이 아니다. 구현 시 표본(MS Office·LibreOffice·Google Docs 내보내기로 만든 DOCX·XLSX·PPTX, 20MiB에 가까운 파일 포함)의 엔트리 수, `[Content_Types].xml` 크기, 주 문서 파트 크기, 검사 시간을 측정해 기록하고, 정상 표본이 거부되면 원인과 조정안을 보고해 승인 후 값을 바꾼다(§12.1).

#### 5.5.5 검증의 한계 (잔여 위험)

- 위 검증은 "선언한 형식의 최소 구조를 갖춘 파일"인지 확인할 뿐 **안전성이나 완전한 유효성을 보증하지 않는다** (§5.5.2, §5.5.3의 "보장하지 않는 것").
- 폴리글랏 파일(예: PDF이면서 ZIP), 악성 콘텐츠·매크로를 포함한 파일, ZIP 로컬 헤더와 중앙 디렉터리 불일치를 이용한 파일은 통과할 수 있다.
- 암호가 설정된 Office 문서(OLE 컨테이너)는 ZIP이 아니므로 거부된다(기능 제약).
- 악성코드 검사는 범위 밖이다. 파일을 서버에서 실행하거나 렌더링하지 않는다.

### 5.6 크기 제한 [결정 Q5, D1~D4]

- **정의:** 20MiB = 20 × 1024 × 1024 = **20,971,520바이트**. Spring `DataSize`의 `20MB`와 같은 값이다.
  - 1 ~ 20,971,520바이트: 허용
  - 20,971,521바이트 이상: `413 FILE_TOO_LARGE`
  - 0바이트: `400 VALIDATION_FAILED`
- **Spring Boot multipart** [결정 D2, 구현 단계 설정 변경]: `spring.servlet.multipart.max-file-size=20MB`, `max-request-size=21MB`. [기존 확인] 현재는 설정이 없어 기본값(파일 1MB, 요청 10MB)이 적용된다.
- **413 응답** [결정 D3]: 신규 `ErrorCode.FILE_TOO_LARGE`(413), 공통 형식 `{ code, message }`, 메시지 예: "파일은 20MB 이하만 업로드할 수 있습니다." 내부 경로·예외 정보는 넣지 않는다.
  - [기존 확인] `MaxUploadSizeExceededException`은 Spring `ErrorResponse`(413)를 구현해, 현재 `GlobalExceptionHandler`의 catch-all이 **413 + 코드 `BAD_REQUEST`**로 응답한다. 전용 처리로 `FILE_TOO_LARGE`를 반환한다.
  - 서버가 스트림 복사 중 직접 센 크기가 상한을 넘을 때도 같은 오류를 반환한다(이중 확인).
- **Next.js rewrite** [결정 D1, 구현 단계 설정 변경]: `next.config.ts`에 `experimental.proxyClientMaxBodySize: '25mb'`.
  - [기존 확인] Next.js 16.4.1은 rewrite 프록시에 요청 본문 복제 스트림(`clonableBody`)을 쓰며, 기본 10MB를 넘는 부분은 **오류 없이 잘라 전달하고 경고만 남긴다**(`body-streams.js`). 설정하지 않으면 10MB 초과 업로드가 잘린 multipart로 Backend에 도착한다.
  - 25MB로 두면 21MB(요청 상한) 초과 요청도 잘리지 않고 Backend에 도착해 Spring이 413을 판단한다. 25MB를 넘는 요청은 여전히 잘릴 수 있다 → 정상 UI는 클라이언트 사전 검사(20MiB)로 전송하지 않는다.
  - **위험:** experimental 옵션이라 Next.js 버전 업그레이드 시 이름·동작이 바뀔 수 있다. 본문 복제를 위해 요청당 최대 설정 크기만큼 메모리를 쓸 수 있다(동시 업로드 수 × 최대 25MB). MVP 단일 사용자 환경에서 수용하고, 업그레이드 시 재확인한다.
- **Tomcat·프록시 타임아웃** [결정 D4]: `server.tomcat.max-swallow-size`(기본 2MB)와 Next.js `proxyTimeout`(기본 30초)은 변경하지 않는다.
  - [검증 필요] Backend가 본문을 다 읽기 전에 응답하면(크기 초과, 미인증) Tomcat이 남은 본문이 2MB를 넘을 때 연결을 닫아, 클라이언트·프록시가 `413`/`401` 대신 연결 오류를 받을 수 있다.
  - [검증 필요] 느린 네트워크에서 20MiB 업로드가 30초 프록시 타임아웃을 넘을 수 있다.
  - 재현되면 원인과 대안을 보고하고 추가 승인 후 변경한다.

### 5.7 원본 파일명 [결정 Q8, D7]

1. multipart의 원본 파일명에서 마지막 `/`와 `\` 뒤 부분만 사용한다(경로 구분자 제거).
2. [기존] `ProjectText` 가장자리 공백 제거를 적용한다.
3. [기존] `ProjectText` 이름 문자 규칙으로 제어 문자, 줄·문단 구분자, 폭 없는 문자, 양방향 제어 문자(`U+202E` 등 표시 위장)를 거부한다. 보이는 문자가 1개 이상이어야 한다.
4. `.`·`..`만으로 된 이름, 확장자가 없는 이름은 거부한다.
5. UTF-16 255자를 넘으면 **자르지 않고** `400` 거부한다.
6. 거부 시 `400 VALIDATION_FAILED`, `fieldErrors[file]`.
7. 정규화된 이름을 `original_filename`에 저장하고, 구성원에게 메타데이터로 표시한다 [결정]. 저장 경로에는 쓰지 않는다.
8. 애플리케이션 로그에 원본 파일명을 남기지 않는다 [제안]. 로그에는 `projectId`, `documentId`, `storage_key`만 남긴다.

## 6. Backend API 명세

### 6.1 공통 [기존 재사용]

- 인증: Spring Security 세션. 모든 엔드포인트는 로그인 필요. `SecurityConfig` 변경 없음.
- CSRF: `POST`는 `X-XSRF-TOKEN` 헤더 필요(multipart도 같음).
- 오류 형식: `{ code, message, fieldErrors? }`.
- 숫자가 아닌 `projectId`·`documentId`: `400 BAD_REQUEST`([기존] SPEC-01 Q14).

### 6.2 엔드포인트 [결정 Q11, Q12]

| 메서드·경로 | 권한 | 성공 |
| --- | --- | --- |
| `POST /api/projects/{projectId}/documents` (`multipart/form-data`) | 구성원 (OWNER, MEMBER) | `201`, `Location: /api/projects/{projectId}/documents/{documentId}`, `DocumentResponse` |
| `GET /api/projects/{projectId}/documents` | 구성원 | `200`, `DocumentSummaryResponse[]` |
| `GET /api/projects/{projectId}/documents/{documentId}` | 구성원 | `200`, `DocumentResponse` |

- 문서는 항상 경로의 `projectId`와 `documentId`를 **함께** 조건으로 조회한다(`findByIdAndProjectId`). 다른 프로젝트의 문서 ID를 넣으면 존재하지 않는 문서와 같은 `404 DOCUMENT_NOT_FOUND`다(IDOR 방어).
- 메타데이터만 먼저 만드는 2단계 업로드는 사용하지 않는다 [결정 Q12].

### 6.3 요청·응답 본문

**요청 (`multipart/form-data`)**

| 파트 | 필수 | 설명 |
| --- | --- | --- |
| `title` | O | 문자열 |
| `description` | X | 문자열. 없거나 공백만이면 `null` |
| `file` | O | 파일 1개 |

- 정의되지 않은 파트는 무시한다. 작성자·상태·버전 번호는 요청으로 받지 않는다.

**`DocumentResponse`** (등록 `201`, 상세 `200`)

```jsonc
{
  "id": 10,
  "title": "요구사항 명세서",
  "description": "1차 초안",
  "status": "DRAFT",
  "createdBy": { "id": 1, "displayName": "Author" },
  "createdAt": "2026-10-27T09:00:00Z",
  "updatedAt": "2026-10-27T09:00:00Z",
  "latestVersion": {
    "versionNumber": 1,
    "originalFilename": "requirements.pdf",
    "sizeBytes": 1048576,
    "uploadedAt": "2026-10-27T09:00:00Z"
  }
}
```

**`DocumentSummaryResponse`** (목록 항목)

```jsonc
{ "id": 10, "title": "요구사항 명세서", "status": "DRAFT", "createdBy": { "id": 1, "displayName": "Author" },
  "createdAt": "2026-10-27T09:00:00Z", "latestVersionNumber": 1 }
```

- 응답에 넣지 않는 것: `storage_key`, 서버 경로, `sha256`, `content_type`, 작성자 이메일 [결정, SPEC-02 Q3과 같은 원칙].
- 목록 정렬: `created_at DESC, id DESC` [결정 Q13]. 목록 조회는 작성자와 최신 버전 번호를 한 번의 쿼리로 가져온다(N+1 방지, 구현 시 확인).

### 6.4 오류 코드

| 코드 | HTTP | 상황 |
| --- | --- | --- |
| [기존] `VALIDATION_FAILED` | 400 | 제목·설명 오류, 파일 누락·빈 파일·형식 불일치·손상·파일명 오류(`fieldErrors[file]`) |
| [기존] `BAD_REQUEST` | 400 | 숫자가 아닌 경로 변수 |
| [기존] `AUTH_REQUIRED` | 401 | 미인증 |
| [기존] `CSRF_INVALID` | 403 | CSRF 실패 |
| [기존] `PROJECT_NOT_FOUND` | 404 | 존재하지 않거나 구성원이 아닌 프로젝트 |
| [신규] `DOCUMENT_NOT_FOUND` | 404 | 이 프로젝트에 없는 문서 (다른 프로젝트 문서, 미존재 — 본문 동일) |
| [신규] `FILE_TOO_LARGE` | 413 | 20,971,520바이트 초과, multipart 요청 상한 초과 |
| [기존] `UNSUPPORTED_MEDIA_TYPE` | 415 | `multipart/form-data`가 아닌 요청 |
| [기존] `INTERNAL_ERROR` | 500 | 파일 저장 실패, DB 오류. 내부 경로·저장 키 미노출 [결정] |

- 파일 누락(`file` 파트 없음)은 프레임워크 기본 `400 BAD_REQUEST`가 아니라 `400 VALIDATION_FAILED` + `fieldErrors[file]`로 응답한다 [제안, 구현 시 바인딩 방식 확인].

### 6.5 처리 순서

```text
Security(CSRF·인증) → multipart 해석(크기 상한 → 413) → 경로 변수 타입(400) → 본문 바인딩·검증(400)
→ 프로젝트 접근(404) → 문서 등록 권한 → 파일명 검증(400) → 임시 파일 복사·크기·해시(413/400) → 형식 검증(400)
→ 최종 위치 배치 → DB 트랜잭션(Document + Version v1) → 201
```

- multipart 해석과 본문 검증은 Spring MVC가 Service 호출 전에 수행하므로, 구성원이 아닌 사용자도 크기 초과·형식 오류 요청에는 `413`·`400`을 먼저 받을 수 있다. 이 응답에는 프로젝트 정보가 없어 존재 노출이 없다([기존] SPEC-02 D2·D4와 같은 원칙). **비구성원 404 보장은 형식이 올바른 요청 기준이다.**
- 파일 내용 검증(임시 파일 복사 이후)은 접근·권한 확인 뒤에 수행한다. 구성원이 아닌 사용자의 파일은 저장소에 쓰지 않는다.

## 7. 인증·인가, 트랜잭션, 동시성

### 7.1 권한 [결정 Q1]

| 작업 | OWNER | MEMBER | 비구성원 | 미인증 |
| --- | --- | --- | --- | --- |
| 문서 등록 | O | O | `404` | `401` |
| 문서 목록 | O | O | `404` | `401` |
| 문서 상세 | O | O | `404` | `401` |

- SPEC-02의 "MEMBER는 프로젝트·구성원 조회만 가능"은 **SPEC-02 기능 범위 안의 정책**이며, 문서 등록 권한을 제한하는 정책으로 해석하지 않는다 [결정 Q1].
- 접근 확인은 [기존] `ProjectService.getAccessibleProject()`, 작업별 권한은 [기존] `ProjectPermissionService`에 문서 등록 권한 메서드를 추가해 판단한다(현재는 OWNER·MEMBER 모두 허용). 권한 규칙을 Controller·Repository·Frontend에 두지 않는다(SPEC-01 §7.8 원칙 2).
- 업로드 중 구성원에서 제거되는 경우: [기존] SPEC-02 D5 원칙대로 접근 확인 시점에 구성원이었다면 등록이 성공할 수 있다. 제거 커밋 이후 시작된 요청부터 404다.

### 7.2 파일 저장과 DB 트랜잭션 [결정 Q7]

**정상 흐름**

1. 인증·인가 확인
2. 입력값·파일명 검증
3. 업로드 스트림을 저장 루트 `tmp/`의 임시 파일로 복사 (크기·SHA-256 계산, 메모리 사용은 버퍼 크기 수준)
4. 형식 검증
5. 서버 생성 저장 키로 최종 파일을 배타적 생성(`CREATE_NEW`)하고 임시 파일 내용 복사 (§5.4)
6. DB 트랜잭션 하나에서 Document와 DocumentVersion v1 INSERT
7. 커밋 성공 후 `201`

**실패 흐름과 보상**

| 실패 지점 | 응답 | DB | 파일 |
| --- | --- | --- | --- |
| 1~2 | 401·403·404·400 | 행 없음 | 임시 파일 없음 |
| 3 임시 파일 쓰기 실패 | `500 INTERNAL_ERROR` | 행 없음 | 임시 파일 삭제 시도 |
| 3 크기 초과 / 4 형식 오류 | `413` / `400` | 행 없음 | 임시 파일 삭제 |
| 5 최종 배치 실패 — 같은 저장 키 파일이 이미 있음 | `500` | 행 없음 (트랜잭션 시작 전) | 임시 파일만 삭제. **기존 파일은 이 요청이 만든 것이 아니므로 삭제·변경하지 않는다** |
| 5 최종 배치 실패 — 생성 후 복사 중 I/O 오류 | `500` | 행 없음 (트랜잭션 시작 전) | 이 요청이 만든 최종 파일과 임시 파일 삭제 시도 |
| 6 INSERT 실패 (제약 위반 등) | `500` (또는 해당 오류) | **Document·Version 모두 롤백** | **롤백 확정 후** 최종 파일 삭제 (보상) |
| 6 커밋 결과 불명확 (커밋 중 연결 단절 등) | `500` | 알 수 없음 | **삭제하지 않는다.** `storage_key`를 WARN 로그로 남긴다 |
| 보상 삭제 실패 | 원래 오류 유지 | 롤백됨 | 고아 파일 잔존, `storage_key` 로그 |

- **파일 저장 실패 시 DB 행이 생기지 않는다:** DB 트랜잭션은 파일이 최종 위치에 놓인 뒤에만 시작한다.
- **파일 저장 실패와 DB 롤백 보상은 다른 경로다:** 저장 실패(1~5단계)는 DB 트랜잭션 시작 전이며 "이 요청이 만든 파일 정리"만 한다. 보상 삭제(6단계)는 커밋 실패 후 이미 배치된 최종 파일을 지우는 것이다. 두 경우 모두 **이 요청이 `CREATE_NEW`로 생성에 성공한 파일만** 지운다. 생성에 실패한 저장 키(기존 파일)는 어떤 경우에도 지우지 않는다.
- **보상 삭제는 롤백이 확정된 경우에만:** 트랜잭션 완료 결과가 "롤백"으로 확정되었을 때만 파일을 지운다. 커밋 결과가 불명확한데 파일을 지우면 "파일 없는 DB 행"이 생길 수 있으므로 지우지 않는다.
- **원래 오류를 덮지 않는다:** 보상 삭제나 임시 파일 정리 중 발생한 예외는 원래 예외에 suppressed로 붙이거나 로그로만 남기고, 응답은 원래 오류 기준으로 한다.
- **원자성의 한계:** 저장소와 DB 사이에는 원자적 트랜잭션이 없다. 따라서 고아 파일(DB 행 없는 파일)은 생길 수 있고, 이는 잔여 위험이다(§12). 반대 방향("DB 행이 있는데 파일 없음")은 위 순서로 막는다.
- **구현 방식** [제안 — 구현 단계에서 프로젝트 구조를 확인한 뒤 선택]: [기존] SPEC-02의 `TransactionTemplate` 패턴, 트랜잭션 완료 콜백(`TransactionSynchronization#afterCompletion`의 `STATUS_ROLLED_BACK`/`STATUS_UNKNOWN`) 등을 후보로 한다. 관찰 가능한 동작(위 표)을 통합 테스트로 확정한다.
- **IDENTITY와 flush:** `Document`·`DocumentVersion` ID가 IDENTITY이므로 INSERT는 `save()` 시점에 실행된다([기존] SPEC-01 §7.7). 지연 제약이 없어 제약 위반은 `save()` 중 발생하지만, 커밋 시점 오류도 같은 보상 규칙을 따라야 한다.

### 7.3 동시성 [결정]

- 같은 사용자가 같은 요청을 다시 보내면 별도 문서가 생성된다(멱등성 키 미도입). Frontend는 제출 중 버튼 비활성화로 줄인다.
- 같은 프로젝트에 같은 제목의 문서를 동시에 등록하면 각각 성공한다(제목 유니크 없음).
- 저장 키는 UUID라 동시 업로드가 같은 파일 경로를 쓰지 않는다. 충돌 시 `uk_document_versions_storage_key`가 최종 방어선이다.
- v1의 `version_number`는 등록 트랜잭션 안에서 1로 고정된다. 버전 번호 경합은 SPEC-04에서 다룬다.

### 7.4 CSRF·세션 [기존 재사용]

- multipart 요청도 [기존] `X-XSRF-TOKEN` 헤더로 CSRF를 검증한다. Frontend는 [기존] `lib/api-client.ts`를 FormData를 지원하도록 최소 확장해 사용한다(§8.3).
- 세션 만료 시 `401 AUTH_REQUIRED`, Frontend는 [기존] 세션 만료 흐름을 따른다.

## 8. Frontend 화면 및 상태 흐름

### 8.1 화면 [제안]

| 경로 | 화면 | 보호 |
| --- | --- | --- |
| `/projects/[projectId]` | [기존] 상세에 "문서" 섹션 추가: 목록 + "문서 등록" 링크 | 기존 |
| `/projects/[projectId]/documents/new` | 문서 등록 폼 | `Suspense` → `RequireAuth` |
| `/projects/[projectId]/documents/[documentId]` | 문서 상세 메타데이터 | `Suspense` → `RequireAuth` |

- 동적 세그먼트가 있으므로 [기존] SPEC-01 §8.2처럼 `RequireAuth`를 포함한 하위 트리 전체를 `Suspense`로 감싼다. `npm run build`로 확인한다.

### 8.2 상태

| 화면 | Loading | Empty | Error | Success |
| --- | --- | --- | --- | --- |
| 문서 목록 | "문서를 불러오는 중..." | "아직 등록된 문서가 없습니다." + 등록 링크 | 오류 + 다시 시도 | 제목·상태·작성자·최신 버전·등록 시각, 상세 링크 |
| 등록 | 제출 중 버튼 비활성화 ("등록 중...") | — | 필드 오류(클라이언트 + 서버 `fieldErrors`), 413 문구, 서버·네트워크 오류 | 상세로 이동 (`router.push`) |
| 상세 | "불러오는 중..." | — | 404·400: "문서를 찾을 수 없습니다." / 프로젝트 404: 프로젝트 없음 / 그 외: 오류 + 다시 시도 | 메타데이터 표시 (다운로드 없음) |

### 8.3 등록 폼

- 클라이언트 사전 검사(서버가 최종 기준): 제목·설명은 [기존] `features/projects/validation.ts` 규칙 재사용(제목 200자), 파일 필수, 확장자 4종, 크기 1~20,971,520바이트(`File.size`).
- 전송: `FormData`(`title`, `description`, `file`). `Content-Type`은 브라우저가 boundary와 함께 설정하도록 지정하지 않는다. CSRF 헤더는 [기존] 방식대로 붙는다. [기존] `CSRF_INVALID` 1회 재시도 시 같은 `FormData`를 다시 보낸다.
- [기존 확인] 현재 `api-client.ts`는 본문이 있으면 `Content-Type: application/json`과 `JSON.stringify`를 적용한다 → `FormData`일 때만 이 처리를 건너뛰도록 최소 확장한다.
- 오류 표시: `413` "파일은 20MB 이하만 업로드할 수 있습니다.", `400 fieldErrors[file]`은 파일 필드 아래, 제목·설명 오류는 각 필드, `404 PROJECT_NOT_FOUND`는 프로젝트 없음 화면, `401`은 세션 만료 흐름.
- 제출 중 버튼 비활성화와 연속 제출 차단([기존] SPEC-01 패턴).
- **`<Activity>` 재진입** [기존 교훈, SPEC-01 연속 생성 버그]: 성공 후 화면이 숨겨질 때 폼을 초기화한다. 파일 입력은 코드로 값을 설정할 수 없으므로 폼 `reset()` 또는 입력 재마운트(`key` 변경)로 비운다. 요청 중 화면을 떠났다가 성공·실패한 경우에도 제출 상태를 복구한다.

## 9. 예외 및 오류 처리 (`spec.md` §12 검토)

| 시나리오 | Backend | Frontend |
| --- | --- | --- |
| 미인증 | `401` | 로그인 화면 |
| 프로젝트 구성원이 아닌 사용자 | `404 PROJECT_NOT_FOUND` (미존재와 같은 본문) | 프로젝트 없음 |
| 다른 프로젝트 문서 ID / 없는 문서 | `404 DOCUMENT_NOT_FOUND` (같은 본문) | 문서 없음 |
| 제목·설명·파일명 오류, 파일 누락, 빈 파일 | `400 VALIDATION_FAILED` | 필드 오류 |
| 허용되지 않는 형식, 확장자 위장, 손상된 ZIP·PDF | `400 VALIDATION_FAILED` (`file`) | 파일 필드 오류 |
| 20MiB 초과 | `413 FILE_TOO_LARGE` | 크기 오류 문구 (정상 UI는 사전 검사로 전송하지 않음) |
| 중복 요청 | 별도 문서 생성 (허용) | 제출 중 비활성화 |
| 동시 등록 | 각각 성공 | — |
| 파일 저장 실패 (디스크·권한) | `500 INTERNAL_ERROR`, DB 행 없음 | 서버 오류 문구 |
| DB 저장 실패 | `500`, Document·Version 롤백, 파일 보상 삭제 | 서버 오류 문구 |
| 커밋 결과 불명확 | `500`, 파일 보존, 로그 | 서버 오류 문구 |
| CSRF 실패 | `403 CSRF_INVALID` | [기존] 1회 재시도 |
| 네트워크 실패, 프록시 타임아웃 | — | 연결 오류 문구 + 다시 시도 |
| 잘못된 상태 전이 | 해당 없음 (상태 변경 API 없음) | — |

## 10. 테스트 전략

### 10.1 Backend 단위 테스트

- **PDF 최소 구조 (§5.5.2):**
  - 통과: 테스트 코드에서 만든 최소 PDF(교차 참조 표), 교차 참조 스트림 PDF, 헤더 앞에 1,024바이트 이내 접두 바이트가 있는 PDF, 증분 업데이트(`%%EOF` 2개) PDF
  - 거부: `%PDF-`만 포함한 텍스트, 헤더 버전 형식 오류(`%PDF-9.9`), `%%EOF` 없음, `startxref` 없음, `startxref` 오프셋이 파일 크기 이상, 오프셋 위치가 `xref`·`obj`가 아님, 헤더가 1,024바이트 뒤에 있음, DOCX를 `.pdf`로 바꾼 파일
  - 보장 범위 확인: 최소 구조는 맞고 내부 객체가 손상된 PDF가 **통과함**을 테스트로 명시한다(보장하지 않는 범위의 회귀 방지)
- **OOXML (§5.5.3):**
  - 통과: 테스트 코드에서 만든 최소 DOCX·XLSX·PPTX(`Override`로 주 문서 선언), 주 문서 PartName 대소문자가 다른 선언
  - 거부: 손상된 ZIP, 암호화 엔트리, 필수 엔트리(`[Content_Types].xml`·주 문서 파트) 누락, 형식에 맞지 않는 XML, 루트가 `Types`가 아님, 주 문서 `Override` 없음(`Default`만으로 `application/xml`), 주 문서 Content Type이 다른 형식(XLSX를 `.docx`로)·매크로 형식·서식 파일 형식, Content Type 문자열이 다른 PartName이나 주석에만 있음, 같은 PartName `Override` 중복
  - XML 보안: `DOCTYPE` 포함 → 거부, 외부 엔티티(`file:`·`http:` 참조) 포함 → 거부되고 외부 접근이 일어나지 않음(로컬 테스트 서버·파일 접근 없음 확인), 엔티티 확장 공격 → 거부
  - 자원 상한: 엔트리 수 10,001개, `[Content_Types].xml` 1MiB 초과, 주 문서 파트 선언 크기가 상한 초과, 선언 크기와 실제 압축 해제 크기 불일치, CRC 불일치, 높은 압축률 주 문서 파트(상한 초과) → 모두 `400`이며 상한 이상 압축 해제하지 않음
- 파일명 정규화: `../a.pdf`, `C:\x\a.pdf`, 제어·양방향·폭 없는 문자, 255/256자, `report.pdf.exe`, 확장자 없음
- 저장소: 저장 키 → 경로 변환의 루트 이탈 차단, 임시 파일 위치
- 제목·설명 검증(SPEC-01 §5.5.5 케이스 재사용, 길이 200)
- 상태 enum, 권한(`ProjectPermissionService`)
- 보상 규칙: 트랜잭션 완료 결과가 롤백·커밋·불명확일 때 파일 삭제 여부 (콜백 단위 테스트)

### 10.2 Backend 통합 테스트 (실제 PostgreSQL, 보안 설정 활성)

| 대상 | 내용 |
| --- | --- |
| 등록 | `201`, `Location`, 응답 필드(내부 정보 미포함), `documents`·`document_versions` 각 1행, 파일 존재·크기·SHA-256 일치, `status=DRAFT`, `version_number=1` |
| 4개 형식 | 각 형식 `201`, 저장 `content_type` 확인 |
| 형식 위장·손상 | `400 VALIDATION_FAILED`, `fieldErrors[file]`, DB 행 없음, 저장소에 파일 없음 |
| 권한 | OWNER·MEMBER 등록·조회 성공, 비구성원 등록·목록·상세 `404`(미존재와 본문 동일), 저장소에 파일 없음 |
| IDOR | 프로젝트 A 구성원이 `/projects/A/documents/{B의 문서}` → `404 DOCUMENT_NOT_FOUND`, 미존재 문서와 본문 동일 |
| 목록 | 정렬, 최신 버전 번호, 작성자 표시 이름, 빈 목록 |
| 제거된 구성원 | 문서 유지·작성자 이름 표시, 제거된 사용자는 `404` |
| **크기 경계** | 실제 HTTP(`webEnvironment = RANDOM_PORT`)로 1MiB, 19MiB, 20,971,520바이트(`201`), 20,971,521바이트·21MiB(`413 FILE_TOO_LARGE`) — MockMvc는 Tomcat multipart 상한을 거치지 않으므로 사용하지 않는다 |
| **파일 저장 실패** | `DOCFLOW_STORAGE_DIR`를 일반 파일로 지정해 실제 I/O 오류 → `500`, DB 행 없음, 응답에 경로 없음 |
| **저장 키 충돌·덮어쓰기 방지** | §10.2.1 시나리오 B — 기존 행과 파일이 있는 저장 키로 업로드 → `500`, 새 행 없음, 기존 파일 내용·SHA-256 불변, 기존 행 유지 |
| **DB 실패 보상** | §10.2.1 시나리오 A — 파일 배치 성공 후 Version INSERT가 저장 키 UNIQUE 위반으로 실패 → `500`, 원인 제약 확인, `documents`·`document_versions` 행 수 불변, 새로 만든 파일 보상 삭제, 기존 행 유지 |
| 공통 | 미인증 `401`, CSRF 없음 `403`(DB·저장소 변화 없음), JSON 요청 `415`, 숫자가 아닌 경로 변수 `400`, `file` 누락 `400 VALIDATION_FAILED` |
| 동시성 | 같은 요청 2회 → 문서 2개. (선택) [기존] SPEC-02 DB 잠금 대기 기법으로 "접근 확인 후 구성원 제거 커밋 → 등록 성공"(D5) 재현 |
| 스키마 | V5 FK·UNIQUE·CHECK 직접 INSERT 검증, `ddl-auto=validate` |
| **회귀** | SPEC-AUTH·SPEC-01·SPEC-02 기존 테스트 전체 |

- 보상 삭제 실패와 커밋 결과 불명확은 실제 장애로 결정적으로 재현하기 어렵다. 이 두 경우만 `FileStorage` 테스트 대역과 콜백 단위 테스트로 검증한다(mock 사용 예외).
- **테스트 데이터 정리 순서:** 새 FK가 모두 `RESTRICT`이므로 문서 테스트는 `document_versions` → `documents` → (기존) `projects` → `users` 순으로 정리하고, 테스트별 임시 저장 디렉터리를 사용한다. 문서 행이 남으면 [기존] 다른 테스트의 `DELETE FROM projects`·`users` 정리가 실패하므로, 문서를 만드는 모든 테스트 클래스는 자체 정리를 보장한다.

#### 10.2.1 저장 키 고정 통합 테스트: DB 롤백 보상(A)과 덮어쓰기 방지(B)

두 시나리오 모두 테스트 클래스 전용 구성으로 `StorageKeyGenerator`(§5.4)를 고정 키 `K`를 반환하는 구현으로 교체한다. 키 생성만 대체하며 저장소·DB·트랜잭션은 실제 구현이다. 요청자(세션 사용자)는 정상 구성원이고, 그 데이터는 바꾸지 않는다.

**공통 준비 (테스트마다 독립):** 테스트별 사용자·프로젝트를 만들고, `DOCFLOW_STORAGE_DIR`는 테스트별 임시 디렉터리를 쓴다. 별도 프로젝트·문서에 `storage_key = K`인 `document_versions` 행("기존 행")을 JDBC로 넣는다. 정리는 §10.2의 순서를 따른다. 실행 순서나 다른 테스트 데이터에 의존하지 않는다.

**시나리오 A — DB 롤백과 보상 삭제 (AC-DOC-12)**

- **격리된 픽스처:** 테스트 저장소에 `K` 파일을 **만들지 않는다.** "DB 행은 있는데 파일이 없는" 상태는 이 테스트만을 위해 의도적으로 만든 격리된 픽스처이며, 운영에서 정상적으로 생기는 상태가 아니다.
- **흐름:**
  1. 정상 구성원이 유효한 파일을 업로드한다.
  2. `K` 파일이 없으므로 `CREATE_NEW` 배치가 성공한다(이 요청이 `K`를 소유).
  3. DB 트랜잭션: `documents` INSERT 성공 → `document_versions` INSERT에서 **실제 PostgreSQL UNIQUE 위반**(`uk_document_versions_storage_key`, 기존 행과 충돌) → 트랜잭션 롤백.
  4. 롤백 확정 후 보상: 이 요청이 만든 `K` 파일 삭제.
- **검증:**
  - 응답 `500 INTERNAL_ERROR`, 본문에 경로·저장 키·제약 이름 없음
  - 실패 원인이 DB 제약임: 테스트 로그(PostgreSQL 오류)에 `uk_document_versions_storage_key`가 나타나고, 저장소 오류 로그가 없음
  - `documents`·`document_versions` 행 수가 요청 전과 같음 (먼저 INSERT된 Document도 롤백)
  - `K` 파일이 존재하지 않음(보상 삭제됨), 임시 디렉터리 비어 있음
  - 기존 행은 그대로 남아 있음

**시나리오 B — 저장 키 충돌과 덮어쓰기 방지 (AC-DOC-11, AC-DOC-28)**

- **준비:** 기존 행과 함께 테스트 저장소에 `K` 파일을 실제 내용으로 만들고 SHA-256을 기록한다(운영과 같은 정상 상태).
- **흐름:**
  1. 정상 구성원이 유효한 파일을 업로드한다.
  2. 최종 배치에서 `CREATE_NEW`가 `FileAlreadyExistsException`으로 실패한다 → 저장 실패. DB 트랜잭션은 시작하지 않는다.
  3. 정리: 이 요청의 임시 파일만 삭제한다. `K`는 이 요청이 생성하지 않았으므로 삭제하지 않는다.
- **검증:**
  - 응답 `500 INTERNAL_ERROR`, 본문에 경로·저장 키 없음
  - 새 `documents`·`document_versions` 행 없음 (행 수 불변)
  - `K` 파일이 존재하고 내용·SHA-256이 준비 시점과 같음 (덮어쓰기·삭제 없음)
  - 기존 행 유지, 임시 디렉터리 비어 있음

**한계와 보조 검증**

- 두 시나리오 모두 저장 키 생성기를 교체하는 테스트 전용 구성이 필요하다. 실패 자체는 실제 PostgreSQL 제약(A)과 실제 파일시스템 동작(B)이 일으킨다.
- 동시 경쟁(두 요청이 같은 키를 동시에 만드는 경우)은 UUID 충돌이 사실상 없고 결정적 재현이 어려워 통합 테스트로 만들지 않는다. 대신 `CREATE_NEW`의 원자성은 JDK API 계약에 의존하며, 단위 테스트로 "대상이 있으면 실패하고 기존 내용이 그대로"임을 Linux(CI)에서 확인하고 Windows 수동 검증에서 같은 동작을 확인한다(§12.1).
- 대안으로 SPEC-02의 DB 잠금 대기 기법(별도 커넥션이 프로젝트 행 삭제를 미커밋으로 잡았다가 커밋)으로 `documents.project_id` FK 위반을 일으킬 수 있으나, 첫 번째 INSERT에서 실패해 "Document·Version 동시 롤백"을 직접 보여 주지 못하므로 보조 테스트로만 고려한다.

### 10.3 Frontend 테스트 (Vitest)

| 대상 | 내용 |
| --- | --- |
| API 클라이언트 | `FormData` 본문은 `Content-Type`을 지정하지 않고 그대로 전송, CSRF 헤더 유지, `CSRF_INVALID` 재시도 시 본문 재전송, 기존 JSON 요청 회귀 |
| 문서 API | 경로·메서드·`FormData` 파트(`title`, `description`, `file`) |
| 등록 폼 | 클라이언트 검증(제목, 파일 필수, 확장자, 20,971,520바이트 경계), 제출 중 비활성화·중복 제출 방지, `201` → 상세 이동, `400 fieldErrors`(제목·파일)·`413`·`404`·`401`·5xx 표시 |
| `<Activity>` 재진입 | 성공 후 숨김 → 재표시 시 제목·설명·파일 입력이 비어 있고 제출 가능, 요청 중 숨김 후 성공·실패 시 제출 상태 복구 |
| 목록·상세 | Loading·Empty·Error·Success, 상태·최신 버전 표시, 404 처리, 다운로드 UI 없음 |
| 세션 | 문서 API `401` → `/login?...&reason=expired` (화면 단위) |
| 빌드 | 새 동적 경로의 `Suspense` 배치로 `npm run build` 성공 |
| 회귀 | SPEC-01·02 Frontend 테스트 전체 |

### 10.4 실제 업로드 경로 검증 (구현 단계, 클라우드 수동 실행 + Windows)

- Next.js(`next start` 또는 `next dev`) → rewrite → Spring Boot 실제 경로로 다음을 확인한다. CI에서는 두 서버를 함께 띄우지 않으므로 자동화 대상이 아니다.
  - 1MiB, 19MiB, 20MiB(20,971,520바이트) 업로드 성공, 저장 파일의 크기·SHA-256 일치(본문 잘림 없음)
  - 21MiB 업로드 시 브라우저(또는 HTTP 클라이언트)가 `413 FILE_TOO_LARGE`를 실제로 받는지, 연결 종료로 끝나는지 [결정 D4]
  - 미인증 상태 대용량 업로드 시 `401` 전달 여부
  - 느린 업로드(대역폭 제한)에서 30초 프록시 타임아웃 동작

### 10.5 수동 검증 (Windows 로컬, 사용자)

1. Author 로그인 → 프로젝트 상세 → 문서 등록(PDF) → 상세 이동, 메타데이터 확인
2. DOCX·XLSX·PPTX 각각 등록, 목록 정렬·최신 버전 확인
3. Reviewer(MEMBER) 로그인 → 문서 등록·조회 가능
4. 비구성원 계정 → 문서 상세 URL 직접 접근 시 "찾을 수 없음"
5. 확장자 위장 파일(예: 텍스트를 `.pdf`로), 빈 파일, 21MiB 파일 → 오류 문구
6. 19MiB·20MiB 파일 업로드 성공 (Next.js rewrite 경유)
7. 업로드 요청의 `X-XSRF-TOKEN` 헤더, `201` 응답, 응답에 저장 경로·저장 키 없음
8. `DOCFLOW_STORAGE_DIR` 아래 파일 생성 확인, Git 상태에 업로드 파일이 나타나지 않음
9. 등록 성공 후 목록에 갔다가 등록 화면에 다시 들어오면 폼(파일 포함)이 비어 있음

### 10.6 검증 환경과 주체

| 검증 | 주체 |
| --- | --- |
| Backend 단위·통합 (JDK 25, PostgreSQL) | GitHub Actions CI. 클라우드는 JDK 21 + 로컬 PostgreSQL로 보조 실행(저장소 설정 변경 없이) |
| Frontend lint·test·build | Claude Code 클라우드, CI |
| 실제 업로드 경로 (§10.4) | Claude Code 클라우드 수동 실행(가능한 범위), 사용자 Windows |
| 수동 E2E (§10.5) | 사용자 (Windows 로컬) |

## 11. Acceptance Criteria

| ID | 기준 | 확인 방법 | 검증 |
| --- | --- | --- | --- |
| **AC-DOC-01** | OWNER와 MEMBER는 문서를 등록할 수 있다 | `201`, `Location`, `DocumentResponse`, `status=DRAFT`, `latestVersion.versionNumber=1`, `createdBy`=세션 사용자 | 통합 |
| **AC-DOC-02** | 등록 시 Document와 Version v1이 함께 저장된다 | `documents` 1행 + `document_versions` 1행(`version_number=1`, `uploaded_by`=작성자), 파일 크기·SHA-256 일치 | 통합 |
| **AC-DOC-03** | PDF·DOCX·XLSX·PPTX를 허용하고 서버가 판별한 형식을 저장한다 | 4종 `201`, `content_type`이 §5.5 값 | 통합 |
| **AC-DOC-04** | 허용되지 않는 형식과 형식 위장 파일을 거부한다 | 허용 외 확장자, `%PDF-` 문자열만 포함한 텍스트, 다른 형식을 `.pdf`로 바꾼 파일, 주 문서 Content Type이 다른 OOXML 형식·매크로·서식 형식으로 선언된 파일, Content Type 문자열이 주 문서 파트와 연결되지 않은 파일 → `400 VALIDATION_FAILED`, `fieldErrors[file]`, DB 행·저장 파일 없음 (§5.5.2, §5.5.3) | 단위 + 통합 |
| **AC-DOC-05** | 최소 구조 검증을 통과하지 못하거나 검사 자원 상한을 넘는 파일을 거부한다 | PDF: §5.5.2 P1~P5 중 하나라도 불충족. OOXML: 손상 ZIP, 필수 엔트리 누락, `[Content_Types].xml` 형식 오류·`DOCTYPE`·외부 엔티티, 주 문서 파트 크기·CRC 불일치, 엔트리 수·압축 해제량 상한 초과 → `400`, 상한 이상 압축 해제하지 않음, 외부 리소스 접근 없음. **최소 구조를 갖춘 PDF의 내부 손상은 이 기준의 거부 대상이 아님**(§5.5.2) | 단위 |
| **AC-DOC-06** | 빈 파일과 파일 누락을 거부한다 | `400 VALIDATION_FAILED`, `fieldErrors[file]` | 통합 |
| **AC-DOC-07** | 20MiB 경계가 정확하다 | 실제 HTTP: 1MiB·19MiB·20,971,520바이트 `201`, 20,971,521바이트·21MiB `413 FILE_TOO_LARGE`(공통 형식, 내부 정보 없음) | 통합 (RANDOM_PORT) |
| **AC-DOC-08** | 실제 Next.js rewrite 경로로 19MiB·20MiB 업로드가 잘림 없이 성공한다 | 저장 파일 크기·SHA-256이 원본과 일치 | 수동 (클라우드·Windows) |
| **AC-DOC-09** | 실제 요청 경로에서 크기 초과 시 응답이 전달된다 | 21MiB 업로드에 `413`이 클라이언트에 도달. 연결 종료로 끝나면 원인·대안 보고 후 추가 승인 [결정 D4] | 수동 (클라우드·Windows) |
| **AC-DOC-10** | 제목·설명·파일명 규칙을 적용한다 | 정규화, 금지 문자, 제목 200·설명 1000·파일명 255(초과 시 거부, 자르지 않음), 경로 구분자 제거, 이중 확장자 거부 | 단위 + 통합 |
| **AC-DOC-11** | 파일 저장 실패 시 DB 행이 생성되지 않는다 | 저장소 I/O 오류, 저장 키 충돌(§10.2.1 B) → `500 INTERNAL_ERROR`, 새 행 0, 응답에 경로·저장 키 없음 | 통합 |
| **AC-DOC-12** | DB 저장 실패 시 Document·Version이 함께 롤백되고 파일이 보상 삭제된다 | §10.2.1 A: 파일 배치 성공 후 Version INSERT의 실제 UNIQUE 위반 → `500`, 원인 제약 확인, 두 테이블 행 수 불변(먼저 INSERT된 Document 포함), 이 요청이 만든 파일 삭제, 기존 행 보존. 세션 사용자 데이터는 변경하지 않음 | 통합 |
| **AC-DOC-13** | 커밋 결과가 불명확하면 파일을 삭제하지 않는다 | 트랜잭션 완료 결과 불명확 → 파일 보존, `storage_key` 로그 | 단위 (콜백) |
| **AC-DOC-14** | 보상 실패가 원래 오류를 덮지 않는다 | 보상 삭제 실패 시에도 원래 오류 응답, 삭제 실패는 로그 | 단위 (테스트 대역) |
| **AC-DOC-15** | 구성원이 아닌 사용자는 프로젝트·문서 존재를 알 수 없다 (유효한 요청 기준) | 등록·목록·상세 `404 PROJECT_NOT_FOUND`, 미존재 프로젝트와 본문 동일, 저장소에 파일 없음 | 통합 |
| **AC-DOC-16** | 다른 프로젝트의 문서 ID로 조회할 수 없다 | `404 DOCUMENT_NOT_FOUND`, 미존재 문서와 본문 동일 | 통합 |
| **AC-DOC-17** | 문서 목록은 구성원에게 정렬된 메타데이터를 반환한다 | `created_at DESC, id DESC`, 작성자 표시 이름, `latestVersionNumber`, 빈 목록 `[]` | 통합 |
| **AC-DOC-18** | 문서 상세는 메타데이터만 반환하고 내부 정보를 노출하지 않는다 | §6.3 필드만, `storage_key`·경로·`sha256`·`content_type`·이메일 없음 | 통합 |
| **AC-DOC-19** | 제거된 구성원의 문서는 유지되고 제거된 사용자는 접근할 수 없다 | 문서·작성자 이름 유지, 제거된 사용자 `404` | 통합 |
| **AC-DOC-20** | 인증·CSRF·요청 형식 오류가 기존 계약대로 처리된다 | 미인증 `401`, CSRF 없음 `403`(DB·저장소 변화 없음), 비 multipart `415`, 숫자가 아닌 경로 변수 `400` | 통합 |
| **AC-DOC-21** | 같은 요청의 재전송은 별도 문서를 만든다 | 2회 요청 → 문서 2개, 저장 키 2개 | 통합 |
| **AC-DOC-22** | Flyway V5 스키마가 적용된다 | `ddl-auto=validate`, FK·UNIQUE·CHECK 직접 INSERT 검증 | 통합 |
| **AC-DOC-23** | Frontend API 클라이언트가 FormData를 전송한다 | `Content-Type` 미지정, CSRF 헤더 유지, `CSRF_INVALID` 재시도 시 재전송, 기존 JSON 요청 회귀 없음 | Vitest |
| **AC-DOC-24** | 등록 폼은 검증·중복 제출 방지·오류 표시를 하고 성공 시 상세로 이동한다 | 클라이언트 검증(크기 경계 포함), 제출 중 비활성화, `400`·`413`·`404`·5xx 표시, `router.push` | Vitest |
| **AC-DOC-25** | 등록 화면 재진입 시 폼과 파일 입력이 초기화된다 | `<Activity>` 숨김→재표시 후 빈 폼, 요청 중 이탈 후 제출 상태 복구 | Vitest |
| **AC-DOC-26** | 문서 목록·상세 화면은 상태를 구분하고 다운로드를 제공하지 않는다 | Loading·Empty·Error·Success, 404 문구, 다운로드 UI 없음 | Vitest |
| **AC-DOC-27** | 문서 API의 `401`은 기존 세션 만료 흐름을 따른다 | `refresh()` → `/login?...&reason=expired` | Vitest |
| **AC-DOC-28** | 업로드 파일은 저장 루트 아래 서버 생성 키로만 저장되고, 기존 파일을 덮어쓰거나 지우지 않으며, Git에 포함되지 않는다 | 저장 경로에 원본 파일명 없음, 루트 이탈 차단 테스트, 같은 키 파일이 있으면 배치 실패·기존 내용 불변(§10.2.1 B, `CREATE_NEW` 단위 테스트), 기본 저장 경로가 Git 제외 | 단위 + 통합 + 수동 |
| **AC-DOC-29** | 실제 브라우저에서 등록·조회·권한·오류 흐름이 동작한다 | §10.5 1~9 | 수동 (Windows 로컬) |
| **AC-DOC-30** | SPEC-AUTH·01·02 동작이 회귀하지 않고 CI가 보안 설정 변경 없이 통과한다 | 기존 Backend·Frontend 테스트 통과, `SecurityConfig` 인가 규칙 변경 없음 | CI |

## 12. 기술적 고려사항 및 잔여 위험

| ID | 위험 | 대응 |
| --- | --- | --- |
| R1 | 저장소와 DB 사이 원자적 트랜잭션 없음 → 고아 파일 | 파일 먼저 배치 → DB, 롤백 확정 시 보상 삭제, 불명확 시 보존·로그 (§7.2). 자동 정리는 범위 밖 |
| R2 | 보상 삭제 실패 (Windows 파일 잠금 등) | 원래 오류 유지, `storage_key` 로그. 고아 파일 수용 |
| R3 | Next.js rewrite 10MB 본문 잘림 | `experimental.proxyClientMaxBodySize: '25mb'` [결정 D1], 실제 경로 검증(AC-DOC-08) |
| R4 | experimental 옵션 변경 가능성, 본문 복제 메모리(동시 업로드 × 최대 25MB) | MVP 수용, Next.js 업그레이드 시 재확인 |
| R5 | 25MB를 넘는 요청은 여전히 잘려 Backend가 손상된 multipart를 받을 수 있음 | 클라이언트 사전 검사, 서버는 손상 multipart를 4xx로 처리하는지 구현 시 확인 |
| R6 | 크기 초과·미인증 시 Tomcat 미소비 본문(2MB 초과)으로 연결 종료 → `413`·`401` 미전달 가능 | [결정 D4] 설정 유지, 실제 경로 검증(AC-DOC-09), 재현 시 보고 후 승인 |
| R7 | 느린 네트워크에서 30초 프록시 타임아웃 | [결정 D4] 유지, 검증 후 판단 |
| R8 | 형식 검증이 안전성을 보증하지 않음 (악성 콘텐츠, 매크로, 폴리글랏, ZIP 헤더 불일치) | 실행·렌더링하지 않음, 다운로드 보안은 SPEC-05. 악성코드 검사 범위 밖 |
| R9 | 저장소 고갈 | 파일 크기 상한만 적용. 할당량·모니터링 범위 밖 |
| R10 | 재전송·이중 제출로 중복 문서 | 허용 [결정], 버튼 비활성화로 완화 |
| R11 | 업로드 중 구성원 제거 | [기존] SPEC-02 D5 원칙 (접근 확인 시점 기준) |
| R12 | 디스크 사용 이중화 (Tomcat multipart 임시 파일 + 저장소 임시 파일) | 요청 처리 후 Tomcat이 정리, 저장소 임시 파일은 실패 시 삭제 |
| R13 | 원본 파일명에 개인정보 포함 가능 | 구성원에게만 표시 [결정], 로그 미기록 |
| R14 | Windows 경로·파일 생성 동작 | 최종 파일을 `CREATE_NEW`로 생성(이동·교체 없음), 백신·색인 서비스의 파일 잠금으로 생성·삭제 실패 시 `500`·보상 로그. Windows에서 충돌 시 기존 파일 보존 수동 확인 |
| R15 | `ProjectText` 재사용으로 document → project 도메인 의존 | 단방향 의존(순환 없음)으로 수용. 공통화 리팩터링은 범위 밖 |
| R16 | PDF 최소 구조 검증은 내부 손상·복잡한 구조의 PDF를 통과시키고, `startxref`가 틀린 복구 가능 PDF는 거부할 수 있음 | 보장 범위를 §5.5.2에 명시, 정상 PDF 표본으로 과잉 거부 확인 (§12.1) |
| R17 | `[Content_Types].xml`과 주 문서 파트만으로 OOXML 패키지 전체 유효성을 보장하지 않음 (관계·다른 파트·스키마 미검증), 비표준 위치의 주 문서는 거부 가능 | 보장 범위를 §5.5.3에 명시, 정상 표본으로 확인 |
| R18 | XML 파서 설정 실수 시 외부 엔티티(XXE)·외부 리소스 접근 위험 | JDK 기본 구현 명시 생성, DTD·외부 엔티티·외부 접근 차단, `DOCTYPE` 거부, 보안 단위 테스트 (§5.5.3) |
| R19 | OOXML 검사 상한이 잠정값이라 정상 대용량 문서를 거부할 수 있음 | 상한 초과는 실패로 처리(부분 성공 없음), 구현 시 표본 측정 후 필요하면 승인받아 조정 (§5.5.4) |
| R20 | 문서 행이 남으면 기존 테스트의 `projects`·`users` 정리가 FK `RESTRICT`로 실패 | 문서 테스트의 자체 정리 순서 보장 (§10.2) |

### 12.1 구현 시 검증 사항 (정책 결정 아님)

- 실제 업로드 경로에서 413·401 전달 여부와 연결 종료 여부 (R6)
- 25MB 초과 요청의 Backend 처리 결과 (R5)
- `file` 파트 누락을 `VALIDATION_FAILED`로 응답하는 바인딩 방식
- 목록 조회의 N+1 방지 쿼리 형태
- 트랜잭션 완료 콜백 기반 보상의 정확한 구현 방식과 커밋 결과 불명확 상태 처리
- OOXML 검사 상한 잠정값(§5.5.4)의 적정성: 정상 표본의 엔트리 수, `[Content_Types].xml` 크기, 주 문서 파트 크기, 검사 시간 측정
- PDF 최소 구조 검증(§5.5.2)의 과잉 거부 여부: 일반 도구로 만든 정상 PDF 표본 확인
- JDK StAX 파서의 보안 속성 동작(`DOCTYPE` 이벤트 처리, 외부 엔티티 차단) 확인
- `DataIntegrityViolationException` 원인 체인에서 제약 이름 확인(§10.2.1 원인 검증)
- `CREATE_NEW` 배타적 생성이 Linux(CI)와 Windows에서 기존 파일을 보존하며 실패하는지 확인 (§5.4, AC-DOC-28)
- 로컬 기본 저장 경로의 정확한 위치와 `.gitignore` 항목

## 13. 후속 SPEC과의 관계

| 후속 SPEC | 이 SPEC이 제공하는 것 | 후속에서 결정할 것 |
| --- | --- | --- |
| SPEC-04 Version 등록 | `document_versions`, `FileStorage`, 형식·크기 검증, 저장–DB 보상 규칙 | 다음 버전 번호 할당과 동시 등록, 최신 버전 포인터 재검토, 승인된 문서에 새 버전 허용 여부(`spec.md` §16.2) |
| SPEC-05 Version 조회 | Version 메타데이터, `storage_key`·`sha256`(내부) | 버전 목록, 다운로드(Content-Disposition, `X-Content-Type-Options: nosniff` 등), 미리보기 여부 |
| SPEC-06 이후 Review | `documents.status = DRAFT`, `created_by` | 상태 전이, Reviewer 역할 모델(`spec.md` §16.2), 제거된 구성원의 진행 중 리뷰 |
| SPEC-13 AuditLog | 문서 등록 이벤트 발생 지점 | 기록 여부와 범위 |
| 삭제 도입 SPEC (미정) | FK `RESTRICT` | 프로젝트·문서 삭제 시 파일 정리 |

## 14. 결정 사항 및 미결정 사항

### 14.1 결정 사항 (2026-10-27, 사용자 확정)

| ID | 결정 | 반영 위치 |
| --- | --- | --- |
| Q1 | OWNER·MEMBER 모두 문서 등록·목록·상세 조회. 비구성원 404, 미인증 401. SPEC-02의 "MEMBER는 조회만"은 SPEC-02 기능 범위의 정책으로 해석. `ProjectPermissionService` 재사용 | §7.1 |
| Q2 | 작성자 = 최초 등록 사용자(`created_by`), 변경 없음. Reviewer 역할 모델은 SPEC-06 | §5.1 |
| Q3 | 상세는 메타데이터만. 다운로드·버전 목록·미리보기 제외(SPEC-05) | §3, §6.3 |
| Q4 | PDF·DOCX·XLSX·PPTX만. 확장자 + 시그니처 + OOXML 구조 검증, 자원 상한, 실제 크기 확인, 확장자·형식 불일치 거부. 악성코드 검사 범위 밖 | §5.5 |
| Q5 | 파일 최대 20MiB, 초과 시 413 `FILE_TOO_LARGE`, 요청 상한은 파일보다 약간 크게 | §5.6 |
| Q6 | 로컬 파일시스템, `FileStorage`, `DOCFLOW_STORAGE_DIR`, UUID 저장 키, 루트 이탈 방지, Git 제외. ADR-0001 Q2 확정 | §5.4 |
| Q7 | 검증 → 임시 파일 → 최종 배치 → DB 트랜잭션 → 201, 롤백 시 보상 삭제, 불명확 시 보존 | §7.2 |
| Q8 | 제목 1~200, 설명 0~1000, `ProjectText` 재사용, UTF-16 기준, 제목 중복 허용, 파일명 경로 문자 제거·255자 | §5.3, §5.7 |
| Q9 | 제거된 구성원의 문서 유지, `created_by` 불변·RESTRICT, 제거된 사용자 접근 상실 | §4.4, §5.2 |
| Q10 | 문서 삭제 제외 | §3 |
| Q11 | 프로젝트 하위 경로, `projectId`+`documentId` 함께 확인 | §6.2 |
| Q12 | multipart 단일 요청(`title`, `description`, `file`), FormData 최소 확장, boundary는 브라우저 설정, CSRF 유지 | §6.3, §8.3 |
| Q13 | `created_at DESC, id DESC`, 페이지네이션 없음, v1, 최신 버전은 `MAX(version_number)`, 포인터 컬럼 없음 | §5.1, §6.3 |
| Q14 | 초기 DRAFT, VARCHAR+CHECK, `@Enumerated(STRING)`, 상태 변경 API 없음 | §5.1, §5.2 |
| 추가 | 파일 저장 실패는 내부 경로 없는 500, 원본 파일명은 구성원에게 표시, 서버 경로·`storage_key` 비노출, 재전송 시 별도 문서 허용, 잔여 위험 문서화, 업로드 중 권한 제거는 SPEC-02 원칙 유지 | §5.4, §7.2, §7.3, §12 |
| D1 | `experimental.proxyClientMaxBodySize: '25mb'`(구현 단계), rewrite 구조 유지, 20MiB 정책 유지, 위험 기록 | §5.6, §12 R3~R5 |
| D2 | `max-file-size=20MB`, `max-request-size=21MB`(구현 단계). 20MB = 20,971,520바이트, 1바이트 초과 시 413 | §5.6 |
| D3 | `FILE_TOO_LARGE`(413), 공통 형식, 내부 정보 비노출 | §5.6, §6.4 |
| D4 | `max-swallow-size`·`proxyTimeout` 유지, 실제 경로 검증 후 재현 시 보고·승인 | §5.6, §10.4, §12 R6·R7 |
| D5 | `DOCFLOW_STORAGE_DIR`, 로컬 기본 경로, Git 제외, 테스트별 임시 디렉터리, UUID 키, 원본 파일명 미사용 (`.env.example`·`.gitignore`·설정 변경은 구현 단계) | §5.4 |
| D6 | 형식·빈 파일·파일명 오류는 `VALIDATION_FAILED` + `fieldErrors.file`, 20MiB 초과는 `FILE_TOO_LARGE` | §5.5, §6.4 |
| D7 | 파일명 최대 255자, 초과 시 자르지 않고 400, 경로 구분자 제거, 제어·양방향·표시 위장 문자 거부, 정규화된 이름만 표시 | §5.7 |
| D8 | OOXML: ZIP 구조, 필수 엔트리, 제한된 크기 안 읽기 검증, 엔트리 수·압축 해제량 제한, 손상·위장 거부, XML 외부 엔티티 처리·전체 Office 문서 파싱 없음. JDK 기본 기능으로 구현. (v0.1의 "XML 파서 미사용"은 v0.2 D10으로 대체) | §5.5.3 |
| D9 | PDF는 헤더와 최소 구조(헤더·`%%EOF`·`startxref`·교차 참조 위치)만 검증. 전체 무결성·렌더링 가능 여부는 보장하지 않으며 위험 명시. 전체 PDF 파싱 라이브러리 미도입 (v0.2) | §5.5.2, AC-DOC-04, AC-DOC-05, R16 |
| D10 | OOXML `[Content_Types].xml`은 JDK 기본 XML 파서로 파싱(DTD·외부 엔티티·외부 리소스 차단)해 주 문서 PartName–ContentType 대응을 검증. 주 문서 파트는 크기 상한 안에서 끝까지 읽어 크기·CRC 확인. 신규 라이브러리 없음 (v0.2) | §5.5.3, AC-DOC-04, AC-DOC-05, R17, R18 |
| D11 | 업로드 크기 제한과 ZIP 검사 자원 상한을 구분. 상한 초과는 검증 실패. 상한 수치는 잠정값(엔트리 10,000개, `[Content_Types].xml` 1MiB, 주 문서 파트 128MiB)이며 구현 시 표본 측정으로 확인 (v0.2) | §5.5.4, R19 |

### 14.2 미결정 사항

정책 미결정 없음. §12.1은 구현 중 확인할 기술 사항이다. 결과가 이 SPEC과 다르면 보고 후 SPEC을 먼저 갱신한다.

## 15. 구현 계획 및 단계별 체크리스트 (구현 승인 후)

0. [x] 설계 결정 확정 (Q1~Q14, D1~D8, 2026-10-27)
1. [x] GitHub Issue #15 생성, `feat/15/document-create` 브랜치 생성
2. [x] 문서: 이 SPEC Draft v0.1, `specs/README.md`, ADR-0001 Q2 확정 (v0.2에서 `spec.md` §16.1 갱신)
3. [x] 최종 설계 리뷰 반영 (Draft v0.2: B1·B2·R1·R2·R3)
3-1. [ ] SPEC 검토·승인 (Draft → 확정)
4. [ ] Backend: V5 마이그레이션, `Document`·`DocumentVersion`, `FileStorage`·`LocalFileStorage`, 형식·파일명 검증, `ProjectPermissionService` 문서 등록 권한, API, `FILE_TOO_LARGE`, multipart 설정, `DOCFLOW_STORAGE_DIR`(`.env.example`·`.gitignore`) + 단위·통합 테스트 (§10.2.1 DB 실패 보상 테스트 포함)
4-1. [ ] OOXML 검사 상한·PDF 최소 구조 검증의 정상 표본 측정 기록 (§5.5.4, §12.1)
5. [ ] CI 확인 (Backend)
6. [ ] Frontend: `api-client` FormData 지원, 문서 API·타입·검증, 목록 섹션·등록·상세 화면, `next.config.ts` `proxyClientMaxBodySize` + Vitest
7. [ ] Frontend lint·test·build (클라우드) → CI 확인
8. [ ] 실제 업로드 경로 검증 (§10.4)
9. [ ] 문서: README, `docs/architecture.md`, 이 SPEC §16 결과 기록
10. [ ] Windows 로컬 수동 검증 (사용자, §10.5)
11. [ ] PR (`Closes #15`), 병합은 사용자

## 16. 검증 결과

**미실행.** 설계 문서 단계이며 구현과 테스트는 시작하지 않았다.

| 항목 | Claude Code 클라우드 | GitHub Actions CI | Windows 로컬 (사용자) |
| --- | --- | --- | --- |
| Backend build·test | 미실행 | 미실행 | 미실행 |
| Frontend lint·test·build | 미실행 | 미실행 | 미실행 |
| 실제 업로드 경로 (§10.4) | 미실행 | — | 미실행 |
| 수동 E2E (§10.5) | — | — | 미실행 |

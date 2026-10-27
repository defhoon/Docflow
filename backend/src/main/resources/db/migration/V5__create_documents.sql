-- SPEC-03 (Issue #15): 문서와 문서 버전
-- 파일은 document_versions.storage_key가 가리키는 저장소에 있다. DB 행만 연쇄 삭제하면 파일이 남으므로
-- FK는 모두 RESTRICT로 둔다. 업무 규칙(20MiB 상한, 허용 형식, 제목 문자 규칙)은 애플리케이션이 판단하고
-- 아래 CHECK는 마지막 방어선이다.
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
    CONSTRAINT ck_documents_status
        CHECK (status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'RE_REVIEW', 'APPROVED'))
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

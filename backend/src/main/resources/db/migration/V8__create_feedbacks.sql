-- SPEC-08 (Issue #25): 버전별 Feedback. 상태·답글·좌표 없음. 수정·삭제 API 없음.
CREATE TABLE feedbacks (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_version_id  BIGINT        NOT NULL REFERENCES document_versions (id) ON DELETE RESTRICT,
    author_id            BIGINT        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    content              VARCHAR(2000) NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_feedbacks_content_not_blank CHECK (length(btrim(content)) > 0)
);

CREATE INDEX ix_feedbacks_version ON feedbacks (document_version_id, id);

-- SPEC-06 (Issue #23): 활동 이력(Audit Log). append-only이며 수정·삭제 API가 없다.
-- 업무 변경과 같은 트랜잭션에서 기록한다. version_number는 기록 시점의 스냅샷이다.
CREATE TABLE audit_logs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects (id) ON DELETE RESTRICT,
    actor_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    action          VARCHAR(40)  NOT NULL,
    target_type     VARCHAR(30)  NOT NULL,
    target_id       BIGINT       NOT NULL,
    document_id     BIGINT       REFERENCES documents (id) ON DELETE RESTRICT,
    version_number  INT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_logs_action
        CHECK (action IN ('DOCUMENT_CREATED', 'VERSION_CREATED', 'FEEDBACK_CREATED', 'DOCUMENT_FINALIZED')),
    CONSTRAINT ck_audit_logs_target_type
        CHECK (target_type IN ('DOCUMENT', 'DOCUMENT_VERSION', 'FEEDBACK')),
    CONSTRAINT ck_audit_logs_version_positive CHECK (version_number IS NULL OR version_number >= 1)
);

CREATE INDEX ix_audit_logs_project ON audit_logs (project_id, id DESC);
CREATE INDEX ix_audit_logs_document ON audit_logs (document_id, id DESC);

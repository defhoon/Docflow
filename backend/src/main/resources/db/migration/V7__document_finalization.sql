-- SPEC-07 (Issue #24): 문서 협업 상태(ACTIVE/FINALIZED)와 최종 버전 확정.

-- 1) 기존 상태 마이그레이션. APPROVED는 확정 버전·확정자 정보가 없어 FINALIZED로 변환할 수 없으므로 중단한다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM documents WHERE status = 'APPROVED') THEN
        RAISE EXCEPTION 'V7: documents with status APPROVED exist; resolve them manually before migrating (SPEC-07 §5)';
    END IF;
END
$$;

ALTER TABLE documents DROP CONSTRAINT ck_documents_status;
UPDATE documents SET status = 'ACTIVE'
 WHERE status IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'RE_REVIEW');
ALTER TABLE documents ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE documents ADD CONSTRAINT ck_documents_status CHECK (status IN ('ACTIVE', 'FINALIZED'));

-- 2) 확정 정보. 확정 버전은 같은 문서의 버전이어야 한다(복합 FK).
ALTER TABLE document_versions ADD CONSTRAINT uk_document_versions_id_document UNIQUE (id, document_id);

ALTER TABLE documents
    ADD COLUMN final_version_id BIGINT,
    ADD COLUMN finalized_by     BIGINT REFERENCES users (id) ON DELETE RESTRICT,
    ADD COLUMN finalized_at     TIMESTAMPTZ,
    ADD CONSTRAINT fk_documents_final_version
        FOREIGN KEY (final_version_id, id) REFERENCES document_versions (id, document_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_documents_status_finalization CHECK ( -- 이름 순 평가: 상태 CHECK가 먼저 보고된다
        (status = 'ACTIVE' AND final_version_id IS NULL AND finalized_by IS NULL AND finalized_at IS NULL)
        OR (status = 'FINALIZED' AND final_version_id IS NOT NULL AND finalized_by IS NOT NULL AND finalized_at IS NOT NULL)
    );

-- 3) FINALIZED는 영구 상태: 상태·확정 정보 변경을 DB에서도 거부한다(마지막 방어선).
CREATE FUNCTION documents_finalized_immutable() RETURNS trigger AS $$
BEGIN
    IF OLD.status = 'FINALIZED' AND (
           NEW.status IS DISTINCT FROM OLD.status
        OR NEW.final_version_id IS DISTINCT FROM OLD.final_version_id
        OR NEW.finalized_by IS DISTINCT FROM OLD.finalized_by
        OR NEW.finalized_at IS DISTINCT FROM OLD.finalized_at) THEN
        RAISE EXCEPTION 'finalized document % is immutable', OLD.id USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_documents_finalized_immutable
    BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION documents_finalized_immutable();

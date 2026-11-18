-- SPEC-09 (Issue #49): 문서 설명 확대와 버전별 정보(version note).
-- 기존 행은 바꾸지 않는다. 모든 변경은 기존 데이터와 호환된다.
--   1) documents.description: VARCHAR(1000) → VARCHAR(2000). PostgreSQL에서 VARCHAR 길이 확대는 테이블 재작성 없이
--      메타데이터만 바뀐다. 기존 값은 그대로 유지된다.
--   2) document_versions.version_note: 기본값 없는 NULL 허용 열 추가(재작성 없음). 기존 버전은 NULL(정보 없음)이다.
--      업로드 시 한 번 저장하고 수정하지 않는다.
--   3) 활동 이력에 DOCUMENT_DESCRIPTION_UPDATED 추가. 기존 행은 모두 새 CHECK를 만족한다.
ALTER TABLE documents ALTER COLUMN description TYPE VARCHAR(2000);

ALTER TABLE document_versions ADD COLUMN version_note VARCHAR(1000);

ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_action
    CHECK (action IN ('DOCUMENT_CREATED', 'VERSION_CREATED', 'FEEDBACK_CREATED', 'DOCUMENT_FINALIZED',
                      'DOCUMENT_DESCRIPTION_UPDATED'));

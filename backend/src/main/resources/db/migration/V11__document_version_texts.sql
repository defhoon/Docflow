-- SPEC-10 (Issue #53): 버전별 PDF 텍스트·좌표 추출 결과.
-- 새 테이블만 추가한다. 기존 테이블·행은 바꾸지 않는다.
-- 추출 결과는 원본 파일에서 언제든 다시 만들 수 있는 파생 데이터다. 버전은 수정되지 않으므로(SPEC-04) 버전당 한 행이며
-- extractor_version이 바뀌면 다시 추출한다. content는 gzip으로 압축한 JSON(EXTRACTED일 때만)이다.
CREATE TABLE document_version_texts (
    version_id        BIGINT       PRIMARY KEY REFERENCES document_versions (id) ON DELETE CASCADE,
    status            VARCHAR(20)  NOT NULL,
    extractor_version INTEGER      NOT NULL,
    page_count        INTEGER      NOT NULL,
    word_count        INTEGER      NOT NULL,
    char_count        INTEGER      NOT NULL,
    content           BYTEA,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_version_texts_status
        CHECK (status IN ('EXTRACTED', 'NO_TEXT', 'ENCRYPTED', 'CORRUPTED', 'TOO_LARGE', 'TIMEOUT', 'UNSUPPORTED_TYPE')),
    CONSTRAINT ck_document_version_texts_content
        CHECK ((status = 'EXTRACTED') = (content IS NOT NULL))
);

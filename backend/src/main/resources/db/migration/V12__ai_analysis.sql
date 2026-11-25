-- SPEC-11 (Issue #54): 프로젝트 단위 AI 기능 설정과 AI 분석 작업.
-- 기존 행은 바꾸지 않는다. projects에 기본값이 있는 열을 추가하므로 기존 프로젝트는 모두 AI 꺼짐(false)이다.
ALTER TABLE projects ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- 마지막으로 켠 시각·사용자. 켤 때 외부 전송 고지에 동의한 기록이다(동의 없이는 켤 수 없다).
ALTER TABLE projects ADD COLUMN ai_enabled_at TIMESTAMPTZ;
ALTER TABLE projects ADD COLUMN ai_enabled_by BIGINT REFERENCES users (id);

-- 분석 작업. SUMMARY(버전 요약)와 CHANGE_SUMMARY(두 버전 변경 요약, SPEC-14)가 같은 구조를 쓴다.
CREATE TABLE document_analyses (
    id              BIGSERIAL    PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects (id),
    document_id     BIGINT       NOT NULL REFERENCES documents (id),
    version_id      BIGINT       NOT NULL REFERENCES document_versions (id),
    -- 비교 기준 버전. SUMMARY는 NULL이다.
    base_version_id BIGINT       REFERENCES document_versions (id),
    analysis_type   VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    model           VARCHAR(100),
    api_calls       INTEGER      NOT NULL DEFAULT 0,
    input_tokens    BIGINT       NOT NULL DEFAULT 0,
    output_tokens   BIGINT       NOT NULL DEFAULT 0,
    -- 추정 비용(USD × 1,000,000). 설정한 단가로 계산한 값이며 청구 금액이 아니다.
    cost_micros     BIGINT       NOT NULL DEFAULT 0,
    result          JSONB,
    error_code      VARCHAR(40),
    requested_by    BIGINT       REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_analyses_type CHECK (analysis_type IN ('SUMMARY', 'CHANGE_SUMMARY')),
    CONSTRAINT ck_document_analyses_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_document_analyses_base CHECK ((analysis_type = 'SUMMARY') = (base_version_id IS NULL)),
    CONSTRAINT ck_document_analyses_result CHECK ((status = 'COMPLETED') = (result IS NOT NULL))
);

-- 같은 분석은 하나만: PostgreSQL 고유 제약은 NULL을 서로 다른 값으로 보므로 기준 버전 NULL을 0으로 바꿔 비교한다.
CREATE UNIQUE INDEX uk_document_analyses_target
    ON document_analyses (version_id, analysis_type, COALESCE(base_version_id, 0));
-- 작업자 대기열 조회
CREATE INDEX ix_document_analyses_queue ON document_analyses (status, next_attempt_at);
-- 비용·호출 상한 집계
CREATE INDEX ix_document_analyses_usage ON document_analyses (project_id, updated_at);

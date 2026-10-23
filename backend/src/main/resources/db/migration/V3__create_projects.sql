-- SPEC-01 (Issue #6): 프로젝트와 Owner
-- 이름·설명의 정규화와 허용 문자 검증은 애플리케이션에서 수행한다 (specs/01-project-create.md §5.5).
-- CHECK 제약은 애플리케이션을 거치지 않는 직접 INSERT에 대한 최소 방어선이다.
-- btrim()은 기본적으로 ASCII 스페이스(U+0020)만 제거한다.
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

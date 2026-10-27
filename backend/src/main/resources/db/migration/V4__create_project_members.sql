-- SPEC-02 (Issue #13): 프로젝트 구성원(MEMBER)
-- Owner는 projects.owner_id로 관리하며 이 테이블에 저장하지 않는다(애플리케이션에서 보장).
-- 중복 추가는 UNIQUE 제약이 최종적으로 막는다. 애플리케이션은 이 제약 이름으로 409를 판단한다.
CREATE TABLE project_members (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  BIGINT      NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id)
);

-- 내가 구성원으로 참여한 프로젝트 조회용
CREATE INDEX ix_project_members_user ON project_members (user_id);

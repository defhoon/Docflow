-- SPEC-AUTH 확장 (Issue #26): 초대코드 기반 회원가입과 운영 관리자.
-- 초대코드 원문은 저장하지 않는다. code_hash = SHA-256(code) 16진수. 코드는 128비트 난수라 느린 해시가 필요 없다.
ALTER TABLE users ADD COLUMN system_role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT ck_users_system_role CHECK (system_role IN ('USER', 'ADMIN'));

CREATE TABLE invitations (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code_hash     CHAR(64)     NOT NULL,
    grants_admin  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by    BIGINT       REFERENCES users (id) ON DELETE RESTRICT,
    note          VARCHAR(100),
    expires_at    TIMESTAMPTZ  NOT NULL,
    used_at       TIMESTAMPTZ,
    used_by       BIGINT       REFERENCES users (id) ON DELETE RESTRICT,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_invitations_code_hash UNIQUE (code_hash),
    CONSTRAINT uk_invitations_used_by UNIQUE (used_by),
    CONSTRAINT ck_invitations_code_hash CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_invitations_used CHECK ((used_at IS NULL) = (used_by IS NULL)),
    CONSTRAINT ck_invitations_not_used_and_revoked CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX ix_invitations_created ON invitations (id DESC);

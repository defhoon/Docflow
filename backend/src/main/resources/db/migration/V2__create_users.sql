-- SPEC-AUTH (Issue #4): 사용자 계정
-- 이메일은 애플리케이션에서 trim + 소문자로 정규화하여 저장하고,
-- lower(email) 유니크 인덱스로 대소문자만 다른 중복 계정을 DB 수준에서도 차단한다.
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_users_email ON users (lower(email));

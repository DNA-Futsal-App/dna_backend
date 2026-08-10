CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    child_instagram VARCHAR(64),
    followed_category_id VARCHAR(100),
    followed_division_id VARCHAR(100),
    followed_team_id VARCHAR(100),
    status VARCHAR(40) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_accounts_email UNIQUE (email),
    CONSTRAINT uk_user_accounts_phone UNIQUE (phone),
    CONSTRAINT ck_user_accounts_status CHECK (status IN ('PENDING_EMAIL_VERIFICATION', 'ACTIVE', 'LOCKED'))
);

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_email_verification_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_email_verification_user ON email_verification_tokens(user_id, created_at DESC);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_password_reset_user_day ON password_reset_tokens(user_id, created_at DESC);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    user_agent VARCHAR(300),
    ip_address VARCHAR(64),
    CONSTRAINT uk_refresh_session_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_sessions_user ON refresh_sessions(user_id, revoked_at, expires_at);

CREATE TABLE mail_outbox (
    id UUID PRIMARY KEY,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    html_body TEXT,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    CONSTRAINT ck_mail_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SENT', 'DEAD'))
);
CREATE INDEX idx_mail_outbox_dispatch ON mail_outbox(status, next_attempt_at, created_at);

CREATE TABLE processed_sports_events (
    event_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

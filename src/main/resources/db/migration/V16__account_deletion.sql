ALTER TABLE user_accounts DROP CONSTRAINT ck_user_accounts_status;
ALTER TABLE user_accounts
    ADD COLUMN deletion_requested_at TIMESTAMPTZ,
    ADD COLUMN deletion_scheduled_for TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN phone DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD CONSTRAINT ck_user_accounts_status CHECK (status IN
        ('PENDING_EMAIL_VERIFICATION','ACTIVE','LOCKED','PENDING_DELETION','DELETING','DELETED')),
    ADD CONSTRAINT ck_user_accounts_personal_data CHECK (
        (status IN ('DELETING','DELETED') AND email IS NULL AND phone IS NULL AND password_hash IS NULL)
        OR (status NOT IN ('DELETING','DELETED') AND email IS NOT NULL AND phone IS NOT NULL AND password_hash IS NOT NULL)),
    ADD CONSTRAINT ck_user_accounts_deletion_schedule CHECK (
        status NOT IN ('PENDING_DELETION','DELETING','DELETED')
        OR (deletion_requested_at IS NOT NULL AND deletion_scheduled_for IS NOT NULL));
CREATE INDEX idx_user_accounts_deletion_due ON user_accounts(deletion_scheduled_for)
    WHERE status = 'PENDING_DELETION';

CREATE TABLE account_deletion_attempts (
    identifier_hash VARCHAR(64) PRIMARY KEY,
    attempts INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE account_deletion_cleanup (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    resource_type VARCHAR(10) NOT NULL CHECK (resource_type IN ('OBJECT','PAR')),
    resource_key VARCHAR(700) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id, resource_type, resource_key)
);
CREATE INDEX idx_account_deletion_cleanup_due ON account_deletion_cleanup(next_attempt_at);

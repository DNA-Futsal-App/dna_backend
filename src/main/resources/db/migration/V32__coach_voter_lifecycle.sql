-- Preserve recoverable voting contexts without restoring votes or unrelated revoked categories.
ALTER TABLE award_coach_credentials ADD COLUMN removed_at TIMESTAMPTZ;

CREATE TABLE award_coach_credential_restore_contexts (
    credential_id UUID NOT NULL REFERENCES award_coach_credentials(id) ON DELETE CASCADE,
    context_id UUID NOT NULL REFERENCES award_coach_voters(id) ON DELETE CASCADE,
    application_id UUID REFERENCES award_coach_category_applications(id) ON DELETE SET NULL,
    PRIMARY KEY (credential_id, context_id)
);

ALTER TABLE award_coach_revocation_audit
    DROP CONSTRAINT IF EXISTS award_coach_revocation_audit_action_check;
ALTER TABLE award_coach_revocation_audit
    ADD CONSTRAINT ck_award_coach_revocation_audit_action
    CHECK (action IN ('CATEGORY_REVOKED','COACH_REVOKED','COACH_REACTIVATED','COACH_REMOVED'));

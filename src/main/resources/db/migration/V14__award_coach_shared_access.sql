CREATE TABLE award_coach_access_links (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
    created_by_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uk_award_coach_access_link_active ON award_coach_access_links(edition_id) WHERE status='ACTIVE';

CREATE TABLE award_coach_credentials (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    access_link_id UUID REFERENCES award_coach_access_links(id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_award_coach_credential_user UNIQUE(edition_id,user_id)
);

ALTER TABLE award_coach_voters ALTER COLUMN invite_id DROP NOT NULL;
ALTER TABLE award_coach_voters ADD COLUMN credential_id UUID REFERENCES award_coach_credentials(id) ON DELETE CASCADE;
ALTER TABLE award_coach_voters ADD COLUMN context_source VARCHAR(20) NOT NULL DEFAULT 'MIGRATED';
ALTER TABLE award_coach_voters ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO award_coach_credentials(id,edition_id,user_id,access_link_id,active,created_at)
SELECT id,edition_id,user_id,NULL,TRUE,created_at FROM award_coach_voters
ON CONFLICT (edition_id,user_id) DO NOTHING;

UPDATE award_coach_voters v SET credential_id=c.id,context_source='MIGRATED',active=TRUE
FROM award_coach_credentials c WHERE c.edition_id=v.edition_id AND c.user_id=v.user_id AND v.credential_id IS NULL;

ALTER TABLE award_coach_voters DROP CONSTRAINT IF EXISTS uk_award_coach_voter_user;
ALTER TABLE award_coach_voters DROP CONSTRAINT IF EXISTS uk_award_coach_voter_candidate;
ALTER TABLE award_coach_voters ADD CONSTRAINT ck_award_coach_voter_context_source CHECK(context_source IN ('SELF_SELECTED','ADMIN','MIGRATED'));
CREATE INDEX idx_award_coach_voters_credential ON award_coach_voters(credential_id,active,created_at);
CREATE UNIQUE INDEX uk_award_coach_voter_active_context ON award_coach_voters(credential_id,event_id,division_id,category_id,team_id) WHERE active=TRUE AND credential_id IS NOT NULL;

ALTER TABLE award_ballots DROP CONSTRAINT IF EXISTS uk_award_ballot_user;
UPDATE award_coach_invites SET status='REVOKED' WHERE status='PENDING';

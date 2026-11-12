-- Phase 2: category-scoped coach invitations. Existing edition-wide links are kept
-- for backward compatibility until the registration migration (phase 3).
CREATE TABLE award_coach_category_links (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE RESTRICT,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_coach_category_link_dates CHECK (expires_at > created_at)
);

-- One ACTIVE link per competitive category; expired links remain historical until revoked.
CREATE UNIQUE INDEX uk_award_coach_category_link_active
    ON award_coach_category_links(edition_id, event_id, division_id, category_id, gender)
    WHERE status='ACTIVE';

CREATE INDEX idx_award_coach_category_links_edition
    ON award_coach_category_links(edition_id, created_at DESC);

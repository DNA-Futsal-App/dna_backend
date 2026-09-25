CREATE SEQUENCE award_registration_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE award_registrations (
    id UUID PRIMARY KEY,
    registration_number BIGINT NOT NULL UNIQUE,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE CASCADE,
    representative_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    representative_cpf_encrypted VARCHAR(512) NOT NULL,
    athlete_name VARCHAR(150) NOT NULL,
    athlete_instagram VARCHAR(64) NOT NULL,
    athlete_instagram_normalized VARCHAR(64) NOT NULL,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    division_name VARCHAR(120) NOT NULL,
    category_id BIGINT NOT NULL,
    category_name VARCHAR(120) NOT NULL,
    team_id VARCHAR(100) NOT NULL,
    team_name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_award_registration_user UNIQUE (edition_id, representative_user_id),
    CONSTRAINT uk_award_registration_athlete UNIQUE (edition_id, athlete_instagram_normalized),
    CONSTRAINT ck_award_registration_status CHECK (status IN ('DRAFT','SUBMITTED','CANCELLED'))
);

CREATE INDEX idx_award_registration_context
    ON award_registrations (edition_id, division_id, category_id, team_id, registration_number);

CREATE TABLE award_registration_entries (
    id UUID PRIMARY KEY,
    registration_id UUID NOT NULL REFERENCES award_registrations(id) ON DELETE CASCADE,
    contest_category VARCHAR(40) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    media_status VARCHAR(20) NOT NULL,
    external_url VARCHAR(2000),
    object_name VARCHAR(700),
    display_filename VARCHAR(700),
    pending_object_name VARCHAR(700),
    pending_par_id VARCHAR(500),
    duration_ms BIGINT,
    width INTEGER,
    height INTEGER,
    file_size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_award_registration_entry_category UNIQUE (registration_id, contest_category),
    CONSTRAINT ck_award_registration_entry_category CHECK (
      contest_category IN ('BEAUTIFUL_GOAL','BEST_DRIBBLE','FREE_KICK_GOAL','BEST_SAVE')
    ),
    CONSTRAINT ck_award_registration_entry_source CHECK (source_type IN ('LINK','UPLOAD')),
    CONSTRAINT ck_award_registration_entry_media_status CHECK (media_status IN ('PENDING','READY'))
);

CREATE INDEX idx_award_registration_entries_registration
    ON award_registration_entries (registration_id, created_at);

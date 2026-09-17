ALTER TABLE user_accounts
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_accounts_role
        CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE award_candidates
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_key VARCHAR(120),
    ADD COLUMN secondary_name VARCHAR(150),
    ADD COLUMN source_role VARCHAR(120),
    ADD COLUMN imported_at TIMESTAMPTZ;

ALTER TABLE award_candidates
    ADD CONSTRAINT ck_award_candidate_source
        CHECK (source IN ('MANUAL', 'SCRAPER'));

CREATE UNIQUE INDEX uk_award_candidate_source_key
    ON award_candidates (edition_id, source, source_key)
    WHERE source_key IS NOT NULL;

CREATE INDEX idx_award_candidates_team_source
    ON award_candidates (
        edition_id,
        event_id,
        team_id,
        source,
        active
    );

CREATE TABLE award_vote_categories (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL
        REFERENCES award_editions(id)
        ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    position_code VARCHAR(50),
    display_order INTEGER NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_award_vote_category
        UNIQUE (edition_id, code),
    CONSTRAINT ck_award_vote_category_target
        CHECK (target_type IN ('ATHLETE', 'COACH'))
);

INSERT INTO award_editions (
    id,
    slug,
    name,
    season,
    status,
    voting_opens_at,
    voting_closes_at,
    created_at
)
VALUES (
    '2b31a2c2-40a5-4e37-9d98-0d21caa6e001',
    'premio-dna-2026',
    'Prêmio DNA Futsal 2026',
    2026,
    'DRAFT',
    NULL,
    NULL,
    CURRENT_TIMESTAMP
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO award_vote_categories (
    id,
    edition_id,
    code,
    label,
    target_type,
    position_code,
    display_order,
    required,
    created_at
)
SELECT
    values_row.id,
    edition.id,
    values_row.code,
    values_row.label,
    values_row.target_type,
    values_row.position_code,
    values_row.display_order,
    TRUE,
    CURRENT_TIMESTAMP
FROM award_editions edition
CROSS JOIN (
    VALUES
        (
            '2b31a2c2-40a5-4e37-9d98-0d21caa6e010'::UUID,
            'GOLEIRO',
            'Goleiro',
            'ATHLETE',
            'GOLEIRO',
            10
        ),
        (
            '2b31a2c2-40a5-4e37-9d98-0d21caa6e020'::UUID,
            'FIXO',
            'Fixo',
            'ATHLETE',
            'FIXO',
            20
        ),
        (
            '2b31a2c2-40a5-4e37-9d98-0d21caa6e030'::UUID,
            'ALA',
            'Ala',
            'ATHLETE',
            'ALA',
            30
        ),
        (
            '2b31a2c2-40a5-4e37-9d98-0d21caa6e040'::UUID,
            'PIVO',
            'Pivô',
            'ATHLETE',
            'PIVO',
            40
        ),
        (
            '2b31a2c2-40a5-4e37-9d98-0d21caa6e050'::UUID,
            'TECNICO',
            'Técnico',
            'COACH',
            NULL,
            50
        )
) AS values_row(
    id,
    code,
    label,
    target_type,
    position_code,
    display_order
)
WHERE edition.slug = 'premio-dna-2026'
ON CONFLICT (edition_id, code) DO NOTHING;

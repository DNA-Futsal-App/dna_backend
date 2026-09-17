CREATE TABLE award_editions (
                                id UUID PRIMARY KEY,
                                slug VARCHAR(100) NOT NULL,
                                name VARCHAR(150) NOT NULL,
                                season INTEGER NOT NULL,
                                status VARCHAR(20) NOT NULL,
                                voting_opens_at TIMESTAMPTZ,
                                voting_closes_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ NOT NULL,

                                CONSTRAINT uk_award_editions_slug UNIQUE (slug),
                                CONSTRAINT ck_award_editions_status
                                    CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
);

CREATE TABLE award_categories (
                                  id UUID PRIMARY KEY,
                                  edition_id UUID NOT NULL
                                      REFERENCES award_editions(id) ON DELETE CASCADE,

                                  code VARCHAR(50) NOT NULL,
                                  label VARCHAR(100) NOT NULL,

                                  target_type VARCHAR(20) NOT NULL,
                                  position_code VARCHAR(50),

                                  display_order INTEGER NOT NULL,
                                  required BOOLEAN NOT NULL DEFAULT TRUE,

                                  created_at TIMESTAMPTZ NOT NULL,

                                  CONSTRAINT uk_award_category_code
                                      UNIQUE (edition_id, code),

                                  CONSTRAINT ck_award_category_target_type
                                      CHECK (target_type IN ('ATHLETE', 'COACH'))
);

CREATE INDEX idx_award_categories_edition
    ON award_categories(edition_id, display_order);


CREATE TABLE award_candidates (
                                  id UUID PRIMARY KEY,

                                  edition_id UUID NOT NULL
                                      REFERENCES award_editions(id) ON DELETE CASCADE,

                                  candidate_type VARCHAR(20) NOT NULL,

                                  external_person_id VARCHAR(120),

                                  name VARCHAR(150) NOT NULL,

                                  position_code VARCHAR(50),

                                  event_id BIGINT NOT NULL,
                                  division_id BIGINT NOT NULL,
                                  category_id BIGINT NOT NULL,

                                  team_id VARCHAR(100) NOT NULL,
                                  team_name VARCHAR(150) NOT NULL,
                                  team_logo_url VARCHAR(500),

                                  image_url VARCHAR(500),

                                  active BOOLEAN NOT NULL DEFAULT TRUE,

                                  created_at TIMESTAMPTZ NOT NULL,

                                  CONSTRAINT ck_award_candidate_type
                                      CHECK (candidate_type IN ('ATHLETE', 'COACH'))
);

CREATE INDEX idx_award_candidates_context
    ON award_candidates(
                        edition_id,
                        division_id,
                        category_id,
                        candidate_type,
                        position_code,
                        team_id
        );


CREATE TABLE award_coach_invites (
                                     id UUID PRIMARY KEY,

                                     edition_id UUID NOT NULL
                                         REFERENCES award_editions(id) ON DELETE CASCADE,

                                     coach_candidate_id UUID NOT NULL
                                         REFERENCES award_candidates(id),

                                     token_hash VARCHAR(64) NOT NULL,

                                     expires_at TIMESTAMPTZ NOT NULL,

                                     status VARCHAR(20) NOT NULL,

                                     claimed_by_user_id UUID
                                         REFERENCES user_accounts(id),

                                     claimed_at TIMESTAMPTZ,

                                     created_at TIMESTAMPTZ NOT NULL,

                                     CONSTRAINT uk_award_coach_invite_token
                                         UNIQUE (token_hash),

                                     CONSTRAINT ck_award_coach_invite_status
                                         CHECK (status IN ('PENDING', 'CLAIMED', 'REVOKED'))
);

CREATE INDEX idx_award_coach_invites_candidate
    ON award_coach_invites(
                           edition_id,
                           coach_candidate_id
        );


CREATE TABLE award_coach_voters (
                                    id UUID PRIMARY KEY,

                                    edition_id UUID NOT NULL
                                        REFERENCES award_editions(id) ON DELETE CASCADE,

                                    user_id UUID NOT NULL
                                        REFERENCES user_accounts(id) ON DELETE CASCADE,

                                    invite_id UUID NOT NULL
                                        REFERENCES award_coach_invites(id),

                                    self_coach_candidate_id UUID NOT NULL
                                        REFERENCES award_candidates(id),

                                    event_id BIGINT NOT NULL,
                                    division_id BIGINT NOT NULL,
                                    category_id BIGINT NOT NULL,
                                    team_id VARCHAR(100) NOT NULL,

                                    created_at TIMESTAMPTZ NOT NULL,

                                    CONSTRAINT uk_award_coach_voter_user
                                        UNIQUE (edition_id, user_id),

                                    CONSTRAINT uk_award_coach_voter_candidate
                                        UNIQUE (edition_id, self_coach_candidate_id),

                                    CONSTRAINT uk_award_coach_voter_invite
                                        UNIQUE (invite_id)
);

CREATE INDEX idx_award_coach_voters_context
    ON award_coach_voters(
                          edition_id,
                          division_id,
                          category_id
        );


CREATE TABLE award_ballots (
                               id UUID PRIMARY KEY,

                               edition_id UUID NOT NULL
                                   REFERENCES award_editions(id),

                               voter_user_id UUID NOT NULL
                                   REFERENCES user_accounts(id),

                               submitted_at TIMESTAMPTZ NOT NULL,

                               CONSTRAINT uk_award_ballot_voter
                                   UNIQUE (edition_id, voter_user_id)
);


CREATE TABLE award_ballot_votes (
                                    id UUID PRIMARY KEY,

                                    ballot_id UUID NOT NULL
                                        REFERENCES award_ballots(id) ON DELETE CASCADE,

                                    award_category_id UUID NOT NULL
                                        REFERENCES award_categories(id),

                                    candidate_id UUID NOT NULL
                                        REFERENCES award_candidates(id),

                                    created_at TIMESTAMPTZ NOT NULL,

                                    CONSTRAINT uk_award_ballot_category
                                        UNIQUE (ballot_id, award_category_id)
);

CREATE INDEX idx_award_ballot_votes_candidate
    ON award_ballot_votes(candidate_id);
CREATE TABLE sports_event_monitor_state (
                                            event_id BIGINT PRIMARY KEY,
                                            initialized_at TIMESTAMPTZ NOT NULL,
                                            last_checked_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sports_match_state (
                                    id UUID PRIMARY KEY,

                                    event_id BIGINT NOT NULL,
                                    match_id VARCHAR(180) NOT NULL,

                                    home_team_id VARCHAR(100),
                                    away_team_id VARCHAR(100),

                                    scheduled_date DATE,
                                    scheduled_at TIMESTAMPTZ,

                                    status VARCHAR(40) NOT NULL,

                                    home_score INTEGER,
                                    away_score INTEGER,

                                    first_seen_at TIMESTAMPTZ NOT NULL,
                                    last_seen_at TIMESTAMPTZ NOT NULL,

                                    CONSTRAINT uk_sports_match_state_event_match
                                        UNIQUE (event_id, match_id),

                                    CONSTRAINT ck_sports_match_state_status
                                        CHECK (
                                            status IN (
                                                       'SCHEDULED',
                                                       'FINISHED',
                                                       'RESULT_PENDING'
                                                )
                                            )
);

CREATE INDEX idx_sports_match_state_event
    ON sports_match_state(event_id);

CREATE TABLE sports_change_events (
                                      id UUID PRIMARY KEY,

                                      type VARCHAR(50) NOT NULL,

                                      event_id BIGINT NOT NULL,
                                      match_id VARCHAR(180) NOT NULL,

                                      home_team_id VARCHAR(100),
                                      away_team_id VARCHAR(100),

                                      previous_scheduled_date DATE,
                                      scheduled_date DATE,

                                      previous_scheduled_at TIMESTAMPTZ,
                                      scheduled_at TIMESTAMPTZ,

                                      previous_home_score INTEGER,
                                      previous_away_score INTEGER,

                                      home_score INTEGER,
                                      away_score INTEGER,

                                      occurred_at TIMESTAMPTZ NOT NULL,
                                      created_at TIMESTAMPTZ NOT NULL,

                                      processed_at TIMESTAMPTZ,

                                      CONSTRAINT ck_sports_change_event_type
                                          CHECK (
                                              type IN (
                                                       'MATCH_ADDED',
                                                       'MATCH_SCHEDULE_CHANGED',
                                                       'MATCH_RESULT_PUBLISHED',
                                                       'MATCH_RESULT_CORRECTED'
                                                  )
                                              )
);

CREATE INDEX idx_sports_change_events_unprocessed
    ON sports_change_events(
                            processed_at,
                            created_at
        );

CREATE INDEX idx_sports_change_events_match
    ON sports_change_events(
                            event_id,
                            match_id,
                            created_at
        );
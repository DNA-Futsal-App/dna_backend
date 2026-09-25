ALTER TABLE notification_preferences
    ADD COLUMN community_score_updates BOOLEAN NOT NULL DEFAULT TRUE;


CREATE TABLE community_score_reports (
                                         id UUID PRIMARY KEY,

                                         source VARCHAR(30) NOT NULL,
                                         source_update_id VARCHAR(120) NOT NULL,

                                         source_chat_id VARCHAR(120) NOT NULL,
                                         source_message_id VARCHAR(120),

                                         raw_text VARCHAR(600) NOT NULL,

                                         season INTEGER NOT NULL,

                                         division_id BIGINT NOT NULL,
                                         division_name VARCHAR(100) NOT NULL,

                                         category_id BIGINT NOT NULL,
                                         category_name VARCHAR(150) NOT NULL,

                                         event_id BIGINT NOT NULL,

                                         home_team_id VARCHAR(100) NOT NULL,
                                         home_team_name VARCHAR(180) NOT NULL,

                                         away_team_id VARCHAR(100) NOT NULL,
                                         away_team_name VARCHAR(180) NOT NULL,

                                         home_score INTEGER NOT NULL,
                                         away_score INTEGER NOT NULL,

                                         match_date DATE NOT NULL,

                                         reported_at TIMESTAMPTZ NOT NULL,
                                         received_at TIMESTAMPTZ NOT NULL,

                                         CONSTRAINT uk_community_score_source_update
                                             UNIQUE (
                                                     source,
                                                     source_update_id
                                                 ),

                                         CONSTRAINT ck_community_score_home
                                             CHECK (
                                                 home_score >= 0
                                                     AND home_score <= 99
                                                 ),

                                         CONSTRAINT ck_community_score_away
                                             CHECK (
                                                 away_score >= 0
                                                     AND away_score <= 99
                                                 )
);


CREATE INDEX idx_community_score_event
    ON community_score_reports (
                                event_id,
                                match_date
        );


CREATE TABLE community_live_match_state (
                                            id UUID PRIMARY KEY,

                                            event_id BIGINT NOT NULL,

                                            home_team_id VARCHAR(100) NOT NULL,
                                            away_team_id VARCHAR(100) NOT NULL,

                                            match_date DATE NOT NULL,

                                            home_score INTEGER NOT NULL,
                                            away_score INTEGER NOT NULL,

                                            last_report_id UUID NOT NULL
                                                REFERENCES community_score_reports(id),

                                            created_at TIMESTAMPTZ NOT NULL,
                                            updated_at TIMESTAMPTZ NOT NULL,

                                            CONSTRAINT uk_community_live_match
                                                UNIQUE (
                                                        event_id,
                                                        home_team_id,
                                                        away_team_id,
                                                        match_date
                                                    )
);


CREATE TABLE community_notification_events (
                                               id UUID PRIMARY KEY,

                                               report_id UUID NOT NULL
                                                   REFERENCES community_score_reports(id),

                                               type VARCHAR(50) NOT NULL,

                                               event_id BIGINT NOT NULL,

                                               home_team_id VARCHAR(100) NOT NULL,
                                               away_team_id VARCHAR(100) NOT NULL,

                                               previous_home_score INTEGER,
                                               previous_away_score INTEGER,

                                               home_score INTEGER NOT NULL,
                                               away_score INTEGER NOT NULL,

                                               match_date DATE NOT NULL,

                                               occurred_at TIMESTAMPTZ NOT NULL,
                                               created_at TIMESTAMPTZ NOT NULL,

                                               processed_at TIMESTAMPTZ,

                                               CONSTRAINT uk_community_notification_report
                                                   UNIQUE (report_id),

                                               CONSTRAINT ck_community_notification_type
                                                   CHECK (
                                                       type IN (
                                                                'COMMUNITY_SCORE_UPDATE',
                                                                'COMMUNITY_SCORE_CORRECTION'
                                                           )
                                                       )
);


CREATE INDEX idx_community_notification_pending
    ON community_notification_events (
                                      processed_at,
                                      created_at
        );


ALTER TABLE user_notifications
    ALTER COLUMN sports_change_event_id DROP NOT NULL;


ALTER TABLE user_notifications
    ADD COLUMN community_event_id UUID
        REFERENCES community_notification_events(id);


ALTER TABLE user_notifications
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'OFFICIAL';


ALTER TABLE user_notifications
DROP CONSTRAINT ck_user_notifications_type;


ALTER TABLE user_notifications
    ADD CONSTRAINT ck_user_notifications_type
        CHECK (
            type IN (
                     'MATCH_ADDED',
                     'MATCH_SCHEDULE_CHANGED',
                     'MATCH_RESULT_PUBLISHED',
                     'MATCH_RESULT_CORRECTED',
                     'COMMUNITY_SCORE_UPDATE',
                     'COMMUNITY_SCORE_CORRECTION'
                )
            );


ALTER TABLE user_notifications
    ADD CONSTRAINT ck_user_notification_source
        CHECK (
            (
                source = 'OFFICIAL'
                    AND sports_change_event_id IS NOT NULL
                    AND community_event_id IS NULL
                )
                OR
            (
                source = 'COMMUNITY'
                    AND sports_change_event_id IS NULL
                    AND community_event_id IS NOT NULL
                )
            );


CREATE UNIQUE INDEX uk_user_notification_community
    ON user_notifications (
                           user_id,
                           community_event_id
        )
    WHERE community_event_id IS NOT NULL;
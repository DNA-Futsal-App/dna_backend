CREATE TABLE notification_preferences (
                                          user_id UUID PRIMARY KEY
                                              REFERENCES user_accounts(id)
                                                  ON DELETE CASCADE,

                                          enabled BOOLEAN NOT NULL DEFAULT TRUE,

                                          match_added BOOLEAN NOT NULL DEFAULT TRUE,
                                          schedule_changed BOOLEAN NOT NULL DEFAULT TRUE,
                                          result_published BOOLEAN NOT NULL DEFAULT TRUE,
                                          result_corrected BOOLEAN NOT NULL DEFAULT TRUE,

                                          created_at TIMESTAMPTZ NOT NULL,
                                          updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_notifications (
                                    id BIGSERIAL PRIMARY KEY,

                                    user_id UUID NOT NULL
                                        REFERENCES user_accounts(id)
                                            ON DELETE CASCADE,

                                    sports_change_event_id UUID NOT NULL
                                        REFERENCES sports_change_events(id),

                                    type VARCHAR(50) NOT NULL,

                                    event_id BIGINT NOT NULL,
                                    match_id VARCHAR(180) NOT NULL,

                                    created_at TIMESTAMPTZ NOT NULL,
                                    read_at TIMESTAMPTZ,

                                    CONSTRAINT uk_user_notifications_user_event
                                        UNIQUE (
                                                user_id,
                                                sports_change_event_id
                                            ),

                                    CONSTRAINT ck_user_notifications_type
                                        CHECK (
                                            type IN (
                                                     'MATCH_ADDED',
                                                     'MATCH_SCHEDULE_CHANGED',
                                                     'MATCH_RESULT_PUBLISHED',
                                                     'MATCH_RESULT_CORRECTED'
                                                )
                                            )
);

CREATE INDEX idx_user_notifications_user_created
    ON user_notifications (
                           user_id,
                           created_at DESC
        );

CREATE INDEX idx_user_notifications_unread
    ON user_notifications (
                           user_id,
                           read_at,
                           created_at DESC
        );
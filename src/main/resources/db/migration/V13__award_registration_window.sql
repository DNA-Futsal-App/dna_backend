ALTER TABLE award_editions
    ADD COLUMN registrations_open BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN registrations_opened_at TIMESTAMPTZ,
    ADD COLUMN registrations_closed_at TIMESTAMPTZ;

UPDATE award_editions
SET registrations_opened_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE registrations_open = TRUE
  AND registrations_opened_at IS NULL;

ALTER TABLE award_editions
    ALTER COLUMN registrations_open SET DEFAULT FALSE;

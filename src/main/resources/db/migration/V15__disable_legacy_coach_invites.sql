UPDATE award_coach_invites
SET status = 'REVOKED'
WHERE status <> 'REVOKED';

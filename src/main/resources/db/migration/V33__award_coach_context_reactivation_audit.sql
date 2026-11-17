-- Keep revocation and reactivation auditable; no ballot or voter rows are modified.
ALTER TABLE award_coach_revocation_audit
    DROP CONSTRAINT ck_award_coach_revocation_audit_action;
ALTER TABLE award_coach_revocation_audit
    ADD CONSTRAINT ck_award_coach_revocation_audit_action
    CHECK (action IN ('CATEGORY_REVOKED', 'CATEGORY_REACTIVATED', 'COACH_REVOKED',
                     'COACH_REACTIVATED', 'COACH_REMOVED'));

package br.com.dnafutsal.backend.identity.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountDeletionProcessor {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MeterRegistry metrics;

    public AccountDeletionProcessor(JdbcTemplate jdbc, Clock clock, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public void process(UUID userId) {
        List<Map<String, Object>> accounts = jdbc.queryForList("""
                SELECT email FROM user_accounts
                WHERE id = ? AND status = 'PENDING_DELETION' AND deletion_scheduled_for <= ?
                FOR UPDATE SKIP LOCKED
                """, userId, Timestamp.from(clock.instant()));
        if (accounts.isEmpty()) {
            return;
        }
        List<Map<String, Object>> media = jdbc.queryForList("""
                SELECT entry.object_name, entry.pending_object_name, entry.pending_par_id
                FROM award_registration_entries entry
                JOIN award_registrations registration ON registration.id = entry.registration_id
                WHERE registration.representative_user_id = ?
                FOR UPDATE OF entry
                """, userId);
        for (Map<String, Object> resource : media) {
            enqueue(userId, "PAR", (String) resource.get("pending_par_id"));
            enqueue(userId, "OBJECT", (String) resource.get("object_name"));
            enqueue(userId, "OBJECT", (String) resource.get("pending_object_name"));
        }
        jdbc.update("DELETE FROM award_registrations WHERE representative_user_id = ?", userId);
        jdbc.update("""
                UPDATE award_coach_voters SET active = FALSE, credential_id = NULL, invite_id = NULL
                WHERE user_id = ?
                """, userId);
        jdbc.update("""
                DELETE FROM award_coach_voters voter WHERE user_id = ?
                AND NOT EXISTS (SELECT 1 FROM award_ballots ballot WHERE ballot.coach_voter_id = voter.id)
                """, userId);
        jdbc.update("DELETE FROM award_coach_credentials WHERE user_id = ?", userId);
        jdbc.update("""
                UPDATE award_coach_invites SET reserved_by_user_id = NULL, reserved_at = NULL,
                    reservation_expires_at = NULL WHERE reserved_by_user_id = ?
                """, userId);
        jdbc.update("""
                UPDATE award_coach_invites SET claimed_by_user_id = NULL, status = 'REVOKED'
                WHERE claimed_by_user_id = ?
                """, userId);
        jdbc.update("DELETE FROM user_notifications WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM notification_preferences WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM refresh_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM email_verification_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM mail_outbox WHERE recipient = ?", accounts.get(0).get("email"));
        jdbc.update("""
                UPDATE user_accounts SET status = 'DELETING', name = 'Conta excluída',
                    email = NULL, phone = NULL, password_hash = NULL, child_instagram = NULL,
                    followed_event_id = NULL, followed_category_id = NULL, followed_division_id = NULL,
                    followed_team_id = NULL, email_verified_at = NULL, role = 'USER',
                    token_version = token_version + 1, version = version + 1, updated_at = ?
                WHERE id = ?
                """, Timestamp.from(clock.instant()), userId);
        metrics.counter("account.deletion.data.removed").increment();
    }

    private void enqueue(UUID userId, String type, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        jdbc.update("""
                INSERT INTO account_deletion_cleanup(id, user_id, resource_type, resource_key, next_attempt_at)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), userId, type, key, Timestamp.from(clock.instant()));
    }

    @Transactional
    public void finish() {
        int completed = jdbc.update("""
                UPDATE user_accounts account SET status = 'DELETED', deleted_at = ?, updated_at = ?,
                    version = version + 1
                WHERE status = 'DELETING'
                AND NOT EXISTS (SELECT 1 FROM account_deletion_cleanup cleanup WHERE cleanup.user_id = account.id)
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        metrics.counter("account.deletion.completed").increment(completed);
        jdbc.update("DELETE FROM account_deletion_attempts WHERE window_started_at < ?",
                Timestamp.from(clock.instant().minusSeconds(900)));
    }
}

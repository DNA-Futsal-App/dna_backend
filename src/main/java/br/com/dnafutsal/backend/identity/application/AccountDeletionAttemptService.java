package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;

@Service
public class AccountDeletionAttemptService {

    private final JdbcTemplate jdbc;
    private final TokenSupport tokens;
    private final Clock clock;

    public AccountDeletionAttemptService(JdbcTemplate jdbc, TokenSupport tokens, Clock clock) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String identifier) {
        String key = tokens.hash("account-deletion:" + identifier);
        Timestamp now = Timestamp.from(clock.instant());
        Integer attempts = jdbc.queryForObject("""
                INSERT INTO account_deletion_attempts(identifier_hash, attempts, window_started_at)
                VALUES (?, 1, ?)
                ON CONFLICT(identifier_hash) DO UPDATE SET
                    attempts = CASE WHEN account_deletion_attempts.window_started_at <= ? - INTERVAL '15 minutes'
                        THEN 1 ELSE account_deletion_attempts.attempts + 1 END,
                    window_started_at = CASE WHEN account_deletion_attempts.window_started_at <= ? - INTERVAL '15 minutes'
                        THEN ? ELSE account_deletion_attempts.window_started_at END
                RETURNING attempts
                """, Integer.class, key, now, now, now, now);
        if (attempts != null && attempts > 7) {
            throw Errors.tooManyRequests("ACCOUNT_DELETION_RATE_LIMITED",
                    "Muitas tentativas. Aguarde 15 minutos e tente novamente.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeeded(String identifier) {
        jdbc.update("DELETE FROM account_deletion_attempts WHERE identifier_hash = ?",
                tokens.hash("account-deletion:" + identifier));
    }
}

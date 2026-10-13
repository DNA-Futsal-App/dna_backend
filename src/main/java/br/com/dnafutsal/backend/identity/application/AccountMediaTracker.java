package br.com.dnafutsal.backend.identity.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Service
public class AccountMediaTracker {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountMediaTracker(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void track(UUID userId, String type, String key) {
        if (key != null && !key.isBlank()) {
            jdbc.update("""
                    INSERT INTO account_deletion_cleanup(id, user_id, resource_type, resource_key, next_attempt_at)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, UUID.randomUUID(), userId, type, key, Timestamp.from(clock.instant()));
        }
    }
}

package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.awards.application.OracleAwardVideoStorage;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountDeletionCleanupProcessor {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionCleanupProcessor.class);
    private final JdbcTemplate jdbc;
    private final OracleAwardVideoStorage storage;
    private final Clock clock;
    private final MeterRegistry metrics;

    public AccountDeletionCleanupProcessor(JdbcTemplate jdbc, OracleAwardVideoStorage storage,
                                           Clock clock, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public void process(UUID taskId) {
        List<Map<String, Object>> tasks = jdbc.queryForList("""
                SELECT * FROM account_deletion_cleanup
                WHERE id = ? AND next_attempt_at <= ?
                AND EXISTS (SELECT 1 FROM user_accounts account WHERE account.id = user_id AND account.status = 'DELETING')
                FOR UPDATE SKIP LOCKED
                """, taskId, Timestamp.from(clock.instant()));
        if (tasks.isEmpty()) {
            return;
        }
        Map<String, Object> task = tasks.get(0);
        try {
            if ("PAR".equals(task.get("resource_type"))) {
                storage.deletePar((String) task.get("resource_key"));
            } else {
                Integer pendingAuthorizations = jdbc.queryForObject("""
                        SELECT count(*) FROM account_deletion_cleanup WHERE user_id = ? AND resource_type = 'PAR'
                        """, Integer.class, task.get("user_id"));
                if (pendingAuthorizations != null && pendingAuthorizations > 0) {
                    return;
                }
                storage.deleteObject((String) task.get("resource_key"));
            }
            jdbc.update("DELETE FROM account_deletion_cleanup WHERE id = ?", taskId);
        } catch (RuntimeException exception) {
            int failures = ((Number) task.get("attempts")).intValue() + 1;
            long delaySeconds = Math.min(3600, 60L << Math.min(failures - 1, 6));
            jdbc.update("UPDATE account_deletion_cleanup SET attempts = ?, next_attempt_at = ? WHERE id = ?",
                    failures, Timestamp.from(clock.instant().plusSeconds(delaySeconds)), taskId);
            metrics.counter("account.deletion.cleanup.failures").increment();
            if (failures >= 10) {
                log.error("Account deletion storage cleanup requires attention; attempts={}", failures);
            }
        }
    }
}

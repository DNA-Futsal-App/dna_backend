package br.com.dnafutsal.backend.identity.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
public class AccountDeletionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionDispatcher.class);
    private final JdbcTemplate jdbc;
    private final AccountDeletionProcessor processor;
    private final AccountDeletionCleanupProcessor cleanup;
    private final Clock clock;
    private final MeterRegistry metrics;

    public AccountDeletionDispatcher(JdbcTemplate jdbc, AccountDeletionProcessor processor,
                                     AccountDeletionCleanupProcessor cleanup, Clock clock, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.processor = processor;
        this.cleanup = cleanup;
        this.clock = clock;
        this.metrics = metrics;
        metrics.gauge("account.deletion.overdue", jdbc, database -> database.queryForObject("""
                SELECT count(*) FROM user_accounts WHERE status = 'PENDING_DELETION' AND deletion_scheduled_for <= ?
                """, Long.class, Timestamp.from(clock.instant())));
        metrics.gauge("account.deletion.cleanup.pending", jdbc, database ->
                database.queryForObject("SELECT count(*) FROM account_deletion_cleanup cleanup JOIN user_accounts account ON account.id = cleanup.user_id WHERE account.status = 'DELETING'", Long.class));
    }

    @Scheduled(fixedDelayString = "${app.account-deletion.delay:60000}")
    public void dispatch() {
        List<UUID> due = jdbc.queryForList("""
                SELECT id FROM user_accounts WHERE status = 'PENDING_DELETION' AND deletion_scheduled_for <= ?
                ORDER BY deletion_scheduled_for LIMIT 20
                """, UUID.class, Timestamp.from(clock.instant()));
        for (UUID userId : due) {
            try {
                processor.process(userId);
            } catch (RuntimeException exception) {
                metrics.counter("account.deletion.failures").increment();
                log.error("Account deletion transaction failed; retry scheduled");
            }
        }
        List<UUID> tasks = jdbc.queryForList("""
                SELECT id FROM account_deletion_cleanup WHERE next_attempt_at <= ?
                AND EXISTS (SELECT 1 FROM user_accounts account WHERE account.id = user_id AND account.status = 'DELETING')
                ORDER BY CASE WHEN resource_type = 'PAR' THEN 0 ELSE 1 END, next_attempt_at LIMIT 20
                """, UUID.class, Timestamp.from(clock.instant()));
        for (UUID taskId : tasks) {
            try {
                cleanup.process(taskId);
            } catch (RuntimeException exception) {
                log.error("Account deletion cleanup transaction failed; retry scheduled");
            }
        }
        processor.finish();
    }
}

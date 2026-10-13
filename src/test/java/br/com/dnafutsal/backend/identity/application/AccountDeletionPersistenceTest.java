package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.awards.application.OracleAwardVideoStorage;
import br.com.dnafutsal.backend.common.TokenSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class AccountDeletionPersistenceTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate transactions;
    private final Instant now = Instant.parse("2026-10-22T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final OracleAwardVideoStorage storage = mock(OracleAwardVideoStorage.class);
    private AccountDeletionProcessor processor;
    private AccountDeletionCleanupProcessor cleanup;
    private UUID userId;

    @BeforeAll
    static void migrate() {
        var datasource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(datasource).load().migrate();
        jdbc = new JdbcTemplate(datasource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE user_accounts CASCADE");
        jdbc.execute("TRUNCATE account_deletion_cleanup, account_deletion_attempts, mail_outbox");
        processor = new AccountDeletionProcessor(jdbc, clock, metrics);
        cleanup = new AccountDeletionCleanupProcessor(jdbc, storage, clock, metrics);
        userId = UUID.randomUUID();
        insertUser(userId, "owner@example.com", "+5511999999999", "PENDING_DELETION");
    }

    @Test
    void removesAllRegistrationsButPreservesBallotsAndOtherAccounts() {
        UUID other = UUID.randomUUID();
        insertUser(other, "other@example.com", "+5511888888888", "ACTIVE");
        UUID edition = insertEdition();
        UUID anotherEdition = insertEdition();
        UUID registration = insertRegistration(userId, edition, "DRAFT");
        insertRegistration(userId, anotherEdition, "SUBMITTED");
        UUID otherRegistration = insertRegistration(other, edition, "SUBMITTED");
        jdbc.update("""
                INSERT INTO award_registration_entries
                (id, registration_id, contest_category, source_type, media_status, object_name,
                 pending_object_name, pending_par_id, created_at, updated_at)
                VALUES (?, ?, 'BEAUTIFUL_GOAL', 'UPLOAD', 'READY', 'video', 'temporary', 'authorization', ?, ?)
                """, UUID.randomUUID(), registration, timestamp(), timestamp());
        UUID candidate = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO award_candidates
                (id, edition_id, candidate_type, name, event_id, division_id, category_id, team_id, team_name, created_at)
                VALUES (?, ?, 'COACH', 'Treinador', 1, 1, 1, 'team', 'Time', ?)
                """, candidate, edition, timestamp());
        UUID voter = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO award_coach_voters
                (id, edition_id, user_id, self_coach_candidate_id, event_id, division_id, category_id, team_id, created_at)
                VALUES (?, ?, ?, ?, 1, 1, 1, 'team', ?)
                """, voter, edition, userId, candidate, timestamp());
        UUID ballot = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO award_ballots (id, edition_id, coach_voter_id, voter_user_id, submitted_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ballot, edition, voter, userId, timestamp(), timestamp());
        UUID category = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO award_vote_categories (id, edition_id, code, label, target_type, display_order, created_at)
                VALUES (?, ?, 'COACH', 'Treinador', 'COACH', 1, ?)
                """, category, edition, timestamp());
        UUID vote = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO award_ballot_votes (id, ballot_id, award_vote_category_id, candidate_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, vote, ballot, category, candidate, timestamp());
        var before = jdbc.queryForList("SELECT * FROM award_ballot_votes WHERE id = ?", vote);
        transactions.executeWithoutResult(status -> processor.process(userId));
        assertThat(count("SELECT count(*) FROM award_registrations WHERE representative_user_id = ?", userId)).isZero();
        assertThat(count("SELECT count(*) FROM award_registrations WHERE id = ?", otherRegistration)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM award_ballot_votes WHERE id = ?", vote)).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM award_ballots WHERE id = ?", ballot)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active FROM award_coach_voters WHERE id = ?", Boolean.class, voter)).isFalse();
        assertThat(count("SELECT count(*) FROM user_accounts WHERE id = ? AND email IS NULL AND password_hash IS NULL", userId)).isEqualTo(1);
        transactions.executeWithoutResult(status -> processor.finish());
        assertThat(state()).isEqualTo("DELETING");
        UUID authorization = jdbc.queryForObject("SELECT id FROM account_deletion_cleanup WHERE resource_type = 'PAR'", UUID.class);
        transactions.executeWithoutResult(status -> cleanup.process(authorization));
        for (UUID task : jdbc.queryForList("SELECT id FROM account_deletion_cleanup", UUID.class)) {
            transactions.executeWithoutResult(status -> cleanup.process(task));
        }
        transactions.executeWithoutResult(status -> processor.finish());
        assertThat(state()).isEqualTo("DELETED");
        verify(storage).deletePar("authorization");
        verify(storage).deleteObject("video");
        verify(storage).deleteObject("temporary");
        insertUser(UUID.randomUUID(), "owner@example.com", "+5511999999999", "ACTIVE");
    }

    @Test
    void doesNotDeleteBeforeDeadlineAndRollsBackOnFailure() {
        jdbc.update("UPDATE user_accounts SET deletion_scheduled_for = ? WHERE id = ?",
                Timestamp.from(now.plusSeconds(1)), userId);
        transactions.executeWithoutResult(status -> processor.process(userId));
        assertThat(state()).isEqualTo("PENDING_DELETION");
        jdbc.update("UPDATE user_accounts SET deletion_scheduled_for = ? WHERE id = ?", timestamp(), userId);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            processor.process(userId);
            throw new IllegalStateException("simulated failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo("PENDING_DELETION");
        assertThat(jdbc.queryForObject("SELECT email FROM user_accounts WHERE id = ?", String.class, userId))
                .isEqualTo("owner@example.com");
    }

    @Test
    void cleanupFailuresRemainDurableAndDoNotFinishDeletion() {
        new AccountMediaTracker(jdbc, clock).track(userId, "OBJECT", "video");
        transactions.executeWithoutResult(status -> processor.process(userId));
        UUID taskId = jdbc.queryForObject("SELECT id FROM account_deletion_cleanup", UUID.class);
        doThrow(new IllegalStateException("storage unavailable")).when(storage).deleteObject(anyString());
        transactions.executeWithoutResult(status -> cleanup.process(taskId));
        transactions.executeWithoutResult(status -> processor.finish());
        assertThat(state()).isEqualTo("DELETING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM account_deletion_cleanup", Integer.class)).isEqualTo(1);
        transactions.executeWithoutResult(status -> cleanup.process(taskId));
        verify(storage, times(1)).deleteObject("video");
    }

    @Test
    void lockedCancellationWinsAgainstConcurrentDeletionWorker() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM user_accounts WHERE id = ? FOR UPDATE", userId);
                var competingWorker = executor.submit(() -> transactions.executeWithoutResult(
                        competing -> processor.process(userId)));
                try {
                    competingWorker.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                jdbc.update("""
                        UPDATE user_accounts SET status = 'ACTIVE', deletion_requested_at = NULL,
                            deletion_scheduled_for = NULL, token_version = token_version + 1 WHERE id = ?
                        """, userId);
            });
            transactions.executeWithoutResult(status -> processor.process(userId));
            assertThat(state()).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("SELECT email FROM user_accounts WHERE id = ?", String.class, userId))
                    .isEqualTo("owner@example.com");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mediaLedgerIsNotProcessedWhileCancellationIsStillPossible() {
        new AccountMediaTracker(jdbc, clock).track(userId, "OBJECT", "video");
        UUID taskId = jdbc.queryForObject("SELECT id FROM account_deletion_cleanup", UUID.class);
        transactions.executeWithoutResult(status -> cleanup.process(taskId));
        verifyNoInteractions(storage);
        assertThat(count("SELECT count(*) FROM account_deletion_cleanup WHERE id = ?", taskId)).isEqualTo(1);
    }

    @Test
    void attemptsAreSharedAndExpireAfterFifteenMinutes() {
        var attempts = new AccountDeletionAttemptService(jdbc, new TokenSupport(), clock);
        for (int attempt = 0; attempt < 7; attempt++) {
            transactions.executeWithoutResult(status -> attempts.reserve("account"));
        }
        var secondInstance = new AccountDeletionAttemptService(jdbc, new TokenSupport(), clock);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> secondInstance.reserve("account")))
                .isInstanceOf(br.com.dnafutsal.backend.common.BusinessException.class);
        var later = new AccountDeletionAttemptService(jdbc, new TokenSupport(),
                Clock.fixed(now.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
        transactions.executeWithoutResult(status -> later.reserve("account"));
        assertThat(jdbc.queryForObject("SELECT attempts FROM account_deletion_attempts", Integer.class)).isEqualTo(1);
    }

    private void insertUser(UUID id, String email, String phone, String status) {
        jdbc.update("""
                INSERT INTO user_accounts
                (id, name, email, phone, password_hash, status, created_at, updated_at,
                 deletion_requested_at, deletion_scheduled_for)
                VALUES (?, 'Responsável', ?, ?, 'hash', ?, ?, ?, ?, ?)
                """, id, email, phone, status, timestamp(), timestamp(),
                Timestamp.from(now.minus(Duration.ofDays(10))), timestamp());
    }

    private UUID insertEdition() {
        UUID edition = UUID.randomUUID();
        jdbc.update("INSERT INTO award_editions (id, slug, name, season, status, created_at) VALUES (?, ?, 'Prêmio', 2026, 'CLOSED', ?)",
                edition, edition.toString(), timestamp());
        return edition;
    }

    private UUID insertRegistration(UUID owner, UUID edition, String status) {
        UUID registration = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO award_registrations
                (id, registration_number, edition_id, representative_user_id, representative_cpf_encrypted,
                 athlete_name, athlete_instagram, athlete_instagram_normalized, event_id, division_id, division_name,
                 category_id, category_name, team_id, team_name, status, created_at, updated_at, gender)
                VALUES (?, nextval('award_registration_number_seq'), ?, ?, 'encrypted', 'Atleta', ?, ?,
                    1, 1, 'Divisão', 1, 'Categoria', 'team', 'Time', ?, ?, ?, 'MALE')
                """, registration, edition, owner, registration.toString(), registration.toString(), status, timestamp(), timestamp());
        return registration;
    }

    private Timestamp timestamp() {
        return Timestamp.from(now);
    }

    private String state() {
        return jdbc.queryForObject("SELECT status FROM user_accounts WHERE id = ?", String.class, userId);
    }

    private long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }
}

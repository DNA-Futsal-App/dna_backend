package br.com.dnafutsal.backend.awards.infrastructure;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Executa migrations reais V20/V21/V22 em PostgreSQL e testa janelas por contexto e concorrência. */
@Testcontainers(disabledWithoutDocker = true)
class AwardPopularVotingPostgresConcurrencyTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final UUID EDITION = UUID.randomUUID();
    private static final UUID VOTER = UUID.randomUUID();
    private static final UUID ENTRY = UUID.randomUUID();

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void runMigration(Connection connection, String name) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + name));
    }

    @BeforeAll
    static void setup() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE award_editions(id UUID PRIMARY KEY)");
            s.execute("CREATE TABLE user_accounts(id UUID PRIMARY KEY)");
            s.execute("CREATE TABLE award_registration_entries(id UUID PRIMARY KEY, review_status VARCHAR(20), reviewed_at TIMESTAMPTZ)");
            s.execute("CREATE TABLE award_pre_vote_finalists(entry_id UUID UNIQUE REFERENCES award_registration_entries(id), finalized_at TIMESTAMPTZ NOT NULL)");
            s.execute("INSERT INTO award_editions(id) VALUES ('" + EDITION + "')");
            s.execute("INSERT INTO user_accounts(id) VALUES ('" + VOTER + "')");
            s.execute("INSERT INTO award_registration_entries(id, review_status, reviewed_at) VALUES ('" + ENTRY + "', 'APPROVED', '2026-11-01 09:00:00+00')");
            s.execute("INSERT INTO award_pre_vote_finalists(entry_id, finalized_at) VALUES ('" + ENTRY + "', '2026-11-01 10:00:00+00')");
            runMigration(c, "V20__award_popular_voting.sql");
            runMigration(c, "V21__award_popular_approval_snapshot.sql");
            runMigration(c, "V22__award_popular_context_windows.sql");
            s.execute("INSERT INTO award_popular_context_windows "
                    + "(id, edition_id, event_id, division_id, category_id, gender, contest_category, opens_at, closes_at) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + EDITION + "', 908, 3, 12, 'MALE', 'BEST_DRIBBLE', "
                    + "'2026-11-01 10:00:00+00', '2026-11-30 10:00:00+00')");
            s.execute("INSERT INTO award_popular_context_windows "
                    + "(id, edition_id, event_id, division_id, category_id, gender, contest_category, opens_at, closes_at) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + EDITION + "', 908, 3, 12, 'MALE', 'BEST_SAVE', "
                    + "'2026-11-15 10:00:00+00', '2026-12-30 10:00:00+00')");
        }
    }

    @Test
    void backfillsOnlyValidApprovedFinalists() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT approved_at FROM award_pre_vote_finalists WHERE entry_id = '" + ENTRY + "'")) {
            assertTrue(r.next());
            assertEquals(Instant.parse("2026-11-01T09:00:00Z"), r.getTimestamp(1).toInstant());
        }
    }

    @Test
    void differentContestCategoriesHaveIndependentWindowsAndSameContextIsUnique() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(*) FROM award_popular_context_windows WHERE edition_id = '" + EDITION + "'")) {
            assertTrue(r.next());
            assertEquals(2, r.getInt(1));
        }
        try (Connection c = connection(); Statement s = c.createStatement()) {
            String duplicate = "INSERT INTO award_popular_context_windows(id,edition_id,event_id,division_id,category_id,gender,contest_category,opens_at,closes_at) "
                    + "VALUES ('" + UUID.randomUUID() + "','" + EDITION + "',908,3,12,'MALE','BEST_SAVE',"
                    + "'2026-11-15 10:00:00+00','2026-12-30 10:00:00+00')";
            assertThrows(java.sql.SQLException.class, () -> s.execute(duplicate));
        }
    }

    @Test
    void concurrentInsertsFromSameVoterCommitExactlyOneVote() throws Exception {
        String insert = """
                INSERT INTO award_popular_votes
                  (id, edition_id, event_id, division_id, category_id, gender,
                   contest_category, entry_id, voter_user_id, created_at)
                VALUES (?, ?, 908, 3, 12, 'MALE', 'BEST_DRIBBLE', ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT ON CONSTRAINT uk_award_popular_voter_context DO NOTHING
                """;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> vote = () -> {
            try (Connection c = connection()) {
                c.setAutoCommit(false);
                ready.countDown();
                if (!go.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo de espera excedido");
                try (Statement s = c.createStatement()) {
                    s.executeQuery("SELECT edition_id FROM award_popular_context_windows WHERE edition_id = '" + EDITION + "' "
                        + "AND event_id = 908 AND division_id = 3 AND category_id = 12 "
                        + "AND gender = 'MALE' AND contest_category = 'BEST_DRIBBLE' FOR SHARE").close();
                }
                int updated;
                try (PreparedStatement p = c.prepareStatement(insert)) {
                    p.setObject(1, UUID.randomUUID());
                    p.setObject(2, EDITION);
                    p.setObject(3, ENTRY);
                    p.setObject(4, VOTER);
                    updated = p.executeUpdate();
                }
                c.commit();
                return updated;
            }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(vote);
            var second = pool.submit(vote);
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(1, first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT count(*) FROM award_popular_votes WHERE voter_user_id = '" + VOTER + "'")) {
            assertTrue(r.next());
            assertEquals(1, r.getInt(1));
        }
    }
}

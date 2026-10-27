package br.com.dnafutsal.backend.identity.application;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A migração V17 não pode alterar o evento original de usuários antigos. */
@Testcontainers(disabledWithoutDocker = true)
class UserCompetitionMigrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void upgradeFromV16PreservesLegacyProfilesAndAllowsFemalePreference() {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);

        // Reproduz a evolução real de um banco preexistente, em vez de testar apenas um banco novo.
        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("16"))
                .load()
                .migrate();

        UUID paulistaUser = UUID.randomUUID();
        UUID uniaoUser = UUID.randomUUID();
        insertLegacyUser(jdbc, paulistaUser, "paulista@example.test", "+5511999990001", 917L);
        insertLegacyUser(jdbc, uniaoUser, "uniao@example.test", "+5511999990002", 925L);

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForObject("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'user_accounts' AND column_name = 'followed_competition_code'
                    """, String.class))
                .isEqualTo("YES");

        for (UUID userId : new UUID[]{paulistaUser, uniaoUser}) {
            assertThat(jdbc.queryForObject(
                    "SELECT followed_competition_code FROM user_accounts WHERE id = ?",
                    String.class, userId)).isNull();
            assertThat(jdbc.queryForObject(
                    "SELECT followed_division_id FROM user_accounts WHERE id = ?",
                    String.class, userId)).isEqualTo("3");
            assertThat(jdbc.queryForObject(
                    "SELECT followed_category_id FROM user_accounts WHERE id = ?",
                    String.class, userId)).isEqualTo("7");
            assertThat(jdbc.queryForObject(
                    "SELECT followed_team_id FROM user_accounts WHERE id = ?",
                    String.class, userId)).isEqualTo("team-1");
        }

        assertThat(jdbc.queryForObject(
                "SELECT followed_event_id FROM user_accounts WHERE id = ?",
                Long.class, paulistaUser)).isEqualTo(917L);
        assertThat(jdbc.queryForObject(
                "SELECT followed_event_id FROM user_accounts WHERE id = ?",
                Long.class, uniaoUser)).isEqualTo(925L);

        jdbc.update("""
                UPDATE user_accounts SET followed_competition_code = 'PAULISTA_FEMININO'
                WHERE id = ?
                """, paulistaUser);
        assertThat(jdbc.queryForObject(
                "SELECT followed_competition_code FROM user_accounts WHERE id = ?",
                String.class, paulistaUser)).isEqualTo("PAULISTA_FEMININO");

        // Migrar novamente é inofensivo; Flyway mantém seu histórico intacto.
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '17' AND success",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT followed_competition_code FROM user_accounts WHERE id = ?",
                String.class, uniaoUser)).isNull();
    }

    private void insertLegacyUser(JdbcTemplate jdbc, UUID id, String email, String phone, long eventId) {
        jdbc.update("""
                INSERT INTO user_accounts
                (id, name, email, phone, password_hash, status, followed_event_id,
                 followed_division_id, followed_category_id, followed_team_id,
                 created_at, updated_at)
                VALUES (?, 'Usuário de teste', ?, ?, 'test-hash', 'ACTIVE', ?,
                        '3', '7', 'team-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, email, phone, eventId);
    }
}

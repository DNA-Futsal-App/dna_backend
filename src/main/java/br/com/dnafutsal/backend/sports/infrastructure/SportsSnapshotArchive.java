package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Last successfully collected sports snapshot, stored outside the JVM so a restart
 * does not erase the fallback. No data is fabricated when the archive is empty.
 */
@Repository
public class SportsSnapshotArchive {

    private static final Logger log = LoggerFactory.getLogger(SportsSnapshotArchive.class);

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Duration maxAge;

    public SportsSnapshotArchive(JdbcTemplate jdbc, JsonMapper json,
                                 @Value("${app.sports.archive.max-age:P14D}") Duration maxAge) {
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Sports archive max-age must be positive");
        }
        this.jdbc = jdbc;
        this.json = json;
        this.maxAge = maxAge;
    }

    public void save(SportsSnapshot snapshot) {
        long eventId = snapshot.event().eventId();
        try {
            String payload = json.writeValueAsString(snapshot);
            jdbc.update("""
                    INSERT INTO sports_snapshot_archive
                        (event_id, season, title, division, category, snapshot_json, last_success_at)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CURRENT_TIMESTAMP)
                    ON CONFLICT (event_id) DO UPDATE SET
                        season = EXCLUDED.season,
                        title = EXCLUDED.title,
                        division = EXCLUDED.division,
                        category = EXCLUDED.category,
                        snapshot_json = EXCLUDED.snapshot_json,
                        last_success_at = EXCLUDED.last_success_at
                    """, eventId, snapshot.event().season(), snapshot.event().title(),
                    snapshot.event().division(), snapshot.event().category(), payload);
        } catch (RuntimeException exception) {
            // Never take down a working live response because archival storage failed.
            log.error("Cannot archive sports snapshot eventId={} exceptionType={}",
                    eventId, exception.getClass().getName());
        }
    }

    public Optional<SportsSnapshot> findRecent(long eventId) {
        try {
            List<String> rows = jdbc.queryForList("""
                    SELECT snapshot_json::text
                    FROM sports_snapshot_archive
                    WHERE event_id = ?
                      AND last_success_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                    """, String.class, eventId, maxAge.toSeconds());
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            SportsSnapshot snapshot = json.readValue(rows.get(0), SportsSnapshot.class);
            if (snapshot == null || snapshot.event() == null || snapshot.event().eventId() != eventId) {
                log.error("Ignoring corrupted sports archive eventId={}", eventId);
                return Optional.empty();
            }
            return Optional.of(snapshot);
        } catch (RuntimeException exception) {
            log.error("Cannot read sports archive eventId={} exceptionType={}",
                    eventId, exception.getClass().getName());
            return Optional.empty();
        }
    }

    public OptionalLong findDefaultEventId(int season, String division, String category) {
        try {
            List<Long> rows = jdbc.queryForList("""
                    SELECT event_id
                    FROM sports_snapshot_archive
                    WHERE season = ?
                      AND LOWER(TRIM(title)) IN ('paulista', 'campeonato paulista')
                      AND LOWER(TRIM(division)) = LOWER(TRIM(?))
                      AND LOWER(TRIM(category)) = LOWER(TRIM(?))
                      AND last_success_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                    ORDER BY last_success_at DESC
                    LIMIT 1
                    """, Long.class, season, division, category, maxAge.toSeconds());
            return rows.isEmpty() ? OptionalLong.empty() : OptionalLong.of(rows.get(0));
        } catch (RuntimeException exception) {
            log.error("Cannot locate archived default sports event season={} exceptionType={}",
                    season, exception.getClass().getName());
            return OptionalLong.empty();
        }
    }
}

package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.api.AwardPreVoteContextRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AwardPreVoteTieDecisionRepository {
    private final JdbcTemplate jdbc;

    public AwardPreVoteTieDecisionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Decision(int finalistCount, List<UUID> entries) {}

    public Optional<Decision> find(UUID editionId, AwardPreVoteContextRequest context) {
        var result = jdbc.query("""
                SELECT finalist_count, rank_1_entry_id, rank_2_entry_id,
                       rank_3_entry_id, rank_4_entry_id
                FROM award_pre_vote_tie_decisions
                WHERE edition_id = ? AND event_id = ? AND division_id = ?
                  AND category_id = ? AND gender = ? AND contest_category = ?
                """, (rs, row) -> {
                    UUID fourth = rs.getObject("rank_4_entry_id", UUID.class);
                    var ids = new java.util.ArrayList<UUID>();
                    ids.add(rs.getObject("rank_1_entry_id", UUID.class));
                    ids.add(rs.getObject("rank_2_entry_id", UUID.class));
                    ids.add(rs.getObject("rank_3_entry_id", UUID.class));
                    if (fourth != null) ids.add(fourth);
                    return new Decision(rs.getInt("finalist_count"), List.copyOf(ids));
                }, editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender().name(), context.contestCategory().name());
        return result.stream().findFirst();
    }

    public void save(UUID editionId, AwardPreVoteContextRequest context,
                     List<UUID> chosen, UUID administrator) {
        jdbc.update("""
                INSERT INTO award_pre_vote_tie_decisions
                  (edition_id, event_id, division_id, category_id, gender, contest_category,
                   finalist_count, rank_1_entry_id, rank_2_entry_id, rank_3_entry_id,
                   rank_4_entry_id, decided_by_user_id, decided_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (edition_id, event_id, division_id, category_id, gender, contest_category)
                DO UPDATE SET finalist_count = EXCLUDED.finalist_count,
                   rank_1_entry_id = EXCLUDED.rank_1_entry_id,
                   rank_2_entry_id = EXCLUDED.rank_2_entry_id,
                   rank_3_entry_id = EXCLUDED.rank_3_entry_id,
                   rank_4_entry_id = EXCLUDED.rank_4_entry_id,
                   decided_by_user_id = EXCLUDED.decided_by_user_id,
                   decided_at = CURRENT_TIMESTAMP
                """, editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender().name(), context.contestCategory().name(), chosen.size(),
                chosen.get(0), chosen.get(1), chosen.get(2),
                chosen.size() == 4 ? chosen.get(3) : null, administrator);
    }
}

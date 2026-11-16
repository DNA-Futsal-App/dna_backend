package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.application.SportsQueryService;
import br.com.dnafutsal.backend.sports.application.SportsSnapshotService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CompetitionKey;
import br.com.dnafutsal.backend.sports.domain.CompetitionKeyView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Immutable eligibility snapshot per award edition and official sporting category. */
@Service
public class AwardGoldEligibilityService {
    private final AwardEditionRepository editions;
    private final SportsCatalogService sports;
    private final SportsSnapshotService snapshots;
    private final SportsQueryService sportsKeys;
    private final AwardLiveRosterService rosters;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AwardGoldEligibilityService(AwardEditionRepository editions, SportsCatalogService sports,
                                       SportsSnapshotService snapshots, SportsQueryService sportsKeys,
                                       AwardLiveRosterService rosters,
                                       JdbcTemplate jdbc, Clock clock) {
        this.editions = editions;
        this.sports = sports;
        this.snapshots = snapshots;
        this.sportsKeys = sportsKeys;
        this.rosters = rosters;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Report preview(UUID editionId, long eventId, long divisionId, long categoryId, String stage) {
        String normalizedStage = stage(stage);
        SportsSnapshot data = requireContext(editionId, eventId, divisionId, categoryId);

        // The prize uses the exact classification source of /api/v1/standings/keys.
        // Do not maintain a second Ouro/Prata/Bronze classifier for awards.
        List<CompetitionKeyView> classified = sportsKeys.competitionKeys(
                new SportsFilter(eventId, null), normalizedStage);
        Map<String, TeamView> official = new HashMap<>();
        for (TeamView team : sports.teams(eventId)) {
            if (team != null && team.id() != null && !team.id().isBlank()) {
                official.putIfAbsent(team.id(), team);
            }
        }
        List<Team> gold = new ArrayList<>();
        Set<String> unresolved = new TreeSet<>();
        for (CompetitionKeyView entry : classified) {
            if (entry.key() != CompetitionKey.GOLD) continue;
            TeamView team = official.get(entry.teamId());
            if (team == null) {
                unresolved.add(entry.teamId());
            } else {
                gold.add(new Team(team.id(), team.name(), null));
            }
        }
        gold.sort(Comparator.comparing(Team::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Team::id));
        return new Report(editionId, eventId, divisionId, categoryId, normalizedStage,
                data.collectedAt(), null, false,
                List.copyOf(gold), List.of(), List.copyOf(unresolved));
    }

    /** Prepare and persist ALL athletes, or roll back the entire category; never partially release it. */
    @Transactional
    public Report prepare(UUID editionId, long eventId, long divisionId, long categoryId, String stage) {
        AwardEdition edition = requireEdition(editionId);
        if (edition.getStatus() != AwardEditionStatus.DRAFT
                && edition.getStatus() != AwardEditionStatus.OPEN) {
            throw Errors.conflict("AWARD_GOLD_PREPARATION_LOCKED",
                    "A Chave Ouro só pode ser preparada em uma edição em rascunho ou aberta.");
        }
        if (isPrepared(editionId, eventId, divisionId, categoryId)) {
            throw Errors.conflict("AWARD_GOLD_ALREADY_PREPARED",
                    "Esta categoria já possui uma relação de elegíveis congelada.");
        }
        Report report = preview(editionId, eventId, divisionId, categoryId, stage);
        if (report.teams().isEmpty() || !report.ambiguousTeamIds().isEmpty()
                || !report.unresolvedTeams().isEmpty()) {
            throw Errors.conflict("AWARD_GOLD_REVIEW_REQUIRED",
                    "Chave Ouro não confirmada: reveja as equipes e as informações da federação.");
        }
        if (edition.getStatus() == AwardEditionStatus.OPEN) {
            // FK inserts into award_ballots acquire KEY SHARE on the coach context.
            // Lock contexts before checking ballots, preventing a concurrent submission
            // from slipping between the no-votes check and the eligibility snapshot.
            jdbc.queryForList("""
                    SELECT id FROM award_coach_voters
                     WHERE edition_id=? AND event_id=? AND division_id=? AND category_id=?
                     ORDER BY id FOR UPDATE
                    """, UUID.class, editionId, eventId, divisionId, categoryId);
            Long submittedBallots = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM award_ballots b
                    JOIN award_coach_voters v ON v.id = b.coach_voter_id
                     WHERE b.edition_id=? AND v.edition_id=b.edition_id
                       AND v.event_id=? AND v.division_id=? AND v.category_id=?
                    """, Long.class, editionId, eventId, divisionId, categoryId);
            if (submittedBallots != null && submittedBallots > 0) {
                throw Errors.conflict("AWARD_GOLD_BALLOTS_EXIST",
                        "Não é permitido preparar a Chave Ouro após o registro de votos nesta categoria.");
            }
        }
        Instant preparedAt = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO award_gold_eligibility (edition_id, event_id, division_id, category_id,
                        stage, source_collected_at, prepared_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, editionId, eventId, divisionId, categoryId, report.stage(),
                    timestamp(report.sourceCollectedAt()), Timestamp.from(preparedAt));
        } catch (DataIntegrityViolationException exception) {
            throw Errors.conflict("AWARD_GOLD_ALREADY_PREPARED",
                    "Esta categoria já possui uma relação de elegíveis congelada.");
        }
        List<Team> preparedTeams = new ArrayList<>();
        for (Team team : report.teams()) {
            jdbc.update("""
                    INSERT INTO award_gold_eligible_teams
                        (edition_id, event_id, division_id, category_id, team_id, team_name)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, editionId, eventId, divisionId, categoryId, team.id(), team.name());
            int athletes = rosters.athletes(editionId, eventId, divisionId, categoryId, team.id()).size();
            if (athletes == 0) {
                throw Errors.conflict("AWARD_GOLD_ROSTER_EMPTY",
                        "Não foi possível obter os atletas de uma equipe da Chave Ouro: " + team.name());
            }
            preparedTeams.add(new Team(team.id(), team.name(), athletes));
        }
        return new Report(editionId, eventId, divisionId, categoryId, report.stage(),
                report.sourceCollectedAt(), preparedAt, true, List.copyOf(preparedTeams), List.of(), List.of());
    }

    @Transactional(readOnly = true)
    public Report prepared(UUID editionId, long eventId, long divisionId, long categoryId) {
        requireEdition(editionId);
        List<PreparedInfo> rows = jdbc.query("""
                SELECT stage, source_collected_at, prepared_at
                  FROM award_gold_eligibility
                 WHERE edition_id=? AND event_id=? AND division_id=? AND category_id=?
                """, (rs, row) -> new PreparedInfo(rs.getString("stage"),
                toInstant(rs.getTimestamp("source_collected_at")),
                toInstant(rs.getTimestamp("prepared_at"))),
                editionId, eventId, divisionId, categoryId);
        PreparedInfo info = rows.stream().findFirst().orElseThrow(() -> Errors.notFound(
                "AWARD_GOLD_NOT_PREPARED", "A relação de equipes elegíveis ainda não foi preparada."));
        return new Report(editionId, eventId, divisionId, categoryId, info.stage(),
                info.collectedAt(), info.preparedAt(), true,
                eligibleTeams(editionId, eventId, divisionId, categoryId), List.of(), List.of());
    }

    @Transactional(readOnly = true)
    public Set<String> eligibleTeamIds(UUID editionId, long eventId, long divisionId, long categoryId) {
        return jdbc.query("""
                SELECT team_id FROM award_gold_eligible_teams
                 WHERE edition_id=? AND event_id=? AND division_id=? AND category_id=?
                """, (rs, row) -> rs.getString("team_id"),
                editionId, eventId, divisionId, categoryId).stream().collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public void requireEligible(UUID editionId, long eventId, long divisionId, long categoryId, String teamId) {
        if (!isPrepared(editionId, eventId, divisionId, categoryId)) {
            throw Errors.conflict("AWARD_GOLD_NOT_PREPARED",
                    "A lista de atletas da Chave Ouro ainda não foi aprovada para esta categoria.");
        }
        if (teamId == null || !eligibleTeamIds(editionId, eventId, divisionId, categoryId)
                .contains(teamId.trim())) {
            throw Errors.badRequest("AWARD_TEAM_NOT_GOLD",
                    "Somente atletas de clubes da Chave Ouro podem receber votos.");
        }
    }


    private boolean isPrepared(UUID editionId, long eventId, long divisionId, long categoryId) {
        return Boolean.TRUE.equals(jdbc.query("""
            SELECT 1 FROM award_gold_eligibility
             WHERE edition_id=? AND event_id=? AND division_id=? AND category_id=?
            """, ResultSet::next, editionId, eventId, divisionId, categoryId));
    }


    private List<Team> eligibleTeams(UUID editionId, long eventId, long divisionId, long categoryId) {
        return jdbc.query("""
                SELECT t.team_id, t.team_name, COUNT(c.id) AS athlete_count
                  FROM award_gold_eligible_teams t
                  LEFT JOIN award_candidates c ON c.edition_id=t.edition_id
                      AND c.event_id=t.event_id AND c.division_id=t.division_id
                      AND c.category_id=t.category_id AND c.team_id=t.team_id
                      AND c.candidate_type='ATHLETE' AND c.active=TRUE
                 WHERE t.edition_id=? AND t.event_id=? AND t.division_id=? AND t.category_id=?
                 GROUP BY t.team_id, t.team_name
                 ORDER BY t.team_name, t.team_id
                """, (rs, row) -> new Team(rs.getString("team_id"), rs.getString("team_name"),
                rs.getInt("athlete_count")),
                editionId, eventId, divisionId, categoryId);
    }

    private AwardEdition requireEdition(UUID id) {
        return editions.findById(id).orElseThrow(() -> Errors.notFound(
                "AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
    }

    private SportsSnapshot requireContext(UUID editionId, long eventId, long divisionId, long categoryId) {
        AwardEdition edition = requireEdition(editionId);
        CatalogCategoryView category = sports.categories(edition.getSeason(), divisionId).stream()
                .filter(item -> item.id() == categoryId).findFirst()
                .orElseThrow(() -> Errors.badRequest("AWARD_CATEGORY_INVALID",
                        "A categoria não pertence à divisão e temporada selecionadas."));
        if (category.eventId() != eventId) {
            throw Errors.badRequest("AWARD_EVENT_MISMATCH",
                    "O evento não corresponde à divisão e categoria selecionadas.");
        }
        SportsSnapshot snapshot = snapshots.snapshot(eventId);
        if (snapshot.event().season() != edition.getSeason()) {
            throw Errors.badRequest("AWARD_SEASON_MISMATCH",
                    "A temporada do evento não corresponde à edição do prêmio.");
        }
        return snapshot;
    }

    private String stage(String stage) {
        String normalized = stage == null ? "" : stage.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("PAULISTA") && !normalized.equals("UNIAO")) {
            throw Errors.badRequest("AWARD_GOLD_STAGE_INVALID",
                    "Use PAULISTA ou UNIAO para identificar a competição.");
        }
        return normalized;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record PreparedInfo(String stage, Instant collectedAt, Instant preparedAt) { }
    public record Team(String id, String name, Integer athletes) { }
    public record Report(UUID editionId, long eventId, long divisionId, long categoryId,
                         String stage, Instant sourceCollectedAt, Instant preparedAt, boolean prepared,
                         List<Team> teams, List<String> ambiguousTeamIds, List<String> unresolvedTeams) { }
}

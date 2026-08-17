package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsQueryServiceTest {

    private final SportsSnapshotService snapshots = mock(SportsSnapshotService.class);
    private final SportsQueryService service = new SportsQueryService(snapshots,
            new AppProperties("http://localhost:3000", List.of("http://localhost:3000"),
                    "America/Sao_Paulo"));

    @Test
    void splitsMatchesAndAppliesTeamPhaseAndDateFilters() {
        TeamView teamA = new TeamView("10", "Time A", null, null);
        TeamView teamB = new TeamView("20", "Time B", null, null);
        TeamView teamC = new TeamView("30", "Time C", null, null);
        MatchView finished = match("100", "1ª Fase", teamA, teamB,
                Instant.parse("2026-04-10T22:30:00Z"), "FINISHED");
        MatchView scheduled = match("101", "2ª Fase", teamB, teamC,
                Instant.parse("2026-05-10T21:00:00Z"), "SCHEDULED");
        when(snapshots.snapshot(917)).thenReturn(snapshot(
                List.of(finished, scheduled), List.of(), List.of()));

        List<MatchView> result = service.playedMatches(new SportsFilter(917, "10"),
                "1a fase", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(result).containsExactly(finished);
        assertThat(service.upcomingMatches(new SportsFilter(917, null), null, null, null))
                .containsExactly(scheduled);
    }

    @Test
    void filtersStandingsAndScorersWithoutDiscardingTheirMetadata() {
        TeamView teamA = new TeamView("10", "Time A", null, null);
        TeamView teamB = new TeamView("20", "Time B", null, null);
        StandingView rowA = standing("Grupo A", teamA);
        StandingView rowB = standing("Grupo B", teamB);
        TopScorerView scorerA = new TopScorerView(1, "1ª Fase", "Atleta A", null, teamA, 8, false);
        TopScorerView scorerB = new TopScorerView(2, "1ª Fase", "Atleta B", null, teamB, 7, false);
        when(snapshots.snapshot(917)).thenReturn(snapshot(
                List.of(), List.of(rowA, rowB), List.of(scorerA, scorerB)));

        assertThat(service.standings(new SportsFilter(917, "10"), "1a", "grupo a"))
                .containsExactly(rowA);
        assertThat(service.topScorers(new SportsFilter(917, null), null, 1))
                .containsExactly(scorerA);
        assertThat(service.topScorers(new SportsFilter(917, "20"), null, 100))
                .singleElement()
                .satisfies(scorer -> {
                    assertThat(scorer.athleteName()).isEqualTo("Atleta B");
                    assertThat(scorer.position()).isEqualTo(1);
                });
    }

    @Test
    void rejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.playedMatches(new SportsFilter(917, null), null,
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SPORTS_DATE_RANGE_INVALID"));
    }

    private SportsSnapshot snapshot(List<MatchView> matches, List<StandingView> standings,
                                    List<TopScorerView> scorers) {
        return new SportsSnapshot(
                new SportsEventView(917, "Paulista", 2026, "Principal", "A1", null),
                List.of(), matches, standings, scorers, Instant.parse("2026-08-15T12:00:00Z"));
    }

    private MatchView match(String id, String phase, TeamView home, TeamView away,
                            Instant scheduledAt, String status) {
        return new MatchView(id, 917, "Paulista", 2026, "Principal", "A1", phase,
                home, away, null, null, scheduledAt, status, false, null, null);
    }

    private StandingView standing(String group, TeamView team) {
        return new StandingView("1ª Fase", group, 1, team, 4, 4, 0, 0,
                20, 5, 15, 12, 3.0, 5.0, 1.25, 0.95);
    }
}

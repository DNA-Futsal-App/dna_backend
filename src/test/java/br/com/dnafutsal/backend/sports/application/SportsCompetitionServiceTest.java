package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SportsCompetitionServiceTest {
    @Test
    void doesNotLeakPaulistaIntoUnionGamesOrInventRankings() {
        SportsSnapshotService snapshots = mock(SportsSnapshotService.class);
        SportsQueryService legacy = mock(SportsQueryService.class);
        TeamView team = new TeamView("a", "Clube A", null, null);
        MatchView paulista = new MatchView("1", 918, "Paulista", 2026, "Sub-7", "A3",
                "Fase Classificatória", team, team, null, null, null, null,
                MatchStatus.SCHEDULED, false, null, null);
        MatchView uniao = new MatchView("2", 918, "Paulista", 2026, "Sub-7", "A3",
                "Torneio União - Grupo 4", team, team, null, null, null, null,
                MatchStatus.SCHEDULED, false, null, null);
        var snapshot = new SportsSnapshot(new SportsEventView(918, "Paulista", 2026, "Sub-7", "A3", null),
                List.of(team), List.of(paulista, uniao), List.of(), List.of(), Instant.EPOCH);
        when(snapshots.snapshot(918)).thenReturn(snapshot);
        when(legacy.upcomingMatches(new SportsFilter(918, null), null, null, null))
                .thenReturn(List.of(paulista, uniao));
        var service = new SportsCompetitionService(snapshots, legacy);
        assertThat(service.upcoming(new SportsFilter(918, null), "UNIAO", null, null, null))
                .extracting(MatchView::id).containsExactly("2");
        assertThat(service.scorers(new SportsFilter(918, null), "UNIAO", null, 10)).isEmpty();
        assertThat(service.structure(918).stages()).extracting(CompetitionStructureView.Stage::id)
                .containsExactly("PAULISTA", "UNIAO");
    }

    @Test
    void separatesOfficialPhasesAndPreservesDistinctGroups() {
        SportsSnapshotService snapshots = mock(SportsSnapshotService.class);
        SportsQueryService legacy = mock(SportsQueryService.class);
        TeamView team = new TeamView("a", "Clube A", null, null);
        var phases = List.of("Fase Classificatória", "Torneio União - Grupo 1", "Torneio União - Grupo 6",
                "Torneio União - Quartas Rubi", "Torneio União - Quartas Ouro");
        List<StandingView> rows = phases.stream().map(phase -> new StandingView(
                phase, phase.contains("Grupo") ? "GRUPO " + (phase.endsWith("1") ? "1" : "6") : "CHAVE RUBI",
                1, team, 1, 1, 0, 0, 2, 1, 1, 3, 1.0, 2.0, 1.0, 3.0)).toList();
        var snapshot = new SportsSnapshot(new SportsEventView(918L, "Campeonato Paulista", 2026, "Sub-7", "A3", null),
                List.of(team), List.of(), rows, List.of(), Instant.EPOCH);
        when(snapshots.snapshot(918L)).thenReturn(snapshot);
        var service = new SportsCompetitionService(snapshots, legacy);
        var structure = service.structure(918L);
        assertThat(structure.stages()).extracting(CompetitionStructureView.Stage::id)
                .containsExactly("PAULISTA", "UNIAO");
        assertThat(structure.stages().get(1).phases()).extracting(CompetitionStructureView.Phase::name)
                .contains("Torneio União - Grupo 1", "Torneio União - Grupo 6", "Torneio União - Quartas Rubi");
        assertThat(service.standings(new SportsFilter(918L, null), "UNIAO", "Torneio União - Grupo 1", "GRUPO 1"))
                .hasSize(1);
        assertThat(service.standings(new SportsFilter(918L, null), "UNIAO", "Fase Classificatória", null))
                .isEmpty();
        assertThat(service.scorers(new SportsFilter(918L, null), "UNIAO", null, 10)).isEmpty();
    }
    @Test
    void paulistaDefaultDoesNotSelectUnionClassificationAndDoesNotDuplicateRows() {
        var snapshots = mock(SportsSnapshotService.class);
        var legacy = mock(SportsQueryService.class);
        TeamView club = new TeamView("club-1", "Clube A", null, null);
        TeamView other = new TeamView("club-2", "Clube B", null, null);
        StandingView paulista = standing("Fase Classificatória", "GRUPO A", club, 8);
        StandingView incomplete = standing("Fase Classificatória", "GRUPO A", club, 1);
        StandingView paulistaOther = standing("Fase Classificatória", "GRUPO B", other, 8);
        StandingView union = standing("Torneio União - Grupo 1", "GRUPO 1", club, 3);
        var data = new SportsSnapshot(new SportsEventView(918, "Campeonato Paulista", 2026,
                "Sub-7", "A3", null), List.of(club, other), List.of(),
                List.of(union, incomplete, paulista, paulistaOther), List.of(), Instant.EPOCH);
        when(snapshots.snapshot(918)).thenReturn(data);
        var service = new SportsCompetitionService(snapshots, legacy);
        assertThat(service.standings(new SportsFilter(918, null), "PAULISTA", null, null))
                .hasSize(2).allMatch(row -> row.phase().equals("Fase Classificatória"));
        assertThat(service.standings(new SportsFilter(918, null), "PAULISTA", null, null))
                .filteredOn(row -> row.team().id().equals("club-1"))
                .singleElement().extracting(StandingView::played).isEqualTo(8);
        assertThat(service.standings(new SportsFilter(918, null), "UNIAO", null, null))
                .hasSize(1).first().extracting(StandingView::phase)
                .isEqualTo("Torneio União - Grupo 1");
        verify(legacy, never()).standings(any(), any(), any());
    }

    @Test
    void genericQuarterFinalDoesNotInventBracketNamedFinal() {
        var snapshots = mock(SportsSnapshotService.class);
        var legacy = mock(SportsQueryService.class);
        TeamView club = new TeamView("club-1", "Clube", null, null);
        MatchView match = new MatchView("m-1", 918, "Campeonato Paulista", 2026, "Sub-7", "A3",
                "Quartas de Final", club, club, null, null, null, null,
                MatchStatus.SCHEDULED, false, null, null);
        var snapshot = new SportsSnapshot(new SportsEventView(918, "Campeonato Paulista", 2026,
                "Sub-7", "A3", null), List.of(club), List.of(match), List.of(), List.of(), Instant.EPOCH);
        when(snapshots.snapshot(918)).thenReturn(snapshot);
        var service = new SportsCompetitionService(snapshots, legacy);
        assertThat(service.structure(918).stages().get(0).phases().get(0).brackets()).isEmpty();
    }

    @Test
    void calendarPreservesEveryPublishedPaulistaGameWithoutLeakingUnionGames() {
        var snapshots = mock(SportsSnapshotService.class);
        var legacy = mock(SportsQueryService.class);
        TeamView a = new TeamView("a", "A", null, null);
        TeamView b = new TeamView("b", "B", null, null);
        var paulista1 = match("p1", "Quartas de Final", a, b);
        var paulista2 = match("p2", "Semifinal", a, b);
        var uniao = match("u1", "Torneio União - Quartas Rubi", a, b);
        var snapshot = new SportsSnapshot(new SportsEventView(918, "Campeonato Paulista", 2026,
                "Sub-7", "A3", null), List.of(a, b), List.of(paulista1, paulista2, uniao),
                List.of(), List.of(), Instant.EPOCH);
        when(snapshots.snapshot(918)).thenReturn(snapshot);
        var filter = new SportsFilter(918, null);
        when(legacy.matchCalendar(filter, null, null, null)).thenReturn(
                new MatchCalendarView(null, SportsScheduleState.ACTIVE,
                        List.of(paulista1, paulista2, uniao), List.of(), List.of()));
        var service = new SportsCompetitionService(snapshots, legacy);
        assertThat(service.calendar(filter, "PAULISTA", null, null, null).played())
                .extracting(MatchView::id).containsExactly("p1", "p2");
        assertThat(service.calendar(filter, "UNIAO", null, null, null).played())
                .extracting(MatchView::id).containsExactly("u1");
    }

    private MatchView match(String id, String phase, TeamView a, TeamView b) {
        return new MatchView(id, 918, "Campeonato Paulista", 2026, "Sub-7", "A3",
                phase, a, b, 1, 0, null, null, MatchStatus.FINISHED, false, null, null);
    }

    private StandingView standing(String phase, String group, TeamView team, int played) {
        return new StandingView(phase, group, 1, team, played, 1, 0, 0,
                2, 1, 1, 3, 1.0, 2.0, 1.0, 3.0);
    }

}

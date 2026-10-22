package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsTopScorerScopeTest {

    @Test
    void defaultsToGeneralEvenWhenCurrentPhaseIsKnockout() {
        SportsQueryService service = service(List.of(
                scorer("Geral", "João Geral", 18),
                scorer("Mata-mata", "Pedro Mata", 5)
        ));

        List<TopScorerView> result = service.topScorers(
                new SportsFilter(123L, null), null, 10
        );

        assertThat(result).extracting(TopScorerView::athleteName)
                .containsExactly("João Geral");
    }

    @Test
    void returnsKnockoutOnlyWhenRequested() {
        SportsQueryService service = service(List.of(
                scorer("Geral", "João Geral", 18),
                scorer("Mata-mata", "Pedro Mata", 5)
        ));

        List<TopScorerView> result = service.topScorers(
                new SportsFilter(123L, null),
                null,
                TopScorerScope.KNOCKOUT,
                10
        );

        assertThat(result).extracting(TopScorerView::athleteName)
                .containsExactly("Pedro Mata");
    }

    @Test
    void generalNeverFallsBackToKnockout() {
        SportsQueryService service = service(List.of(
                scorer("Semifinal", "Pedro Mata", 2)
        ));

        assertThat(service.topScorers(
                new SportsFilter(123L, null), null, 10
        )).isEmpty();
    }

    private SportsQueryService service(List<TopScorerView> scorers) {
        SportsSnapshotService snapshots = mock(SportsSnapshotService.class);
        TeamView team = team();

        MatchView semifinal = new MatchView(
                "semi-1", 123L, "Campeonato Paulista", 2026,
                "Sub-13", "A1", "Semifinal", team,
                new TeamView("20", "Adversário", null, null),
                null, null,
                LocalDate.of(2026, 10, 14),
                Instant.parse("2026-10-14T22:00:00Z"),
                MatchStatus.SCHEDULED, false, null, null
        );

        List<TopScorerView> normalized = scorers.stream()
                .map(row -> new TopScorerView(
                        row.position(), row.phase(), row.athleteName(),
                        row.athleteImageUrl(), team, row.goals(),
                        row.personalDataSuppressed()
                ))
                .toList();

        SportsSnapshot snapshot = new SportsSnapshot(
                new SportsEventView(123L, "Campeonato Paulista", 2026,
                        "Sub-13", "A1", null),
                List.of(team),
                List.of(semifinal),
                List.of(),
                normalized,
                Instant.parse("2026-10-13T12:00:00Z")
        );

        when(snapshots.snapshot(123L)).thenReturn(snapshot);

        return new SportsQueryService(
                snapshots,
                new AppProperties(
                        "https://dna.example",
                        List.of("https://dna.example"),
                        "America/Sao_Paulo"
                ),
                Clock.fixed(
                        Instant.parse("2026-10-13T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    private TeamView team() {
        return new TeamView("10", "DNA Futsal", null, null);
    }

    private TopScorerView scorer(String phase, String athlete, int goals) {
        return new TopScorerView(1, phase, athlete, null, team(), goals, false);
    }
}

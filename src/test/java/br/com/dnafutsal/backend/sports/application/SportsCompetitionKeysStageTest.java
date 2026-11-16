package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsCompetitionKeysStageTest {
    @Test
    void paulistaAwardSelectionDoesNotImportUnionKeysFromSameEvent() {
        TeamView paulista = new TeamView("10", "Paulista Ouro", null, null);
        TeamView uniao = new TeamView("20", "Uniao Ouro", null, null);
        SportsSnapshot snapshot = new SportsSnapshot(
                new SportsEventView(908, "Campeonato Paulista", 2026, "Sub-7", "A1", null),
                List.of(paulista, uniao), List.of(), List.of(
                    standing("Quartas de Final", "Chave Ouro", paulista),
                    standing("Torneio Uniao - Quartas de Final", "Chave Ouro", uniao)),
                List.of(), Instant.parse("2026-11-15T04:00:00Z"));
        SportsSnapshotService snapshots = mock(SportsSnapshotService.class);
        when(snapshots.snapshot(908)).thenReturn(snapshot);
        SportsQueryService service = new SportsQueryService(snapshots,
                new AppProperties("https://dna.example", List.of("https://dna.example"),
                        "America/Sao_Paulo"), Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC));

        assertThat(service.competitionKeys(new SportsFilter(908, null), "PAULISTA"))
                .extracting(CompetitionKeyView::teamId).containsExactly("10");
        assertThat(service.competitionKeys(new SportsFilter(908, null), "UNIAO"))
                .extracting(CompetitionKeyView::teamId).containsExactly("20");
        assertThat(service.competitionKeys(new SportsFilter(908, null)))
                .extracting(CompetitionKeyView::teamId).containsExactly("10", "20");
    }

    private StandingView standing(String phase, String group, TeamView team) {
        return new StandingView(phase, group, 1, team, 1, 1, 0, 0, 1, 0, 1,
                3, 1d, 1d, 0d, 1d);
    }
}

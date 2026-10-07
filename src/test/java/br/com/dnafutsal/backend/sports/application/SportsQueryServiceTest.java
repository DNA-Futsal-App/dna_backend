package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsQueryServiceTest {

    @Test
    void removesDuplicateScorerNamesBeforeRankingAndLimit() {
        SportsSnapshotService snapshots =
                mock(
                        SportsSnapshotService.class
                );

        TeamView team =
                new TeamView(
                        "10",
                        "DNA Futsal",
                        null,
                        null
                );

        SportsSnapshot snapshot =
                new SportsSnapshot(
                        new SportsEventView(
                                123L,
                                "Campeonato Paulista",
                                2026,
                                "Sub-13",
                                "A1",
                                null
                        ),
                        List.of(team),
                        List.of(),
                        List.of(),
                        List.of(
                                scorer(1, "João Silva", 8, team, null),
                                scorer(2, "  JOAO   SILVA  ", 10, team, "https://example.test/joao.jpg"),
                                scorer(3, "Maria Souza", 9, team, null)
                        ),
                        Instant.parse("2026-10-07T00:00:00Z")
                );

        when(snapshots.snapshot(123L))
                .thenReturn(snapshot);

        SportsQueryService service =
                new SportsQueryService(
                        snapshots,
                        new AppProperties(
                                "https://dna.example",
                                List.of("https://dna.example"),
                                "America/Sao_Paulo"
                        ),
                        Clock.fixed(
                                Instant.parse("2026-10-07T00:00:00Z"),
                                ZoneOffset.UTC
                        )
                );

        List<TopScorerView> result =
                service.topScorers(
                        new SportsFilter(123L, null),
                        null,
                        10
                );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).goals()).isEqualTo(10);
        assertThat(result.get(1).athleteName()).isEqualTo("Maria Souza");
        assertThat(result)
                .extracting(TopScorerView::position)
                .containsExactly(1, 2);
    }

    private TopScorerView scorer(
            int position,
            String name,
            int goals,
            TeamView team,
            String imageUrl
    ) {
        return new TopScorerView(
                position,
                null,
                name,
                imageUrl,
                team,
                goals,
                false
        );
    }
}

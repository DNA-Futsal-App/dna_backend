package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.CompetitionKey;
import br.com.dnafutsal.backend.sports.domain.CompetitionKeyView;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsCompetitionKeyTest {

    @Test
    void resolvesGoldSilverAndBronzeFromOfficialStandings() {
        SportsSnapshotService snapshots =
                mock(
                        SportsSnapshotService.class
                );

        TeamView gold =
                team("1", "Time Ouro");
        TeamView silver =
                team("2", "Time Prata");
        TeamView bronze =
                team("3", "Time Bronze");

        SportsSnapshot snapshot =
                new SportsSnapshot(
                        new SportsEventView(
                                910L,
                                "Campeonato Paulista",
                                2026,
                                "Sub-9",
                                "A1",
                                null
                        ),
                        List.of(
                                gold,
                                silver,
                                bronze
                        ),
                        List.of(),
                        List.of(
                                standing(
                                        "CHAVE OURO",
                                        gold
                                ),
                                standing(
                                        "CHAVE PRATA",
                                        silver
                                ),
                                standing(
                                        "CHAVE BRONZE",
                                        bronze
                                )
                        ),
                        List.of(),
                        Instant.parse(
                                "2026-10-07T00:00:00Z"
                        )
                );

        when(
                snapshots.snapshot(
                        910L
                )
        )
                .thenReturn(
                        snapshot
                );

        SportsQueryService service =
                new SportsQueryService(
                        snapshots,
                        new AppProperties(
                                "https://dna.example",
                                List.of(
                                        "https://dna.example"
                                ),
                                "America/Sao_Paulo"
                        ),
                        Clock.fixed(
                                Instant.parse(
                                        "2026-10-07T00:00:00Z"
                                ),
                                ZoneOffset.UTC
                        )
                );

        List<CompetitionKeyView> result =
                service.competitionKeys(
                        new SportsFilter(
                                910L,
                                null
                        )
                );

        assertThat(result)
                .extracting(
                        CompetitionKeyView::teamId,
                        CompetitionKeyView::key
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "1",
                                CompetitionKey.GOLD
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "2",
                                CompetitionKey.SILVER
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "3",
                                CompetitionKey.BRONZE
                        )
                );
    }

    private TeamView team(
            String id,
            String name
    ) {
        return new TeamView(
                id,
                name,
                null,
                null
        );
    }

    private StandingView standing(
            String group,
            TeamView team
    ) {
        return new StandingView(
                "Quartas de Final",
                group,
                1,
                team,
                1,
                1,
                0,
                0,
                1,
                0,
                1,
                3,
                1.0,
                1.0,
                0.0,
                3.0
        );
    }
}

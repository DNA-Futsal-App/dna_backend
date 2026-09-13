package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.sports.domain.MatchStatus;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SportsScraperMapperTest {

    private final SportsScraperMapper mapper = new SportsScraperMapper(
            new AppProperties("http://localhost:3000", List.of("http://localhost:3000"),
                    "America/Sao_Paulo"),
            Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void mapsSnapshotAndPreservesScraperSpecificData() {
        ScraperEvent event = new ScraperEvent(917, "Campeonato Paulista", 2026,
                "Principal", "A1", "https://source.example/events/917");
        List<ScraperTeam> teams = List.of(
                new ScraperTeam(10, "São Paulo", null, "https://source.example/teams/10"),
                new ScraperTeam(20, "Magnus", "https://img.example/magnus.png",
                        "https://source.example/teams/20")
        );
        List<ScraperGame> games = List.of(
                new ScraperGame(100L, "1ª Fase", LocalDate.of(2026, 4, 10), LocalTime.of(19, 30),
                        "Ginásio A", "SAO PAULO", "https://img.example/sao-paulo.png", 3,
                        "Magnus", null, 2, false, "https://source.example/sheets/100"),
                new ScraperGame(null, "1ª Fase", LocalDate.of(2026, 4, 20), LocalTime.of(18, 0),
                        null, "Magnus", null, null, "Equipe Convidada", null, null,
                        true, null)
        );
        List<ScraperStanding> standings = List.of(new ScraperStanding(
                "1ª Fase", "Grupo A", 1, "Sao Paulo", null, 12, 4, 4, 0, 0,
                20, 5, 15, 3.0, 5.0, 1.25, 0.95
        ));
        List<ScraperScorer> scorers = List.of(
                new ScraperScorer("1ª Fase", "Jogadora B", null, "Magnus", null, 5, false),
                new ScraperScorer("1ª Fase", "Jogadora A", "https://img.example/a.png",
                        "Sao Paulo", null, 9, false)
        );

        SportsSnapshot result = mapper.snapshot(
                new ScraperSnapshot(event, standings, games, teams, scorers, null), null);

        assertThat(result.event().eventId()).isEqualTo(917);
        assertThat(result.collectedAt()).isEqualTo(Instant.parse("2026-08-15T12:00:00Z"));
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches().get(0).homeTeam().id()).isEqualTo("10");
        assertThat(result.matches().get(0).homeTeam().logoUrl()).isEqualTo("https://img.example/sao-paulo.png");
        assertThat(result.matches().get(0).scheduledAt()).isEqualTo(Instant.parse("2026-04-10T22:30:00Z"));
        assertThat(result.matches().get(0).status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(result.matches().get(0).matchSheetUrl()).isEqualTo("https://source.example/sheets/100");
        assertThat(result.matches().get(1).id()).startsWith("generated:");
        assertThat(result.matches().get(1).awayTeam().id()).startsWith("name:equipe-convidada");
        assertThat(result.matches().get(1).status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(result.standings().get(0).team().id()).isEqualTo("10");
        assertThat(result.standings().get(0).technicalIndex()).isEqualTo(0.95);
        assertThat(result.topScorers()).extracting(TopScorerView::athleteName)
                .containsExactly("Jogadora A", "Jogadora B");
        assertThat(result.topScorers()).extracting(TopScorerView::position).containsExactly(1, 2);
    }

    @Test
    void usesPersonalDataOverrideWhenProvided() {
        ScraperEvent event = new ScraperEvent(917, "Campeonato", 2026, "Sub-13", "A1", null);
        ScraperScorer suppressed = new ScraperScorer("Fase", null, null, "Time A", null, 4, true);
        ScraperScorer exposed = new ScraperScorer("Fase", "Atleta A", "https://img.example/a.png",
                "Time A", null, 4, false);

        SportsSnapshot result = mapper.snapshot(
                new ScraperSnapshot(event, List.of(), List.of(), List.of(), List.of(suppressed), null),
                List.of(exposed));

        assertThat(result.topScorers()).singleElement().satisfies(scorer -> {
            assertThat(scorer.athleteName()).isEqualTo("Atleta A");
            assertThat(scorer.personalDataSuppressed()).isFalse();
        });
    }

    @Test
    void doesNotTreatFutureZeroZeroAsFinished() {
        ScraperEvent event =
                new ScraperEvent(
                        917,
                        "Campeonato Paulista",
                        2026,
                        "Principal",
                        "A1",
                        null
                );

        ScraperGame future =
                new ScraperGame(
                        100L,
                        "1ª Fase",
                        LocalDate.of(
                                2026,
                                9,
                                8
                        ),
                        LocalTime.of(
                                19,
                                0
                        ),
                        "Ginásio",
                        "Time A",
                        null,
                        0,
                        "Time B",
                        null,
                        0,
                        false,
                        "https://example.com/sumula"
                );

        SportsSnapshot result =
                mapper.snapshot(
                        new ScraperSnapshot(
                                event,
                                List.of(),
                                List.of(
                                        future
                                ),
                                List.of(),
                                List.of(),
                                null
                        ),
                        null
                );

        assertThat(
                result.matches()
                        .get(0)
                        .status()
        ).isEqualTo(
                MatchStatus.SCHEDULED
        );
    }

    @Test
    void marksPastMatchWithoutScoreAsResultPending() {
        ScraperEvent event =
                new ScraperEvent(
                        917,
                        "Campeonato Paulista",
                        2026,
                        "Principal",
                        "A1",
                        null
                );

        ScraperGame past =
                new ScraperGame(
                        100L,
                        "1ª Fase",
                        LocalDate.of(
                                2026,
                                8,
                                30
                        ),
                        LocalTime.of(
                                19,
                                0
                        ),
                        "Ginásio",
                        "Time A",
                        null,
                        null,
                        "Time B",
                        null,
                        null,
                        false,
                        null
                );

        SportsSnapshot result =
                mapper.snapshot(
                        new ScraperSnapshot(
                                event,
                                List.of(),
                                List.of(
                                        past
                                ),
                                List.of(),
                                List.of(),
                                null
                        ),
                        null
                );

        assertThat(
                result.matches()
                        .get(0)
                        .status()
        ).isEqualTo(
                MatchStatus.SCHEDULED
        );
    }
}

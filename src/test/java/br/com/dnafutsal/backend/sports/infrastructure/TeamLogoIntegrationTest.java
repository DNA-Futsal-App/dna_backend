package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.config.AppProperties;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TeamLogoIntegrationTest {
    @Test
    void preservesUpstreamLogosAcrossCatalogMatchesStandingsAndScorers() {
        var mapper = new SportsScraperMapper(new AppProperties("http://localhost:3000",
                List.of("http://localhost:3000"), "America/Sao_Paulo"), Clock.systemUTC());
        String catalogLogo = "https://source.example/clube-a.png";
        String scorerLogo = "https://source.example/clube-a-scorer.png";
        var teams = List.of(new ScraperTeam(10, "Clube A", catalogLogo, null));
        var event = new ScraperEvent(7001, "Paulista", 2026, "Sub-10", "A1", null);
        var game = new ScraperGame(1L, "Fase", LocalDate.of(2026, 1, 1), null, null,
                "Clube A", null, 1, "Sem escudo", null, 0, false, null);
        var standing = new ScraperStanding("Fase", "A", 1, "Clube A", null,
                3, 1, 1, 0, 0, 1, 0, 1, null, null, null, null);
        var scorer = new ScraperScorer("Fase", "Atleta teste", "https://source.example/photo.jpg",
                "Clube A Futsal", scorerLogo, 1, false);
        var result = mapper.snapshot(new ScraperSnapshot(event, List.of(standing), List.of(game),
                teams, List.of(scorer), null), null);

        assertThat(mapper.teams(7001, teams).get(0).logoUrl()).isEqualTo(catalogLogo);
        assertThat(result.teams().get(0).logoUrl()).isEqualTo(catalogLogo);
        assertThat(result.matches().get(0).homeTeam().logoUrl()).isEqualTo(catalogLogo);
        assertThat(result.standings().get(0).team().logoUrl()).isEqualTo(catalogLogo);
        assertThat(result.topScorers().get(0).team().id()).startsWith("name:");
        assertThat(result.topScorers().get(0).team().logoUrl()).isEqualTo(scorerLogo);
        assertThat(result.topScorers().get(0).athleteImageUrl()).isEqualTo("https://source.example/photo.jpg");
        assertThat(result.matches().get(0).awayTeam().logoUrl()).isNull();
    }

    @Test
    void doesNotBorrowALogoWhenTheCurrentSnapshotHasTwoTeamsWithTheSameName() {
        var mapper = new SportsScraperMapper(new AppProperties("http://localhost:3000",
                List.of("http://localhost:3000"), "America/Sao_Paulo"), Clock.systemUTC());
        var teams = List.of(
                new ScraperTeam(10, "Clube A", "https://source.example/clube-a-10.png", null),
                new ScraperTeam(20, "CLUBE A", "https://source.example/clube-a-20.png", null)
        );
        var event = new ScraperEvent(7001, "Paulista", 2026, "Sub-10", "A1", null);
        var scorer = new ScraperScorer("Fase", "Atleta teste", null, "Clube A", null, 1, false);
        var result = mapper.snapshot(new ScraperSnapshot(event, List.of(), List.of(), teams,
                List.of(scorer), null), null);

        assertThat(result.topScorers().get(0).team().logoUrl()).isNull();
        assertThat(result.topScorers().get(0).team().id()).startsWith("name:");
    }
}

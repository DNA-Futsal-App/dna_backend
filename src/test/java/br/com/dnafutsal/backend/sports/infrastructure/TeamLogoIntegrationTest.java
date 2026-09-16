package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.config.AppProperties;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TeamLogoIntegrationTest {
    @Test
    void enrichesCatalogMatchesStandingsAndScorersWithoutReplacingTheAthletePhoto() {
        String logo = "/team-logos/" + "b".repeat(64) + ".webp";
        var logos = new TeamLogoResolver(true, "https://site.example.test",
                new TeamLogoResolver.Manifest(1,
                        List.of(new TeamLogoResolver.Club("clube-a", logo)),
                        List.of(new TeamLogoResolver.Binding(7001, "10", "clube-a", List.of("Clube A", "Clube A Futsal")))));
        var mapper = new SportsScraperMapper(new AppProperties("http://localhost:3000",
                List.of("http://localhost:3000"), "America/Sao_Paulo"), Clock.systemUTC(), logos);
        var teams = List.of(new ScraperTeam(10, "Clube A", "https://source.example/old.png", null));
        var event = new ScraperEvent(7001, "Paulista", 2026, "Sub-10", "A1", null);
        var game = new ScraperGame(1L, "Fase", LocalDate.of(2026, 1, 1), null, null,
                "Clube A", null, 1, "Sem escudo", null, 0, false, null);
        var standing = new ScraperStanding("Fase", "A", 1, "Clube A", null,
                3, 1, 1, 0, 0, 1, 0, 1, null, null, null, null);
        var scorer = new ScraperScorer("Fase", "Atleta teste", "https://source.example/photo.jpg",
                "Clube A Futsal", null, 1, false);
        var result = mapper.snapshot(new ScraperSnapshot(event, List.of(standing), List.of(game),
                teams, List.of(scorer), null), null);
        String expected = "https://site.example.test" + logo;
        assertThat(mapper.teams(7001, teams).get(0).logoUrl()).isEqualTo(expected);
        assertThat(result.teams().get(0).logoUrl()).isEqualTo(expected);
        assertThat(result.matches().get(0).homeTeam().logoUrl()).isEqualTo(expected);
        assertThat(result.standings().get(0).team().logoUrl()).isEqualTo(expected);
        assertThat(result.topScorers().get(0).team().id()).startsWith("name:");
        assertThat(result.topScorers().get(0).team().logoUrl()).isEqualTo(expected);
        assertThat(result.topScorers().get(0).athleteImageUrl()).isEqualTo("https://source.example/photo.jpg");
        assertThat(result.matches().get(0).awayTeam().logoUrl()).isNull();
    }

    @Test
    void doesNotUseAStaleAliasWhenTheCurrentSnapshotHasTwoTeamsWithTheSameName() {
        var logos = new TeamLogoResolver(true, "https://site.example.test",
                new TeamLogoResolver.Manifest(1,
                        List.of(new TeamLogoResolver.Club("clube-a", "/team-logos/" + "b".repeat(64) + ".webp")),
                        List.of(new TeamLogoResolver.Binding(7001, "10", "clube-a", List.of("Clube A")))));
        var mapper = new SportsScraperMapper(new AppProperties("http://localhost:3000",
                List.of("http://localhost:3000"), "America/Sao_Paulo"), Clock.systemUTC(), logos);
        var teams = List.of(new ScraperTeam(10, "Clube A", null, null),
                new ScraperTeam(20, "CLUBE A", null, null));
        var event = new ScraperEvent(7001, "Paulista", 2026, "Sub-10", "A1", null);
        var scorer = new ScraperScorer("Fase", "Atleta teste", null, "Clube A", null, 1, false);
        var result = mapper.snapshot(new ScraperSnapshot(event, List.of(), List.of(), teams,
                List.of(scorer), null), null);
        assertThat(result.topScorers().get(0).team().logoUrl()).isNull();
        assertThat(result.topScorers().get(0).team().id()).startsWith("name:");
    }
}

package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.application.SportsQueryService;
import br.com.dnafutsal.backend.sports.application.SportsSnapshotService;
import br.com.dnafutsal.backend.sports.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwardGoldClassificationReuseTest {
    @Test
    void previewUsesExistingSportsKeysAndExcludesSilverTeams() {
        UUID id = UUID.randomUUID();
        AwardEdition edition = mock(AwardEdition.class);
        AwardEditionRepository editions = mock(AwardEditionRepository.class);
        SportsCatalogService catalog = mock(SportsCatalogService.class);
        SportsSnapshotService snapshots = mock(SportsSnapshotService.class);
        SportsQueryService keys = mock(SportsQueryService.class);

        when(editions.findById(id)).thenReturn(Optional.of(edition));
        when(edition.getSeason()).thenReturn(2026);
        when(catalog.categories(2026, 3)).thenReturn(
                List.of(new CatalogCategoryView(1, "Sub-7", 908)));
        when(snapshots.snapshot(908)).thenReturn(new SportsSnapshot(
                new SportsEventView(908, "Campeonato Paulista", 2026, "Sub-7", "A1", null),
                List.of(), List.of(), List.of(), List.of(),
                Instant.parse("2026-11-15T04:51:47Z")));
        when(catalog.teams(908)).thenReturn(List.of(
                new TeamView("12321", "A.D. BATEBOLA", null, null),
                new TeamView("12223", "Clube Prata", null, null)));
        when(keys.competitionKeys(new SportsFilter(908, null), "PAULISTA"))
                .thenReturn(List.of(
                        new CompetitionKeyView("12321", "A.D. BATEBOLA", CompetitionKey.GOLD),
                        new CompetitionKeyView("12223", "Clube Prata", CompetitionKey.SILVER)));

        AwardGoldEligibilityService service = new AwardGoldEligibilityService(
                editions, catalog, snapshots, keys, mock(AwardLiveRosterService.class),
                mock(JdbcTemplate.class), Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC));

        AwardGoldEligibilityService.Report report = service.preview(id, 908, 3, 1, "PAULISTA");
        assertThat(report.teams()).extracting(AwardGoldEligibilityService.Team::id)
                .containsExactly("12321");
        assertThat(report.ambiguousTeamIds()).isEmpty();
        assertThat(report.unresolvedTeams()).isEmpty();
    }
}

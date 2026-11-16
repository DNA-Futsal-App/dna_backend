package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.application.SportsQueryService;
import br.com.dnafutsal.backend.sports.application.SportsSnapshotService;
import br.com.dnafutsal.backend.sports.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwardGoldAutomaticEligibilityTest {
    @Mock AwardEditionRepository editions;
    @Mock SportsCatalogService sports;
    @Mock SportsSnapshotService snapshots;
    @Mock SportsQueryService sportsKeys;
    @Mock AwardLiveRosterService rosters;
    @Mock JdbcTemplate jdbc;

    @Test
    void goldOnlyWithoutAdministrativeApprovalOrDatabaseSnapshot() {
        AwardEdition edition = configure("Campeonato Paulista");
        when(sportsKeys.competitionKeys(new SportsFilter(908L, null), "PAULISTA"))
                .thenReturn(List.of(new CompetitionKeyView("10", "Clube Ouro", CompetitionKey.GOLD),
                        new CompetitionKeyView("20", "Clube Prata", CompetitionKey.SILVER)));

        AwardGoldEligibilityService service = service();
        assertThat(service.eligibleTeamIds(edition.getId(), 908L, 3L, 1L)).containsExactly("10");
        assertThatThrownBy(() -> service.requireEligible(edition.getId(), 908L, 3L, 1L, "20"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chave Ouro");
        service.requireEligible(edition.getId(), 908L, 3L, 1L, "10");
        verifyNoInteractions(jdbc);
    }

    @Test
    void unionUsesTheExistingStageAwareClassification() {
        AwardEdition edition = configure("Torneio União");
        when(sportsKeys.competitionKeys(new SportsFilter(908L, null), "UNIAO"))
                .thenReturn(List.of(new CompetitionKeyView("10", "Clube Ouro", CompetitionKey.GOLD)));
        assertThat(service().eligibleTeamIds(edition.getId(), 908L, 3L, 1L)).containsExactly("10");
        verifyNoInteractions(jdbc);
    }

    private AwardGoldEligibilityService service() {
        return new AwardGoldEligibilityService(editions, sports, snapshots, sportsKeys,
                rosters, jdbc, Clock.systemUTC());
    }

    private AwardEdition configure(String title) {
        AwardEdition edition = new AwardEdition("legacy-2026", "Premio DNA", 2026,
                AwardEditionStatus.OPEN, null, null);
        TeamView gold = new TeamView("10", "Clube Ouro", null, null);
        TeamView silver = new TeamView("20", "Clube Prata", null, null);
        when(editions.findById(edition.getId())).thenReturn(Optional.of(edition));
        when(sports.categories(2026, 3L)).thenReturn(List.of(new CatalogCategoryView(1L, "Sub-7", 908L)));
        when(snapshots.snapshot(908L)).thenReturn(new SportsSnapshot(
                new SportsEventView(908L, title, 2026, "Sub-7", "A1", null),
                List.of(gold, silver), List.of(), List.of(), List.of(), Instant.parse("2026-11-16T00:00:00Z")));
        when(sports.teams(908L)).thenReturn(List.of(gold, silver));
        return edition;
    }
}

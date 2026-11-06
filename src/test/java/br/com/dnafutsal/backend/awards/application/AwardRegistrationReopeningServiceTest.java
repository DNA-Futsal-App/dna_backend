package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardRegistrationReopeningRequest;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwardRegistrationReopeningServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock AwardEditionRepository editions;
    @Mock AwardPreVoteEditionLockRepository locks;
    @Mock AwardPreVoteFinalistRepository finalists;
    @Mock SportsCatalogService catalog;
    @Mock AwardEdition edition;

    private final UUID editionId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-05T18:00:00Z");

    private AwardRegistrationReopeningService service() {
        return new AwardRegistrationReopeningService(jdbc, editions, locks, finalists, catalog,
                Clock.fixed(now, java.time.ZoneOffset.UTC));
    }

    private AwardRegistrationReopeningRequest request() {
        return new AwardRegistrationReopeningRequest(908L, 3L, 12L,
                AwardRegistrationGender.FEMALE, AwardRegistrationContestCategory.BEST_SAVE,
                now.minusSeconds(60), now.plusSeconds(3600));
    }

    @Test
    void deniesReopeningWhenFinalistsAlreadyExistInExactContext() {
        when(locks.lockEdition(editionId)).thenReturn(Optional.of(edition));
        when(edition.getSeason()).thenReturn(2026);
        when(catalog.divisions(2026, "Paulista Feminino"))
                .thenReturn(List.of(new CatalogItemView(3L, "A1")));
        when(catalog.categories(2026, "Paulista Feminino", 3L))
                .thenReturn(List.of(new CatalogCategoryView(12L, "Sub-8", 908L)));
        when(finalists.existsByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, 908L, 3L, 12L, AwardRegistrationGender.FEMALE,
                AwardRegistrationContestCategory.BEST_SAVE)).thenReturn(true);

        assertThatThrownBy(() -> service().open(editionId, request()))
                .hasMessageContaining("finalistas");
        verifyNoInteractions(jdbc);
    }

    @Test
    void refusesSelectiveReopeningWhileGlobalRegistrationsAreOpen() {
        when(locks.lockEdition(editionId)).thenReturn(Optional.of(edition));
        when(edition.isRegistrationsOpen()).thenReturn(true);

        assertThatThrownBy(() -> service().open(editionId, request()))
                .hasMessageContaining("período geral");
        verifyNoInteractions(jdbc, finalists);
    }
}

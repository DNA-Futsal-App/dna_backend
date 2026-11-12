package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoachCategoryLinkServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AwardEditionRepository editions = mock(AwardEditionRepository.class);
    private final SportsCatalogService catalog = mock(SportsCatalogService.class);
    private final AwardGoldEligibilityService gold = mock(AwardGoldEligibilityService.class);
    private final CurrentUserService user = mock(CurrentUserService.class);
    private final Instant now = Instant.parse("2026-11-11T15:00:00Z");
    private final AwardEdition edition = new AwardEdition(
            "premio-dna-2026", "Prêmio DNA Futsal 2026", 2026, AwardEditionStatus.DRAFT, null, null);
    private final CoachCategoryLinkService service = new CoachCategoryLinkService(
            jdbc, editions, catalog, gold, new TokenSupport(), user,
            new AppProperties("https://dna.example/", List.of("https://dna.example"), "America/Sao_Paulo"),
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void rejectsGenderCategoryMismatchWithoutIssuingLink() {
        when(editions.findById(edition.getId())).thenReturn(Optional.of(edition));
        when(catalog.categories(2026, "Paulista Feminino", 4)).thenReturn(List.of(
                new CatalogCategoryView(71, "Sub-12", 901)));
        assertThatThrownBy(() -> service.create(edition.getId(), new CoachCategoryLinkService.CreateRequest(
                902, 4, 71, AwardRegistrationGender.FEMALE, now.plusSeconds(3600))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("modalidade/divisão");
        verifyNoInteractions(gold, jdbc);
    }

    @Test
    void doesNotIssueLinkWhenGoldEligibilityHasNotBeenFrozen() {
        when(editions.findById(edition.getId())).thenReturn(Optional.of(edition));
        when(catalog.categories(2026, "Paulista", 4)).thenReturn(List.of(
                new CatalogCategoryView(71, "Sub-12", 901)));
        when(gold.prepared(edition.getId(), 901, 4, 71)).thenReturn(
                new AwardGoldEligibilityService.Report(edition.getId(), 901, 4, 71,
                        "PAULISTA", now, null, false, List.of(), List.of(), List.of()));
        assertThatThrownBy(() -> service.create(edition.getId(), new CoachCategoryLinkService.CreateRequest(
                901, 4, 71, AwardRegistrationGender.MALE, now.plusSeconds(3600))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chave Ouro");
        verifyNoInteractions(jdbc);
    }

    @Test
    void generatesOpaqueInvitationOnlyForPreparedGoldCategory() {
        when(editions.findById(edition.getId())).thenReturn(Optional.of(edition));
        when(catalog.categories(2026, "Paulista", 4)).thenReturn(List.of(
                new CatalogCategoryView(71, "Sub-12", 901)));
        when(gold.prepared(edition.getId(), 901, 4, 71)).thenReturn(
                new AwardGoldEligibilityService.Report(edition.getId(), 901, 4, 71,
                        "PAULISTA", now, now, true,
                        List.of(new AwardGoldEligibilityService.Team("11", "Equipe Ouro", 12)),
                        List.of(), List.of()));
        when(user.userId()).thenReturn(UUID.randomUUID());
        var created = service.create(edition.getId(), new CoachCategoryLinkService.CreateRequest(
                901, 4, 71, AwardRegistrationGender.MALE, now.plusSeconds(3600)));
        assertThat(created.accessUrl()).startsWith("https://dna.example/premio-dna/tecnicos/convite/");
        assertThat(created.link().gender()).isEqualTo("MALE");
        assertThat(created.link().status()).isEqualTo("AVAILABLE");
        assertThat(created.link().available()).isTrue();
        verify(jdbc).update(any(String.class), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }
}

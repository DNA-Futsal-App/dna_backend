package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SportsPreferenceValidatorTest {
    private final SportsCatalogService catalog = mock(SportsCatalogService.class);
    private final SportsPreferenceValidator validator = new SportsPreferenceValidator(catalog);

    @BeforeEach
    void femaleCatalog() {
        when(catalog.event(926L)).thenReturn(new SportsEventView(
                926, "Paulista Feminino", 2026, "Sub-12", "A2", null));
        when(catalog.divisions(2026, "Paulista Feminino"))
                .thenReturn(List.of(new CatalogItemView(10, "A2")));
        when(catalog.categories(2026, "Paulista Feminino", 10))
                .thenReturn(List.of(new CatalogCategoryView(20, "Sub-12", 926)));
    }

    @Test
    void acceptsFemaleEventWithoutFollowedTeam() {
        assertThatCode(() -> validator.validate(SportsCompetitionCode.PAULISTA_FEMININO,
                926L, "10", "20", null)).doesNotThrowAnyException();
        verify(catalog, never()).teams(926L);
    }

    @Test
    void acceptsFemaleTeamFromSameEvent() {
        when(catalog.teams(926)).thenReturn(List.of(new TeamView("11", "Equipe", null, null)));
        assertThatCode(() -> validator.validate(SportsCompetitionCode.PAULISTA_FEMININO,
                926L, "10", "20", "11")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTeamFromDifferentEvent() {
        when(catalog.teams(926)).thenReturn(List.of(new TeamView("11", "Equipe", null, null)));
        assertInvalid(() -> validator.validate(SportsCompetitionCode.PAULISTA_FEMININO,
                926L, "10", "20", "99"));
    }

    @Test
    void rejectsWrongCategoryForEvent() {
        assertInvalid(() -> validator.validate(SportsCompetitionCode.PAULISTA_FEMININO,
                926L, "10", "21", null));
    }

    @Test
    void rejectsMaleTitleForFemaleEvent() {
        when(catalog.divisions(2026, "Paulista")).thenReturn(List.of(new CatalogItemView(3, "A1")));
        assertInvalid(() -> validator.validate(SportsCompetitionCode.PAULISTA,
                926L, "10", "20", null));
    }

    @Test
    void rejectsIncompleteOrMalformedSelection() {
        assertThatCode(() -> validator.validate(SportsCompetitionCode.PAULISTA_FEMININO,
                null, null, null, null)).doesNotThrowAnyException();
        assertInvalid(() -> validator.validate(SportsCompetitionCode.PAULISTA_FEMININO,
                926L, "abc", "20", null));
    }

    @Test
    void leavesLegacyRequestsUntouched() {
        assertThatCode(() -> validator.validate(null, 917L, "3", "7", "10"))
                .doesNotThrowAnyException();
        verify(catalog, never()).teams(anyLong());
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(BusinessException.class,
                problem -> assertThat(problem.code()).isEqualTo("SPORTS_PREFERENCE_INVALID"));
    }
}

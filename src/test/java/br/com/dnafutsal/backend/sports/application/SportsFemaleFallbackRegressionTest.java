package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.application.ProfileService;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SportsFemaleFallbackRegressionTest {
    private final ProfileService profiles = mock(ProfileService.class);
    private final SportsDefaultEventResolver defaults = mock(SportsDefaultEventResolver.class);
    private final SportsFilterResolver resolver = new SportsFilterResolver(profiles, defaults);
    private final UUID userId = UUID.randomUUID();

    private UserProfileResponse profile(SportsCompetitionCode code, Long eventId) {
        return new UserProfileResponse(userId, "Pessoa", "pessoa@example.com", "+5511999999999",
                null, eventId, null, null, null, true,
                Instant.parse("2026-01-01T00:00:00Z"), code);
    }

    @Test
    void femalePreferenceWithoutEventDoesNotFallBackToMale() {
        when(profiles.get(userId)).thenReturn(profile(SportsCompetitionCode.PAULISTA_FEMININO, null));
        assertThatThrownBy(() -> resolver.resolveCompetition(userId, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SPORTS_PREFERENCE_INCOMPLETE"));
        assertThatThrownBy(() -> resolver.resolve(userId, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SPORTS_PREFERENCE_INCOMPLETE"));
        verifyNoInteractions(defaults);
    }

    @Test
    void femalePreferenceWithEventUsesOnlySelectedEvent() {
        when(profiles.get(userId)).thenReturn(profile(SportsCompetitionCode.PAULISTA_FEMININO, 926L));
        assertThat(resolver.resolveCompetition(userId, null, null).eventId()).isEqualTo(926L);
        assertThat(resolver.resolve(userId, null, null).eventId()).isEqualTo(926L);
        verifyNoInteractions(defaults);
    }

    @Test
    void legacyUsersRetainOriginalMaleDefault() {
        when(profiles.get(userId)).thenReturn(profile(null, null));
        when(defaults.resolveCurrentEventId()).thenReturn(917L);
        assertThat(resolver.resolveCompetition(userId, null, null).eventId()).isEqualTo(917L);
    }

    @Test
    void explicitBrowseEventOverridesIncompletePreference() {
        when(profiles.get(userId)).thenReturn(profile(SportsCompetitionCode.PAULISTA_FEMININO, null));
        assertThat(resolver.resolveCompetition(userId, 926L, null).eventId()).isEqualTo(926L);
        verifyNoInteractions(defaults);
    }
}

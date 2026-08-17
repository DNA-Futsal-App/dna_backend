package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.application.ProfileService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SportsFilterResolverTest {

    private final ProfileService profiles = mock(ProfileService.class);
    private final SportsFilterResolver resolver = new SportsFilterResolver(profiles);
    private final UUID userId = UUID.randomUUID();

    @Test
    void usesProfilePreferencesWhenRequestOmitsFilters() {
        when(profiles.get(userId)).thenReturn(profile(917L, "10"));

        var result = resolver.resolve(userId, null, null);

        assertThat(result.eventId()).isEqualTo(917);
        assertThat(result.teamId()).isEqualTo("10");
    }

    @Test
    void requestFiltersOverrideProfilePreferences() {
        when(profiles.get(userId)).thenReturn(profile(917L, "10"));

        var result = resolver.resolve(userId, 918L, "20");

        assertThat(result.eventId()).isEqualTo(918);
        assertThat(result.teamId()).isEqualTo("20");
    }

    @Test
    void requiresEventInRequestOrProfile() {
        when(profiles.get(userId)).thenReturn(profile(null, null));

        assertThatThrownBy(() -> resolver.resolve(userId, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SPORTS_PREFERENCE_REQUIRED"));
    }

    private UserProfileResponse profile(Long eventId, String teamId) {
        return new UserProfileResponse(userId, "User", "user@example.com", "+5511999999999",
                null, eventId, null, null, teamId, true, Instant.parse("2026-01-01T00:00:00Z"));
    }
}

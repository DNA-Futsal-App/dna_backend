package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.application.ProfileService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SportsFilterResolverTest {

    private final ProfileService profiles =
            mock(ProfileService.class);

    private final SportsDefaultEventResolver defaultEvents =
            mock(SportsDefaultEventResolver.class);

    private final SportsFilterResolver resolver =
            new SportsFilterResolver(
                    profiles,
                    defaultEvents
            );

    private final UUID userId =
            UUID.randomUUID();

    @Test
    void usesProfilePreferencesWhenRequestOmitsFilters() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        917L,
                        "10"
                )
        );

        var result =
                resolver.resolve(
                        userId,
                        null,
                        null
                );

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.teamId()
        ).isEqualTo("10");

        verifyNoInteractions(
                defaultEvents
        );
    }

    @Test
    void requestFiltersOverrideProfilePreferences() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        917L,
                        "10"
                )
        );

        var result =
                resolver.resolve(
                        userId,
                        918L,
                        "20"
                );

        assertThat(
                result.eventId()
        ).isEqualTo(918);

        assertThat(
                result.teamId()
        ).isEqualTo("20");

        verifyNoInteractions(
                defaultEvents
        );
    }

    @Test
    void usesDefaultEventWhenProfileHasNoEvent() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        null,
                        null
                )
        );

        when(
                defaultEvents
                        .resolveCurrentEventId()
        ).thenReturn(
                917L
        );

        var result =
                resolver.resolve(
                        userId,
                        null,
                        null
                );

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.teamId()
        ).isNull();
    }

    @Test
    void competitionFilterDoesNotReuseProfileTeam() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        917L,
                        "10"
                )
        );

        var result =
                resolver.resolveCompetition(
                        userId,
                        null,
                        null
                );

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.teamId()
        ).isNull();
    }

    @Test
    void competitionFilterAcceptsExplicitTeam() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        917L,
                        "10"
                )
        );

        var result =
                resolver.resolveCompetition(
                        userId,
                        null,
                        "20"
                );

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.teamId()
        ).isEqualTo("20");
    }

    @Test
    void acceptsRequestedTeamWithDefaultEvent() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        null,
                        null
                )
        );

        when(
                defaultEvents
                        .resolveCurrentEventId()
        ).thenReturn(
                917L
        );

        var result =
                resolver.resolve(
                        userId,
                        null,
                        "20"
                );

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.teamId()
        ).isEqualTo("20");
    }

    @Test
    void doesNotReuseProfileTeamWhenRequestedEventDiffers() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        917L,
                        "10"
                )
        );

        var result =
                resolver.resolve(
                        userId,
                        918L,
                        null
                );

        assertThat(
                result.eventId()
        ).isEqualTo(918);

        assertThat(
                result.teamId()
        ).isNull();

        verifyNoInteractions(
                defaultEvents
        );
    }

    @Test
    void reusesProfileTeamWhenRequestedEventMatchesProfileEvent() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        917L,
                        "10"
                )
        );

        var result =
                resolver.resolve(
                        userId,
                        917L,
                        null
                );

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.teamId()
        ).isEqualTo("10");

        verifyNoInteractions(
                defaultEvents
        );
    }

    @Test
    void ignoresStaleProfileTeamWhenUsingDefaultEvent() {
        when(
                profiles.get(userId)
        ).thenReturn(
                profile(
                        null,
                        "999"
                )
        );

        when(
                defaultEvents
                        .resolveCurrentEventId()
        ).thenReturn(
                917L
        );

        var result =
                resolver.resolve(
                        userId,
                        null,
                        null
                );

        assertThat(
                result.eventId()
        ).isEqualTo(917);

        assertThat(
                result.teamId()
        ).isNull();
    }

    private UserProfileResponse profile(
            Long eventId,
            String teamId
    ) {
        return new UserProfileResponse(
                userId,
                "User",
                "user@example.com",
                "+5511999999999",
                null,
                eventId,
                null,
                null,
                teamId,
                true,
                Instant.parse(
                        "2026-01-01T00:00:00Z"
                )
        );
    }
}
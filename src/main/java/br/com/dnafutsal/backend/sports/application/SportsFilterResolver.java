package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.application.ProfileService;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class SportsFilterResolver {

    private final ProfileService profiles;
    private final SportsDefaultEventResolver defaultEvents;

    public SportsFilterResolver(
            ProfileService profiles,
            SportsDefaultEventResolver defaultEvents
    ) {
        this.profiles = profiles;
        this.defaultEvents = defaultEvents;
    }

    public SportsFilter resolve(
            UUID userId,
            Long requestedEventId,
            String requestedTeamId
    ) {
        UserProfileResponse profile =
                profiles.get(userId);

        Long profileEventId =
                profile.eventId();

        long selectedEventId;
        boolean canReuseProfileTeam;

        if (requestedEventId != null) {
            selectedEventId =
                    requestedEventId;

            canReuseProfileTeam =
                    Objects.equals(
                            requestedEventId,
                            profileEventId
                    );
        } else if (profileEventId != null) {
            selectedEventId =
                    profileEventId;

            canReuseProfileTeam =
                    true;
        } else {
            selectedEventId =
                    defaultEvents
                            .resolveCurrentEventId();

            /*
             * O usuário não possui evento salvo.
             * Portanto um teamId antigo do perfil não
             * pode ser presumido como pertencente ao
             * evento padrão atual.
             */
            canReuseProfileTeam =
                    false;
        }

        String selectedTeamId =
                clean(requestedTeamId);

        if (
                selectedTeamId == null &&
                        canReuseProfileTeam
        ) {
            selectedTeamId =
                    clean(profile.teamId());
        }

        return new SportsFilter(
                selectedEventId,
                selectedTeamId
        );
    }

    private String clean(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return null;
        }

        return value.trim();
    }
}
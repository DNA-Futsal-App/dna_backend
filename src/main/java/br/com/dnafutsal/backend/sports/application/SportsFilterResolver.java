package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.application.ProfileService;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;
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

        long selectedEventId =
                selectEventId(
                        profile,
                        requestedEventId
                );
        boolean canReuseProfileTeam;

        if (requestedEventId != null) {

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
            // Do not bypass the female-selection guard when resolving My Team.
            selectedEventId = selectEventId(profile, null);
            canReuseProfileTeam = false;
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

    public SportsFilter resolveCompetition(
            UUID userId,
            Long requestedEventId,
            String requestedTeamId
    ) {
        UserProfileResponse profile =
                profiles.get(userId);

        long selectedEventId =
                selectEventId(
                        profile,
                        requestedEventId
                );
        return new SportsFilter(
                selectedEventId,
                clean(requestedTeamId)
        );
    }

    private long selectEventId(
            UserProfileResponse profile,
            Long requestedEventId
    ) {
        if (requestedEventId != null) {
            return requestedEventId;
        }

        if (profile.eventId() != null) {
            return profile.eventId();
        }

        if (profile.competitionCode() == SportsCompetitionCode.PAULISTA_FEMININO) {
            throw Errors.badRequest(
                    "SPORTS_PREFERENCE_INCOMPLETE",
                    "Escolha uma divisão e categoria do Paulista Feminino no perfil antes de acompanhar os resultados."
            );
        }

        return defaultEvents
                .resolveCurrentEventId();
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
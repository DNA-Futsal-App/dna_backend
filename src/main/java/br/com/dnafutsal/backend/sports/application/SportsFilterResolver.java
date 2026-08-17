package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.application.ProfileService;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SportsFilterResolver {

    private final ProfileService profiles;

    public SportsFilterResolver(ProfileService profiles) {
        this.profiles = profiles;
    }

    public SportsFilter resolve(UUID userId, Long eventId, String teamId) {
        UserProfileResponse profile = profiles.get(userId);
        Long selectedEventId = eventId == null ? profile.eventId() : eventId;
        if (selectedEventId == null) {
            throw Errors.badRequest("SPORTS_PREFERENCE_REQUIRED",
                    "Escolha uma competição no perfil ou informe eventId na consulta.");
        }
        return new SportsFilter(selectedEventId, first(teamId, profile.teamId()));
    }

    private String first(String requested, String preferred) {
        return requested == null || requested.isBlank() ? preferred : requested.trim();
    }
}

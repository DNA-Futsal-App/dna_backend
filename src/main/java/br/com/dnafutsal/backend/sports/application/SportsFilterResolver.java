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

    public SportsFilter resolve(UUID userId, String categoryId, String divisionId, String teamId) {
        UserProfileResponse profile = profiles.get(userId);
        SportsFilter filter = new SportsFilter(first(categoryId, profile.categoryId()),
                first(divisionId, profile.divisionId()), first(teamId, profile.teamId()));
        if (filter.categoryId() == null || filter.divisionId() == null) {
            throw Errors.badRequest("SPORTS_PREFERENCE_REQUIRED",
                    "Escolha categoria e divisão no perfil ou informe os filtros na consulta.");
        }
        return filter;
    }

    private String first(String requested, String preferred) {
        return requested == null || requested.isBlank() ? preferred : requested.trim();
    }
}

package br.com.dnafutsal.backend.sports.domain;

import java.util.List;

public record SportsTeamDetailsView(
        long eventId,
        String teamId,
        String name,
        String logoUrl,
        List<SportsPersonView> athletes,
        List<SportsPersonView> staff,
        boolean personalDataSuppressed,
        String sourceUrl
) {
}

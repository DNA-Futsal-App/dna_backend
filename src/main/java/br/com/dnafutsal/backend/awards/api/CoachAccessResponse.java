package br.com.dnafutsal.backend.awards.api;

import java.util.UUID;

public record CoachAccessResponse(
        UUID voterId,
        UUID editionId,
        String editionSlug,
        String editionName,
        int season,
        String coachName,
        long eventId,
        long divisionId,
        long categoryId,
        String teamId,
        String teamName
) {
}

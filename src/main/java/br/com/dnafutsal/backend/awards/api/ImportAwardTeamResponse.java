package br.com.dnafutsal.backend.awards.api;

import java.util.List;
import java.util.UUID;

public record ImportAwardTeamResponse(
        UUID editionId,
        long eventId,
        long divisionId,
        long categoryId,
        String teamId,
        String teamName,
        int athletesFound,
        int coachesFound,
        int created,
        int updated,
        int deactivated,
        int positionsPending,
        List<AwardCandidateResponse> candidates
) {
}

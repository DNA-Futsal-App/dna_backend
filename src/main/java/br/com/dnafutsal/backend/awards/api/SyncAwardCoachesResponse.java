package br.com.dnafutsal.backend.awards.api;

import java.util.List;
import java.util.UUID;

public record SyncAwardCoachesResponse(
        UUID editionId,
        long eventId,
        long divisionId,
        long categoryId,
        int teamsScanned,
        int coachesFound,
        List<AwardCandidateResponse> coaches
) {
}

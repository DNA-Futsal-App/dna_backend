package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CoachBallotResponse(
        UUID ballotId,
        UUID editionId,
        Instant submittedAt,
        List<CoachBallotChoiceResponse> votes
) {
}

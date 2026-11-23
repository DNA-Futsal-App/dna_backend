package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Votos existentes de uma credencial, restritos a uma única edição. */
public record AdminCoachBallotsResponse(
        UUID credentialId,
        UUID editionId,
        List<ContextBallot> ballots
) {
    public record ContextBallot(
            UUID contextId,
            UUID ballotId,
            long eventId,
            long divisionId,
            long categoryId,
            String teamId,
            String teamName,
            Instant submittedAt,
            List<CoachBallotChoiceResponse> votes
    ) { }
}

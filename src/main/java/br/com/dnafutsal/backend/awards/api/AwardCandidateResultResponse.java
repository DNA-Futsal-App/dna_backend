
package br.com.dnafutsal.backend.awards.api;

import java.util.UUID;

public record AwardCandidateResultResponse(
        int rank,
        UUID candidateId,
        String candidateName,
        String teamId,
        String teamName,
        String teamLogoUrl,
        int votes,
        double percentage
) {
}

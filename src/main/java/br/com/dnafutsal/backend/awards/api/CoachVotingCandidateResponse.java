package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardCandidate;

import java.util.UUID;

public record CoachVotingCandidateResponse(
        UUID id,
        String name,
        String secondaryName,
        String imageUrl,
        String type,
        String positionCode,
        String teamId,
        String teamName,
        String teamLogoUrl
) {

    public static CoachVotingCandidateResponse from(
            AwardCandidate candidate
    ) {
        return new CoachVotingCandidateResponse(
                candidate.getId(),
                candidate.getName(),
                candidate.getSecondaryName(),
                candidate.getImageUrl(),
                candidate.getCandidateType().name(),
                candidate.getPositionCode(),
                candidate.getTeamId(),
                candidate.getTeamName(),
                candidate.getTeamLogoUrl()
        );
    }
}

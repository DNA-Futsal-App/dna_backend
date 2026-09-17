package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardCandidate;

import java.time.Instant;
import java.util.UUID;

public record AwardCandidateResponse(
        UUID id,
        String type,
        String source,
        String externalPersonId,
        String name,
        String secondaryName,
        String positionCode,
        String sourceRole,
        long eventId,
        long divisionId,
        long categoryId,
        String teamId,
        String teamName,
        String teamLogoUrl,
        String imageUrl,
        boolean active,
        Instant importedAt
) {

    public static AwardCandidateResponse from(
            AwardCandidate candidate
    ) {
        return new AwardCandidateResponse(
                candidate.getId(),
                candidate.getCandidateType().name(),
                candidate.getSource().name(),
                candidate.getExternalPersonId(),
                candidate.getName(),
                candidate.getSecondaryName(),
                candidate.getPositionCode(),
                candidate.getSourceRole(),
                candidate.getEventId(),
                candidate.getDivisionId(),
                candidate.getCategoryId(),
                candidate.getTeamId(),
                candidate.getTeamName(),
                candidate.getTeamLogoUrl(),
                candidate.getImageUrl(),
                candidate.isActive(),
                candidate.getImportedAt()
        );
    }
}

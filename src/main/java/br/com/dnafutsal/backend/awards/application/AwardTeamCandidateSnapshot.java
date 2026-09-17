package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;

import java.util.List;
import java.util.UUID;

record AwardTeamCandidateSnapshot(
        UUID editionId,
        long eventId,
        long divisionId,
        long categoryId,
        String teamId,
        String teamName,
        String teamLogoUrl,
        List<Candidate> candidates
) {

    record Candidate(
            AwardCandidateType type,
            String sourceKey,
            String externalPersonId,
            String name,
            String secondaryName,
            String sourceRole,
            String imageUrl
    ) {
    }
}

package br.com.dnafutsal.backend.awards.api;

import java.util.UUID;

public record CoachBallotChoiceResponse(
        UUID voteCategoryId,
        String voteCategoryCode,
        String voteCategoryLabel,
        UUID candidateId,
        String candidateName,
        String teamId,
        String teamName
) {
}

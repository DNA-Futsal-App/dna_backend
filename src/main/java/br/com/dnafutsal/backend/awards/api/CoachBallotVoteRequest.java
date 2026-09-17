package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CoachBallotVoteRequest(
        @NotNull UUID voteCategoryId,
        @NotNull UUID candidateId
) {
}

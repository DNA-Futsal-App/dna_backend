package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCoachInviteRequest(
        @NotNull UUID editionId,
        @NotNull UUID coachCandidateId
) {
}

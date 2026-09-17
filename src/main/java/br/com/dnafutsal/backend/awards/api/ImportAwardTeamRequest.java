package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ImportAwardTeamRequest(
        @NotNull UUID editionId,
        @Positive long eventId,
        @Positive long divisionId,
        @Positive long categoryId,
        @Positive long teamId
) {
}

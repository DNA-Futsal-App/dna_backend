package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ClaimCoachInviteRequest(
        @NotBlank @Size(max = 200) String token,
        @Positive long eventId,
        @Positive long divisionId,
        @Positive long categoryId,
        @NotBlank @Size(max = 100) String teamId
) {
}

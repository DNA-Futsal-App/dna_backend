package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record OpenAwardEditionRequest(
        @NotNull Instant votingOpensAt,
        @NotNull Instant votingClosesAt
) {
}

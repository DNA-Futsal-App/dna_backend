package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AwardPopularBatchWindowRequest(@Valid @NotNull AwardPopularBatchSelection selection,
        @NotNull Instant opensAt, @NotNull Instant closesAt) { }

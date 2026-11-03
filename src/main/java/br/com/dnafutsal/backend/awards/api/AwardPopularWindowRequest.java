package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AwardPopularWindowRequest(@NotNull Instant opensAt, @NotNull Instant closesAt) { }

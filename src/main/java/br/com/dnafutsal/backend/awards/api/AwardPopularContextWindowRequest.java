package br.com.dnafutsal.backend.awards.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public record AwardPopularContextWindowRequest(@NotNull @Valid AwardPopularContext context,
       @NotNull Instant opensAt, @NotNull Instant closesAt) { }

package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AwardPopularVoteRequest(@NotNull @Valid AwardPopularContext context,
                                      @NotNull UUID entryId) { }

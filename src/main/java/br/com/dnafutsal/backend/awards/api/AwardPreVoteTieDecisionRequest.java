package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AwardPreVoteTieDecisionRequest(
        @NotNull @Valid AwardPreVoteContextRequest context,
        int finalistCount,
        @NotNull @Size(min = 3, max = 4) List<@NotNull UUID> selectedEntryIds
) {}

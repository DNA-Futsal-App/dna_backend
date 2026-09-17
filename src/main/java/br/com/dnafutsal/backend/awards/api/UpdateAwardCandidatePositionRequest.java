package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardPlayerPosition;
import jakarta.validation.constraints.NotNull;

public record UpdateAwardCandidatePositionRequest(
        @NotNull AwardPlayerPosition position
) {
}

package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AwardPreVoteContextRequest(
        @Min(1) long eventId,
        @Min(1) long divisionId,
        @Min(1) long categoryId,
        @NotNull AwardRegistrationGender gender,
        @NotNull AwardRegistrationContestCategory contestCategory
) {}

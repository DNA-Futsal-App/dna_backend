package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record AwardRegistrationReopeningRequest(
        @Positive long eventId, @Positive long divisionId, @Positive long categoryId,
        @NotNull AwardRegistrationGender gender,
        @NotNull AwardRegistrationContestCategory contestCategory,
        @NotNull Instant opensAt, @NotNull Instant closesAt
) {}

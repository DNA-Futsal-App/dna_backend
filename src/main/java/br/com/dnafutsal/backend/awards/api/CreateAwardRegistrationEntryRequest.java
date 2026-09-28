package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAwardRegistrationEntryRequest(
        @NotNull
        AwardRegistrationContestCategory contestCategory,

        @NotNull
        AwardRegistrationMediaSource sourceType,

        @Size(max = 2000)
        String externalUrl
) {
}

package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;

public record AwardRegistrationContestCategoryResponse(
        AwardRegistrationContestCategory code,
        String label
) {
}

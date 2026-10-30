package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;

public record AwardPreVoteContextResponse(
        long eventId,
        long divisionId,
        String divisionName,
        long categoryId,
        String categoryName,
        AwardRegistrationGender gender,
        AwardRegistrationContestCategory contestCategory,
        String contestCategoryLabel
) {}

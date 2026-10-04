package br.com.dnafutsal.backend.awards.api;

import java.util.List;

public record AwardRegistrationContextResponse(
        String editionName,
        int season,
        boolean registrationsOpen,
        long maxUploadBytes,
        int maxDurationSeconds,
        List<AwardRegistrationContestCategoryResponse> contestCategories
) {
}

package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;

public record AwardRegistrationReopeningEligibilityResponse(
        AwardRegistrationContestCategory contestCategory, String label,
        boolean hasFinalists, String reopeningStatus
) {}

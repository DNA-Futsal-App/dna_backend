package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import java.time.Instant;
import java.util.UUID;

public record AwardRegistrationReopeningResponse(
        UUID id, UUID editionId, long eventId, long divisionId, long categoryId,
        AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory,
        Instant opensAt, Instant closesAt, Instant closedAt, String status
) {}

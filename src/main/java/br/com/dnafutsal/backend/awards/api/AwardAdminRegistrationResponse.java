package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AwardAdminRegistrationResponse(
        UUID id,
        long registrationNumber,
        AwardRegistrationStatus status,
        UUID representativeUserId,
        String representativeName,
        String representativeEmail,
        String athleteName,
        String athleteInstagram,
        AwardRegistrationGender gender,
        long divisionId,
        String divisionName,
        long categoryId,
        String categoryName,
        long eventId,
        String teamId,
        String teamName,
        Instant submittedAt,
        List<AwardAdminRegistrationEntryResponse> entries
) {
}

package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record AwardPreVoteAssessmentEntryResponse(
        UUID registrationId,
        UUID entryId,
        long registrationNumber,
        String representativeName,
        String representativeEmail,
        String athleteName,
        String athleteInstagram,
        String teamName,
        AwardRegistrationReviewStatus reviewStatus,
        AwardRegistrationMediaStatus mediaStatus,
        AwardRegistrationMediaSource sourceType,
        String externalUrl,
        BigDecimal averageScore,
        int scoredAdministrators,
        int activeAdministrators,
        boolean allAdministratorsScored
) { }

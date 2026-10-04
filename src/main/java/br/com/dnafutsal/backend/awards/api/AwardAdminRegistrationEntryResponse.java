package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record AwardAdminRegistrationEntryResponse(
        UUID id,
        AwardRegistrationContestCategory contestCategory,
        String contestCategoryLabel,
        AwardRegistrationMediaSource sourceType,
        AwardRegistrationMediaStatus mediaStatus,
        AwardRegistrationReviewStatus reviewStatus,
        String externalUrl,
        String displayFilename,
        Long durationMs,
        Integer width,
        Integer height,
        Long fileSizeBytes,
        Instant reviewedAt,
        UUID reviewedByUserId,
        String reviewedByName,
        String reviewReason
) {
}

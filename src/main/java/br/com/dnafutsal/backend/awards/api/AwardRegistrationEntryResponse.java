package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;

import java.util.UUID;

public record AwardRegistrationEntryResponse(
        UUID id,
        AwardRegistrationContestCategory contestCategory,
        String contestCategoryLabel,
        AwardRegistrationMediaSource sourceType,
        AwardRegistrationMediaStatus mediaStatus,
        String externalUrl,
        String displayFilename,
        Long durationMs,
        Integer width,
        Integer height,
        Long fileSizeBytes
) {
}

package br.com.dnafutsal.backend.awards.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwardRegistrationEntryReviewTest {

    @Test
    void approvesPendingReadyCandidate() {
        AwardRegistrationEntry entry =
                new AwardRegistrationEntry(
                        UUID.randomUUID(),
                        AwardRegistrationContestCategory.BEAUTIFUL_GOAL,
                        AwardRegistrationMediaSource.LINK,
                        "https://example.com/video"
                );

        UUID reviewerId =
                UUID.randomUUID();

        Instant reviewedAt =
                Instant.parse(
                        "2026-10-03T22:00:00Z"
                );

        entry.approveReview(
                reviewerId,
                reviewedAt
        );

        assertThat(
                entry.getReviewStatus()
        ).isEqualTo(
                AwardRegistrationReviewStatus.APPROVED
        );

        assertThat(
                entry.getReviewedByUserId()
        ).isEqualTo(
                reviewerId
        );

        assertThat(
                entry.getReviewedAt()
        ).isEqualTo(
                reviewedAt
        );

        assertThat(
                entry.getReviewReason()
        ).isNull();
    }

    @Test
    void rejectsWithNormalizedReasonAndDoesNotAllowSecondDecision() {
        AwardRegistrationEntry entry =
                new AwardRegistrationEntry(
                        UUID.randomUUID(),
                        AwardRegistrationContestCategory.BEAUTIFUL_GOAL,
                        AwardRegistrationMediaSource.LINK,
                        "https://example.com/video"
                );

        entry.rejectReview(
                UUID.randomUUID(),
                Instant.parse(
                        "2026-10-03T22:00:00Z"
                ),
                "  Vídeo fora das regras da categoria.  "
        );

        assertThat(
                entry.getReviewStatus()
        ).isEqualTo(
                AwardRegistrationReviewStatus.REJECTED
        );

        assertThat(
                entry.getReviewReason()
        ).isEqualTo(
                "Vídeo fora das regras da categoria."
        );

        assertThatThrownBy(() ->
                entry.approveReview(
                        UUID.randomUUID(),
                        Instant.now()
                )
        ).isInstanceOf(
                IllegalStateException.class
        );
    }

    @Test
    void replacingReviewedLinkResetsDecisionAndAllowsReviewAgain() {
        AwardRegistrationEntry entry =
                new AwardRegistrationEntry(
                        UUID.randomUUID(),
                        AwardRegistrationContestCategory.BEST_DRIBBLE,
                        AwardRegistrationMediaSource.LINK,
                        "https://example.com/original"
                );

        entry.approveReview(
                UUID.randomUUID(),
                Instant.parse(
                        "2026-10-03T22:00:00Z"
                )
        );

        entry.updateExternalUrl(
                "https://example.com/replacement"
        );

        assertThat(entry.getSourceType())
                .isEqualTo(AwardRegistrationMediaSource.LINK);
        assertThat(entry.getExternalUrl())
                .isEqualTo("https://example.com/replacement");
        assertThat(entry.getReviewStatus())
                .isEqualTo(AwardRegistrationReviewStatus.PENDING_REVIEW);
        assertThat(entry.getReviewedAt()).isNull();
        assertThat(entry.getReviewedByUserId()).isNull();
        assertThat(entry.getReviewReason()).isNull();

        entry.rejectReview(
                UUID.randomUUID(),
                Instant.parse(
                        "2026-10-03T23:00:00Z"
                ),
                "Novo vídeo fora das regras."
        );

        assertThat(entry.getReviewStatus())
                .isEqualTo(AwardRegistrationReviewStatus.REJECTED);
    }

    @Test
    void replacingReviewedUploadCanSwitchBetweenUploadAndLink() {
        AwardRegistrationEntry entry =
                new AwardRegistrationEntry(
                        UUID.randomUUID(),
                        AwardRegistrationContestCategory.BEST_SAVE,
                        AwardRegistrationMediaSource.UPLOAD,
                        null
                );

        entry.beginUpload("tmp/original", "par-original");
        entry.beginProcessing();
        entry.completeUpload(
                "premio/original.mp4",
                "original.mp4",
                1000L,
                1280,
                720,
                1024L
        );
        entry.approveReview(
                UUID.randomUUID(),
                Instant.parse(
                        "2026-10-03T22:00:00Z"
                )
        );

        entry.updateExternalUrl(
                "https://example.com/external"
        );

        assertThat(entry.getSourceType())
                .isEqualTo(AwardRegistrationMediaSource.LINK);
        assertThat(entry.getObjectName()).isNull();
        assertThat(entry.getMediaStatus())
                .isEqualTo(AwardRegistrationMediaStatus.READY);
        assertThat(entry.getReviewStatus())
                .isEqualTo(AwardRegistrationReviewStatus.PENDING_REVIEW);

        entry.beginUpload("tmp/new", "par-new");

        assertThat(entry.getSourceType())
                .isEqualTo(AwardRegistrationMediaSource.UPLOAD);
        assertThat(entry.getExternalUrl()).isNull();
        assertThat(entry.getMediaStatus())
                .isEqualTo(AwardRegistrationMediaStatus.PENDING);
        assertThat(entry.getReviewStatus())
                .isEqualTo(AwardRegistrationReviewStatus.PENDING_REVIEW);
    }
}

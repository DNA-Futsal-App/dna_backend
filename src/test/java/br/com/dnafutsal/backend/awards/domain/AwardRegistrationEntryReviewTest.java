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
}

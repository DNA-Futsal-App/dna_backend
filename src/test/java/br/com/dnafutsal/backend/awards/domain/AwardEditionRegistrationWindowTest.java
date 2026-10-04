package br.com.dnafutsal.backend.awards.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AwardEditionRegistrationWindowTest {

    @Test
    void opensClosesAndReopensRegistrationWindowIndependentlyFromVoting() {
        AwardEdition edition =
                new AwardEdition(
                        "legacy-2026",
                        "Prêmio Legacy 2026",
                        2026,
                        AwardEditionStatus.DRAFT,
                        null,
                        null
                );

        assertThat(edition.isRegistrationsOpen())
                .isFalse();

        Instant firstOpen =
                Instant.parse("2026-10-04T12:00:00Z");

        edition.openRegistrations(firstOpen);

        assertThat(edition.isRegistrationsOpen())
                .isTrue();
        assertThat(edition.getRegistrationsOpenedAt())
                .isEqualTo(firstOpen);
        assertThat(edition.getRegistrationsClosedAt())
                .isNull();
        assertThat(edition.getStatus())
                .isEqualTo(AwardEditionStatus.DRAFT);

        Instant closedAt =
                Instant.parse("2026-10-10T23:59:59Z");

        edition.closeRegistrations(closedAt);

        assertThat(edition.isRegistrationsOpen())
                .isFalse();
        assertThat(edition.getRegistrationsClosedAt())
                .isEqualTo(closedAt);

        Instant reopenedAt =
                Instant.parse("2026-10-11T12:00:00Z");

        edition.openRegistrations(reopenedAt);

        assertThat(edition.isRegistrationsOpen())
                .isTrue();
        assertThat(edition.getRegistrationsOpenedAt())
                .isEqualTo(reopenedAt);
        assertThat(edition.getRegistrationsClosedAt())
                .isNull();
    }
}

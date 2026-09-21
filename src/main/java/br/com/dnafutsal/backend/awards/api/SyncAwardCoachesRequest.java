package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.Positive;

public record SyncAwardCoachesRequest(
        @Positive long eventId,
        @Positive long divisionId,
        @Positive long categoryId
) {
}

package br.com.dnafutsal.backend.sports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchCompletedNotification(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 100) String categoryId,
        @NotBlank @Size(max = 100) String divisionId,
        @NotEmpty @Size(max = 2) List<@NotBlank @Size(max = 100) String> teamIds,
        @NotNull Instant occurredAt
) {
    public MatchCompletedNotification {
        teamIds = teamIds == null ? null : teamIds.stream().distinct().toList();
    }
}

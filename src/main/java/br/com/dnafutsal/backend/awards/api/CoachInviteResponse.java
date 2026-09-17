package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.UUID;

public record CoachInviteResponse(
        UUID inviteId,
        boolean available,
        String status,
        Instant expiresAt,
        UUID editionId,
        String editionSlug,
        String editionName,
        int season,
        String coachName,
        long eventId,
        long divisionId,
        long categoryId,
        String teamId,
        String teamName
) {
}

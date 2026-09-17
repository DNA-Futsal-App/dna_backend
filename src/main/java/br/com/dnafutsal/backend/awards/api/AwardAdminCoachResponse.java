package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.UUID;

public record AwardAdminCoachResponse(
        UUID candidateId,
        String coachName,
        String teamId,
        String teamName,
        String teamLogoUrl,
        long eventId,
        long divisionId,
        long categoryId,
        boolean active,
        String accessState,
        UUID inviteId,
        Instant inviteExpiresAt,
        Instant reservedAt,
        Instant reservationExpiresAt,
        Instant claimedAt,
        UUID userId,
        String userName,
        String userEmail,
        Instant voterCreatedAt,
        Instant ballotSubmittedAt
) {
}

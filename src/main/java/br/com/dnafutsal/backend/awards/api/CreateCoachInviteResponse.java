package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;
import java.util.UUID;

public record CreateCoachInviteResponse(
        UUID inviteId,
        String inviteUrl,
        Instant expiresAt
) {
}

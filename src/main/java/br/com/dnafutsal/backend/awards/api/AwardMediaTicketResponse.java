package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;

public record AwardMediaTicketResponse (
        String url,
        Instant expiresAt
) {
}

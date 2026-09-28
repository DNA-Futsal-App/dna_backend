package br.com.dnafutsal.backend.awards.api;

import java.time.Instant;

public record AwardUploadTicketResponse(
        String uploadUrl,
        Instant expiresAt,
        long maxUploadBytes
) {
}

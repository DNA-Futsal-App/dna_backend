package br.com.dnafutsal.backend.awards.api;
import java.time.Instant;
public record AwardPopularContextWindowResponse(AwardPopularContext context,
        String status, Instant opensAt, Instant closesAt, Instant manuallyClosedAt) { }

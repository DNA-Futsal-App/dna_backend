package br.com.dnafutsal.backend.identity.api;

import java.time.Instant;

public record AccountDeletionResponse(String status, Instant requestedAt, Instant scheduledFor, String message) {
}

package br.com.dnafutsal.backend.identity.api;

import java.time.Instant;

/** O link equivale a uma credencial temporaria e so deve retornar para ADMIN. */
public record AdminPasswordResetLinkResponse(String link, Instant expiresAt) { }

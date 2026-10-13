package br.com.dnafutsal.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeletionRequest(@NotBlank @Size(max = 72) String currentPassword) {
}

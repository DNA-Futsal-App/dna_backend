package br.com.dnafutsal.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAccountDeletionRequest(
        @NotBlank @Size(max = 254) String login,
        @NotBlank @Size(max = 72) String currentPassword) {
}

package br.com.dnafutsal.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = 10, max = 72) String newPassword
) {
}

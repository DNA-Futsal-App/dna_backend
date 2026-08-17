package br.com.dnafutsal.backend.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 30) String phone,
        @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "deve ser um usuário válido do Instagram")
        String childInstagram,
        @Positive Long eventId,
        @Size(max = 100) String categoryId,
        @Size(max = 100) String divisionId,
        @Size(max = 100) String teamId,
        @NotBlank @Size(max = 72) String currentPassword
) {
}

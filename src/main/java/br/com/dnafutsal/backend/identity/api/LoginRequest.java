package br.com.dnafutsal.backend.identity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String login,
        @NotBlank @Size(max = 72) String password,
        @Valid RegisterRequest.CoachCategoryRegistration coachCategoryRegistration
) {
    // Compatible with existing tests and normal login callers.
    public LoginRequest(String login, String password) {
        this(login, password, null);
    }
}

package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAwardRegistrationLinkRequest(
        @NotBlank
        @Size(max = 2000)
        String url
) {
}

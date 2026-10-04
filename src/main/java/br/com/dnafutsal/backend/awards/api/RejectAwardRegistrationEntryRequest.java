package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectAwardRegistrationEntryRequest(
        @NotBlank
        @Size(min = 5, max = 1000)
        String reason
) {
}

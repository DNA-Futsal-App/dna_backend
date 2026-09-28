package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateAwardRegistrationRequest(
        @NotBlank
        @Size(max = 150)
        String athleteName,

        @NotBlank
        @Size(max = 14)
        String representativeCpf,

        @Positive
        long divisionId,

        @Positive
        long categoryId,

        @NotBlank
        @Size(max = 100)
        String teamId,

        @NotEmpty
        @Size(max = 4)
        List<@Valid CreateAwardRegistrationEntryRequest> entries
) {
}

package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

        @NotNull
        AwardRegistrationGender gender,

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
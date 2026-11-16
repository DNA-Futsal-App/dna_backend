package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateCoachContextRequest(
        @NotNull UUID credentialId,
        @Positive long eventId,
        @Positive long divisionId,
        @Positive long categoryId,
        @NotBlank @Size(max=100) String teamId,
        AwardRegistrationGender gender
) {}

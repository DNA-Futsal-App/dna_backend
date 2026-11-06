package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AwardRegistrationReopeningBatchRequest(
        @NotEmpty @Size(max = 20) List<@Valid @NotNull AwardRegistrationReopeningRequest> items
) {}

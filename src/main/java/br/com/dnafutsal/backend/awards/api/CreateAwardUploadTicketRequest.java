package br.com.dnafutsal.backend.awards.api;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAwardUploadTicketRequest(
        @Positive
        long sizeBytes,

        @Size(max = 120)
        String contentType
) {
}

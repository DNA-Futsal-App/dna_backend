package br.com.dnafutsal.backend.notification.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CommunityScoreIngestRequest(

        @NotBlank
        @Size(max = 30)
        String source,

        @NotBlank
        @Size(max = 120)
        String sourceUpdateId,

        @NotBlank
        @Size(max = 120)
        String sourceChatId,

        @Size(max = 120)
        String sourceMessageId,

        @NotBlank
        @Size(max = 600)
        String rawText,

        @NotNull
        Instant reportedAt

) {
}
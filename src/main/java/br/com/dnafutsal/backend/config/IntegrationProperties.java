package br.com.dnafutsal.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.integrations")
public record IntegrationProperties(@NotNull SportsEndpoint sports, @NotNull Endpoint news) {

    public record SportsEndpoint(
            @NotBlank String baseUrl,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout,
            @NotNull Duration eventCacheTtl,
            @NotNull Duration teamCacheTtl,
            @NotNull Duration snapshotCacheTtl,
            boolean includePersonalData
    ) {



    }

    public record Endpoint(@NotBlank String baseUrl) {
    }
}

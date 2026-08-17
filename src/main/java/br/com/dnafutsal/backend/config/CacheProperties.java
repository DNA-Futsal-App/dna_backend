package br.com.dnafutsal.backend.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
        @Positive long sportsMaxEntries,
        @Positive long identityMaxEntries,
        @Positive long loginAttemptMaxEntries
) {
}

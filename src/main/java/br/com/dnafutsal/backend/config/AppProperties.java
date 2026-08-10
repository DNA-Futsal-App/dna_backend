package br.com.dnafutsal.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendBaseUrl,
        @NotEmpty List<String> corsAllowedOrigins,
        @NotBlank String timezone
) {
    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}

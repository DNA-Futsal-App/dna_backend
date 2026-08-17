package br.com.dnafutsal.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Profile("prod")
@Component
public class ProductionSecretsValidator {

    private final SecurityProperties properties;

    public ProductionSecretsValidator(SecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (isMissingOrPlaceholder(properties.jwtSecret())) {
            throw new IllegalStateException("JWT_SECRET must be configured with a non-placeholder value in production");
        }
    }

    private boolean isMissingOrPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("replace_with")
                || normalized.startsWith("replace-with")
                || normalized.startsWith("replace_me")
                || normalized.startsWith("replace-me")
                || normalized.startsWith("change_this")
                || normalized.startsWith("change-this")
                || normalized.startsWith("dev-only");
    }
}

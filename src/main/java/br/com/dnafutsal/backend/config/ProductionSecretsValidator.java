package br.com.dnafutsal.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("prod")
@Component
public class ProductionSecretsValidator {

    private final SecurityProperties properties;

    public ProductionSecretsValidator(SecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.jwtSecret().startsWith("REPLACE_WITH")
                || properties.internalApiKey().startsWith("change-this")) {
            throw new IllegalStateException("JWT_SECRET and INTERNAL_API_KEY must be replaced in production");
        }
    }
}

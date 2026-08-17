package br.com.dnafutsal.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsValidatorTest {

    @Test
    void reportsPlaceholderJwtSecret() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                security("replace-with-a-random-secret"));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining("replace-with-a-random-secret");
    }

    @Test
    void acceptsConfiguredProductionSecrets() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(security("j".repeat(64)));

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void isNotRegisteredOutsideProductionProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.register(ProductionSecretsValidator.class);
            context.refresh();

            assertThat(context.getBeansOfType(ProductionSecretsValidator.class)).isEmpty();
        }
    }

    private SecurityProperties security(String jwtSecret) {
        return new SecurityProperties(jwtSecret, Duration.ofMinutes(15), Duration.ofDays(30),
                Duration.ofHours(24), Duration.ofMinutes(30));
    }
}

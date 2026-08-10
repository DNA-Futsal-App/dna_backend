package br.com.dnafutsal.backend.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.mail-delivery")
public record MailDeliveryProperties(
        @NotBlank @Email String fromEmail,
        @NotBlank String fromName,
        boolean brevoEnabled,
        @NotBlank String brevoApiUrl,
        String brevoApiKey,
        @Min(1000) long outboxDelay,
        @Min(1) int maxAttempts
) {
}

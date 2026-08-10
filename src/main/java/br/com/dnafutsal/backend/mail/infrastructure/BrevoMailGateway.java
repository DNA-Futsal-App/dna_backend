package br.com.dnafutsal.backend.mail.infrastructure;

import br.com.dnafutsal.backend.config.MailDeliveryProperties;
import br.com.dnafutsal.backend.mail.domain.MailDeliveryException;
import br.com.dnafutsal.backend.mail.domain.MailGateway;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class BrevoMailGateway implements MailGateway {

    private final RestClient client;
    private final MailDeliveryProperties properties;

    public BrevoMailGateway(RestClient.Builder builder, MailDeliveryProperties properties) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.brevoApiUrl()).build();
    }

    @Override
    public void send(MailMessage message) {
        if (!properties.brevoEnabled() || properties.brevoApiKey() == null
                || properties.brevoApiKey().isBlank()) {
            throw new MailDeliveryException("Brevo provider is disabled or not configured");
        }
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", properties.fromName(), "email", properties.fromEmail()),
                "to", List.of(Map.of("email", message.recipient())),
                "subject", message.subject(),
                "htmlContent", message.htmlBody()
        );
        try {
            client.post()
                    .uri("/smtp/email")
                    .header("api-key", properties.brevoApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new MailDeliveryException("Brevo rejected the message", exception);
        }
    }
}

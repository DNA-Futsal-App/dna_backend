package br.com.dnafutsal.backend.mail.infrastructure;

import br.com.dnafutsal.backend.mail.domain.MailDeliveryException;
import br.com.dnafutsal.backend.mail.domain.MailGateway;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class FallbackMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(FallbackMailGateway.class);

    private final BrevoMailGateway primary;
    private final SmtpMailGateway fallback;

    public FallbackMailGateway(BrevoMailGateway primary, SmtpMailGateway fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public void send(MailMessage message) {
        try {
            primary.send(message);
            return;
        } catch (MailDeliveryException primaryFailure) {
            log.warn("Primary email provider failed; using SMTP fallback: {}", primaryFailure.getMessage());
        }
        try {
            fallback.send(message);
        } catch (MailDeliveryException fallbackFailure) {
            throw new MailDeliveryException("Primary and fallback email providers failed", fallbackFailure);
        }
    }
}

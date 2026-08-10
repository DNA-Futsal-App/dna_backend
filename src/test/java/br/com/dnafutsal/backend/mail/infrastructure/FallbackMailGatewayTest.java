package br.com.dnafutsal.backend.mail.infrastructure;

import br.com.dnafutsal.backend.mail.domain.MailDeliveryException;
import br.com.dnafutsal.backend.mail.domain.MailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FallbackMailGatewayTest {

    private final BrevoMailGateway brevo = mock(BrevoMailGateway.class);
    private final SmtpMailGateway smtp = mock(SmtpMailGateway.class);
    private final FallbackMailGateway gateway = new FallbackMailGateway(brevo, smtp);
    private final MailMessage message = new MailMessage("user@example.com", "Subject", "<p>Body</p>");

    @Test
    void usesSmtpWhenBrevoFails() {
        doThrow(new MailDeliveryException("brevo down")).when(brevo).send(message);

        gateway.send(message);

        verify(smtp).send(message);
    }

    @Test
    void reportsFailureWhenBothProvidersFail() {
        doThrow(new MailDeliveryException("brevo down")).when(brevo).send(message);
        doThrow(new MailDeliveryException("smtp down")).when(smtp).send(message);

        assertThatThrownBy(() -> gateway.send(message))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageContaining("Primary and fallback");
    }
}

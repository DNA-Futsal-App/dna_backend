package br.com.dnafutsal.backend.mail.application;

import br.com.dnafutsal.backend.config.MailDeliveryProperties;
import br.com.dnafutsal.backend.mail.domain.MailGateway;
import br.com.dnafutsal.backend.mail.domain.MailOutbox;
import br.com.dnafutsal.backend.mail.infrastructure.MailOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class MailOutboxProcessor {

    private final MailOutboxRepository repository;
    private final MailGateway gateway;
    private final MailDeliveryProperties properties;
    private final Clock clock;

    public MailOutboxProcessor(MailOutboxRepository repository, MailGateway gateway,
                               MailDeliveryProperties properties, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void process(UUID id) {
        MailOutbox mail = repository.findByIdForUpdate(id).orElse(null);
        if (mail == null || !mail.canProcess(clock.instant())) {
            return;
        }
        mail.processing();
        try {
            gateway.send(mail.toMessage());
            mail.sent(clock.instant());
        } catch (RuntimeException exception) {
            mail.failed(clock.instant(), properties.maxAttempts(), exception.getMessage());
        }
    }
}

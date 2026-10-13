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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MailOutboxProcessor.class);

    private final MailOutboxRepository repository;
    private final MailGateway gateway;
    private final MailDeliveryProperties properties;
    private final Clock clock;
    private final io.micrometer.core.instrument.MeterRegistry metrics;

    public MailOutboxProcessor(MailOutboxRepository repository, MailGateway gateway,
                               MailDeliveryProperties properties, Clock clock,
                               io.micrometer.core.instrument.MeterRegistry metrics) {
        this.repository = repository;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
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
            metrics.counter("mail.outbox.delivery.failures").increment();
            log.warn("Outbox delivery failed; retry policy applied");
            mail.failed(clock.instant(), properties.maxAttempts(), exception.getMessage());
        }
    }
}

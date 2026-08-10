package br.com.dnafutsal.backend.mail.application;

import br.com.dnafutsal.backend.mail.domain.MailOutboxStatus;
import br.com.dnafutsal.backend.mail.infrastructure.MailOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

@Component
public class MailOutboxDispatcher {

    private final MailOutboxRepository repository;
    private final MailOutboxProcessor processor;
    private final Clock clock;

    public MailOutboxDispatcher(MailOutboxRepository repository, MailOutboxProcessor processor, Clock clock) {
        this.repository = repository;
        this.processor = processor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.mail-delivery.outbox-delay:15000}")
    public void dispatch() {
        repository.findDueIds(List.of(MailOutboxStatus.PENDING, MailOutboxStatus.RETRY),
                        clock.instant(), PageRequest.of(0, 20))
                .forEach(processor::process);
    }
}

package br.com.dnafutsal.backend.mail.application;

import br.com.dnafutsal.backend.mail.domain.MailMessage;
import br.com.dnafutsal.backend.mail.domain.MailOutbox;
import br.com.dnafutsal.backend.mail.infrastructure.MailOutboxRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class MailOutboxService {

    private final MailOutboxRepository repository;
    private final Clock clock;

    public MailOutboxService(MailOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void enqueue(MailMessage message) {
        repository.save(new MailOutbox(message, clock.instant()));
    }
}

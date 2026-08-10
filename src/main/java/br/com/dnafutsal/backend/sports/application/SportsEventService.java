package br.com.dnafutsal.backend.sports.application;

import br.com.dnafutsal.backend.sports.api.MatchCompletedNotification;
import br.com.dnafutsal.backend.sports.infrastructure.ProcessedSportsEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

@Service
public class SportsEventService {

    private final ProcessedSportsEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final SportsCacheCoordinator cacheCoordinator;
    private final Clock clock;

    public SportsEventService(ProcessedSportsEventRepository events, ApplicationEventPublisher publisher,
                              SportsCacheCoordinator cacheCoordinator, Clock clock) {
        this.events = events;
        this.publisher = publisher;
        this.cacheCoordinator = cacheCoordinator;
        this.clock = clock;
    }

    @Transactional
    public boolean accept(MatchCompletedNotification event) {
        int inserted = events.insertIfAbsent(event.eventId(), event.occurredAt(), clock.instant());
        if (inserted == 0) {
            return false;
        }
        publisher.publishEvent(event);
        return true;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterMatchCompleted(MatchCompletedNotification event) {
        cacheCoordinator.refresh(event);
    }
}

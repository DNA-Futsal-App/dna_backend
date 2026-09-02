package br.com.dnafutsal.backend.sports.notification.application;

import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.sports.application.SportsDefaultEventResolver;
import br.com.dnafutsal.backend.sports.domain.SportsDataGateway;
import br.com.dnafutsal.backend.sports.domain.SportsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class SportsChangeMonitor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    SportsChangeMonitor.class
            );

    private final UserAccountRepository users;
    private final SportsDefaultEventResolver defaultEvents;
    private final SportsDataGateway gateway;
    private final SportsChangeDetector detector;

    public SportsChangeMonitor(
            UserAccountRepository users,
            SportsDefaultEventResolver defaultEvents,
            SportsDataGateway gateway,
            SportsChangeDetector detector
    ) {
        this.users = users;
        this.defaultEvents = defaultEvents;
        this.gateway = gateway;
        this.detector = detector;
    }

    @Scheduled(
            initialDelayString =
                    "${app.sports.monitor.initial-delay:30000}",
            fixedDelayString =
                    "${app.sports.monitor.delay:600000}"
    )
    public void scan() {

        Set<Long> eventIds =
                new LinkedHashSet<>(
                        users.findDistinctFollowedEventIdsByStatus(
                                UserStatus.ACTIVE
                        )
                );
        try {
            eventIds.add(
                    defaultEvents
                            .resolveCurrentEventId()
            );

        } catch (RuntimeException exception) {
            log.warn(
                    "Default sports event could not be resolved for monitoring: {}",
                    exception.getMessage()
            );
        }

        for (Long eventId : eventIds) {

            if (
                    eventId == null
                            || eventId <= 0
            ) {
                continue;
            }

            try {
                SportsSnapshot snapshot =
                        gateway.snapshot(
                                eventId
                        );

                detector.detect(
                        eventId,
                        snapshot.matches()
                );

            } catch (RuntimeException exception) {
                log.warn(
                        "Sports monitoring failed eventId={} error={}",
                        eventId,
                        exception.getMessage()
                );
            }
        }
    }
}
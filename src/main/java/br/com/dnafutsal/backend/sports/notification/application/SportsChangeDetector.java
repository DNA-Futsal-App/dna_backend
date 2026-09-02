package br.com.dnafutsal.backend.sports.notification.application;

import br.com.dnafutsal.backend.sports.domain.MatchStatus;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.notification.domain.SportsChangeEvent;
import br.com.dnafutsal.backend.sports.notification.domain.SportsChangeType;
import br.com.dnafutsal.backend.sports.notification.domain.SportsEventMonitorState;
import br.com.dnafutsal.backend.sports.notification.domain.SportsMatchState;
import br.com.dnafutsal.backend.sports.notification.infrastructure.SportsChangeEventRepository;
import br.com.dnafutsal.backend.sports.notification.infrastructure.SportsEventMonitorStateRepository;
import br.com.dnafutsal.backend.sports.notification.infrastructure.SportsMatchStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SportsChangeDetector {

    private final SportsEventMonitorStateRepository monitors;
    private final SportsMatchStateRepository states;
    private final SportsChangeEventRepository events;
    private final Clock clock;

    public SportsChangeDetector(
            SportsEventMonitorStateRepository monitors,
            SportsMatchStateRepository states,
            SportsChangeEventRepository events,
            Clock clock
    ) {
        this.monitors = monitors;
        this.states = states;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public List<SportsChangeEvent> detect(
            long eventId,
            List<MatchView> currentMatches
    ) {
        Instant now =
                clock.instant();

        List<MatchView> safeMatches =
                currentMatches == null
                        ? List.of()
                        : currentMatches;

        SportsEventMonitorState monitor =
                monitors
                        .findByEventIdForUpdate(
                                eventId
                        )
                        .orElse(null);
        if (monitor == null) {
            baseline(
                    eventId,
                    safeMatches,
                    now
            );

            monitors.save(
                    new SportsEventMonitorState(
                            eventId,
                            now
                    )
            );

            return List.of();
        }

        Map<String, SportsMatchState> previous =
                new LinkedHashMap<>();

        states.findAllByEventId(
                        eventId
                )
                .forEach(state ->
                        previous.put(
                                state.getMatchId(),
                                state
                        )
                );

        List<SportsMatchState> statesToSave =
                new ArrayList<>();

        List<SportsChangeEvent> generated =
                new ArrayList<>();

        for (MatchView current : safeMatches) {

            if (
                    current == null
                            || current.id() == null
                            || current.id().isBlank()
            ) {
                continue;
            }

            SportsMatchState old =
                    previous.get(
                            current.id()
                    );

            if (old == null) {
                if (
                        current.status()
                                == MatchStatus.SCHEDULED
                ) {
                    generated.add(
                            SportsChangeEvent.of(
                                    SportsChangeType.MATCH_ADDED,
                                    null,
                                    current,
                                    now
                            )
                    );
                }

                statesToSave.add(
                        new SportsMatchState(
                                eventId,
                                current,
                                now
                        )
                );

                continue;
            }

            if (
                    scheduleChanged(
                            old,
                            current
                    )
                            && current.status()
                            != MatchStatus.FINISHED
            ) {
                generated.add(
                        SportsChangeEvent.of(
                                SportsChangeType.MATCH_SCHEDULE_CHANGED,
                                old,
                                current,
                                now
                        )
                );
            }

            if (
                    old.getStatus()
                            != MatchStatus.FINISHED
                            && current.status()
                            == MatchStatus.FINISHED
            ) {
                generated.add(
                        SportsChangeEvent.of(
                                SportsChangeType.MATCH_RESULT_PUBLISHED,
                                old,
                                current,
                                now
                        )
                );

            } else if (
                    old.getStatus()
                            == MatchStatus.FINISHED
                            && current.status()
                            == MatchStatus.FINISHED
                            && scoreChanged(
                            old,
                            current
                    )
            ) {
                generated.add(
                        SportsChangeEvent.of(
                                SportsChangeType.MATCH_RESULT_CORRECTED,
                                old,
                                current,
                                now
                        )
                );
            }

            old.refresh(
                    current,
                    now
            );

            statesToSave.add(
                    old
            );
        }

        if (!statesToSave.isEmpty()) {
            states.saveAll(
                    statesToSave
            );
        }

        if (!generated.isEmpty()) {
            events.saveAll(
                    generated
            );
        }

        monitor.checked(
                now
        );

        return List.copyOf(
                generated
        );
    }

    private void baseline(
            long eventId,
            List<MatchView> matches,
            Instant now
    ) {
        List<SportsMatchState> baseline =
                matches.stream()
                        .filter(
                                Objects::nonNull
                        )
                        .filter(match ->
                                match.id() != null
                                        && !match.id()
                                        .isBlank()
                        )
                        .map(match ->
                                new SportsMatchState(
                                        eventId,
                                        match,
                                        now
                                )
                        )
                        .toList();

        if (!baseline.isEmpty()) {
            states.saveAll(
                    baseline
            );
        }
    }

    private boolean scheduleChanged(
            SportsMatchState previous,
            MatchView current
    ) {
        return !Objects.equals(
                previous.getScheduledDate(),
                current.scheduledDate()
        )
                || !Objects.equals(
                previous.getScheduledAt(),
                current.scheduledAt()
        );
    }

    private boolean scoreChanged(
            SportsMatchState previous,
            MatchView current
    ) {
        return !Objects.equals(
                previous.getHomeScore(),
                current.homeScore()
        )
                || !Objects.equals(
                previous.getAwayScore(),
                current.awayScore()
        );
    }
}
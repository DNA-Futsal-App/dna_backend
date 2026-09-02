package br.com.dnafutsal.backend.sports.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "sports_event_monitor_state")
public class SportsEventMonitorState {

    @Id
    @Column(name = "event_id")
    private long eventId;

    @Column(
            name = "initialized_at",
            nullable = false
    )
    private Instant initializedAt;

    @Column(
            name = "last_checked_at",
            nullable = false
    )
    private Instant lastCheckedAt;

    protected SportsEventMonitorState() {
    }

    public SportsEventMonitorState(
            long eventId,
            Instant now
    ) {
        this.eventId = eventId;
        this.initializedAt = now;
        this.lastCheckedAt = now;
    }

    public void checked(
            Instant now
    ) {
        this.lastCheckedAt = now;
    }

    public long getEventId() {
        return eventId;
    }

    public Instant getInitializedAt() {
        return initializedAt;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }
}
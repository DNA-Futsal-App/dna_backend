package br.com.dnafutsal.backend.sports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_sports_events")
public class ProcessedSportsEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ProcessedSportsEvent() {
    }

    public ProcessedSportsEvent(UUID eventId, Instant occurredAt, Instant receivedAt) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }
}

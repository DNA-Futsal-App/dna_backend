package br.com.dnafutsal.backend.sports.infrastructure;

import br.com.dnafutsal.backend.sports.domain.ProcessedSportsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedSportsEventRepository extends JpaRepository<ProcessedSportsEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_sports_events(event_id, occurred_at, received_at)
            VALUES (:eventId, :occurredAt, :receivedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(UUID eventId, Instant occurredAt, Instant receivedAt);
}

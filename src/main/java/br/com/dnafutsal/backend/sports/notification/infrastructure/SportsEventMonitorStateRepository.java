package br.com.dnafutsal.backend.sports.notification.infrastructure;

import br.com.dnafutsal.backend.sports.notification.domain.SportsEventMonitorState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SportsEventMonitorStateRepository
        extends JpaRepository<
        SportsEventMonitorState,
        Long
        > {

    @Lock(
            LockModeType.PESSIMISTIC_WRITE
    )
    @Query("""
            select state
            from SportsEventMonitorState state
            where state.eventId = :eventId
            """)
    Optional<SportsEventMonitorState>
    findByEventIdForUpdate(
            long eventId
    );
}
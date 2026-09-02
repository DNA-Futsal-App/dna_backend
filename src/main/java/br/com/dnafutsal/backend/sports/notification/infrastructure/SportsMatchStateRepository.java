package br.com.dnafutsal.backend.sports.notification.infrastructure;

import br.com.dnafutsal.backend.sports.notification.domain.SportsMatchState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SportsMatchStateRepository
        extends JpaRepository<
        SportsMatchState,
        UUID
        > {

    List<SportsMatchState>
    findAllByEventId(
            long eventId
    );
}
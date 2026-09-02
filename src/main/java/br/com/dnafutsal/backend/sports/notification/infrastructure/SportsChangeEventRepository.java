package br.com.dnafutsal.backend.sports.notification.infrastructure;

import br.com.dnafutsal.backend.sports.notification.domain.SportsChangeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SportsChangeEventRepository extends JpaRepository<SportsChangeEvent, UUID> {
}

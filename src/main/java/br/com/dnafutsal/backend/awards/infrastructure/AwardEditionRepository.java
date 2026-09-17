package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AwardEditionRepository extends JpaRepository<AwardEdition, UUID> {
}

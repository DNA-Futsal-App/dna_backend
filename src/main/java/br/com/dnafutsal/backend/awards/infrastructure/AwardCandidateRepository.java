package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AwardCandidateRepository extends JpaRepository<AwardCandidate, UUID> {

    Optional<AwardCandidate> findByIdAndEditionId(UUID id, UUID editionId);
}

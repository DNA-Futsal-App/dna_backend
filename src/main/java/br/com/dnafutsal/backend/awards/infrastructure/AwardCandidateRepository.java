package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardCandidateRepository
        extends JpaRepository<AwardCandidate, UUID> {

    Optional<AwardCandidate> findByIdAndEditionId(
            UUID id,
            UUID editionId
    );

    List<AwardCandidate> findByEditionIdAndEventIdAndTeamIdAndSource(
            UUID editionId,
            long eventId,
            String teamId,
            AwardCandidateSource source
    );

    List<AwardCandidate> findByEditionIdOrderByTeamNameAscNameAsc(
            UUID editionId
    );
}

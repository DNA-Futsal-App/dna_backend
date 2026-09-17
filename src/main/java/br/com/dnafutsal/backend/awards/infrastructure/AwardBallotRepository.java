package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AwardBallotRepository
        extends JpaRepository<AwardBallot, UUID> {

    boolean existsByEditionIdAndVoterUserId(
            UUID editionId,
            UUID voterUserId
    );

    Optional<AwardBallot> findByEditionIdAndVoterUserId(
            UUID editionId,
            UUID voterUserId
    );
}

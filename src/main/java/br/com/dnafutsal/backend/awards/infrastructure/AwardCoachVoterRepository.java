package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardCoachVoterRepository extends JpaRepository<AwardCoachVoter, UUID> {

    Optional<AwardCoachVoter> findByEditionIdAndUserId(UUID editionId, UUID userId);

    boolean existsByEditionIdAndUserId(UUID editionId, UUID userId);

    boolean existsByEditionIdAndSelfCoachCandidateId(
            UUID editionId,
            UUID selfCoachCandidateId
    );

    List<AwardCoachVoter> findByUserIdOrderByCreatedAtDesc(
            UUID userId
    );

    List<AwardCoachVoter> findByEditionId(
            UUID editionId
    );
}

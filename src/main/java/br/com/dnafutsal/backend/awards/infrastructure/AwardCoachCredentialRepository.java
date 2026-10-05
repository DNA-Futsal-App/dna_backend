package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardCoachCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AwardCoachCredentialRepository extends JpaRepository<AwardCoachCredential, UUID> {
    Optional<AwardCoachCredential> findByEditionIdAndUserId(UUID editionId,UUID userId);
    List<AwardCoachCredential> findByUserIdAndActiveTrueOrderByCreatedAtDesc(UUID userId);
    List<AwardCoachCredential> findByEditionIdOrderByCreatedAtAsc(UUID editionId);
    Optional<AwardCoachCredential> findByIdAndActiveTrue(UUID id);
}

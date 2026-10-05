package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AwardCoachAccessLinkRepository extends JpaRepository<AwardCoachAccessLink, UUID> {
    Optional<AwardCoachAccessLink> findByTokenHash(String tokenHash);
    Optional<AwardCoachAccessLink> findFirstByEditionIdAndStatusOrderByCreatedAtDesc(UUID editionId, AwardCoachAccessLinkStatus status);
    Optional<AwardCoachAccessLink> findFirstByEditionIdOrderByCreatedAtDesc(UUID editionId);
}

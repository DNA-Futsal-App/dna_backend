package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardPreVoteScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface AwardPreVoteScoreRepository extends JpaRepository<AwardPreVoteScore, UUID> {
    List<AwardPreVoteScore> findByEntryIdIn(Collection<UUID> entryIds);
    Optional<AwardPreVoteScore> findByEntryIdAndAdminUserIdAndApprovalAt(UUID entryId, UUID adminUserId, Instant approvalAt);
}

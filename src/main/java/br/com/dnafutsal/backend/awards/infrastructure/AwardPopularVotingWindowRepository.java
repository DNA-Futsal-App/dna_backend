package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardPopularVotingWindow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface AwardPopularVotingWindowRepository extends JpaRepository<AwardPopularVotingWindow, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from AwardPopularVotingWindow w where w.editionId = :editionId")
    Optional<AwardPopularVotingWindow> lock(@Param("editionId") UUID editionId);

    // Votos usam FOR SHARE, fechamento usa FOR UPDATE.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select w from AwardPopularVotingWindow w where w.editionId = :editionId")
    Optional<AwardPopularVotingWindow> lockForVoting(@Param("editionId") UUID editionId);


}

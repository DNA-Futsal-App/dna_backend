package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardCoachInvite;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInviteStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardCoachInviteRepository extends JpaRepository<AwardCoachInvite, UUID> {

    Optional<AwardCoachInvite> findByTokenHash(String tokenHash);

    List<AwardCoachInvite> findByEditionIdOrderByCreatedAtDesc(
            UUID editionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invite
            from AwardCoachInvite invite
            where invite.reservedByUserId = :userId
            """)
    Optional<AwardCoachInvite> findByReservedByUserIdForUpdate(
            @Param("userId") UUID userId
    );

    Optional<AwardCoachInvite> findByEditionIdAndCoachCandidateIdAndStatus(
            UUID editionId,
            UUID coachCandidateId,
            AwardCoachInviteStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invite
            from AwardCoachInvite invite
            where invite.tokenHash = :tokenHash
            """)
    Optional<AwardCoachInvite> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}

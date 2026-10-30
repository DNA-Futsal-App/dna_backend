package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AwardPreVoteEditionLockRepository extends Repository<AwardEdition, UUID> {
    // Serializa notas e publicação na mesma edição, inclusive entre instâncias do backend.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select edition from AwardEdition edition where edition.id = :editionId")
    Optional<AwardEdition> lockEdition(@Param("editionId") UUID editionId);
}

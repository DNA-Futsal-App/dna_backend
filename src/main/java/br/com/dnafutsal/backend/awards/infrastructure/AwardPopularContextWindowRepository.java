package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AwardPopularContextWindowRepository extends JpaRepository<AwardPopularContextWindow, UUID> {
    List<AwardPopularContextWindow> findByEditionId(UUID editionId);

    Optional<AwardPopularContextWindow> findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
            UUID editionId, long eventId, long divisionId, long categoryId,
            AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
       select w from AwardPopularContextWindow w
       where w.editionId = :editionId and w.eventId = :eventId and w.divisionId = :divisionId
         and w.categoryId = :categoryId and w.gender = :gender and w.contestCategory = :contestCategory
       """)
    Optional<AwardPopularContextWindow> lockForVote(
            @Param("editionId") UUID editionId, @Param("eventId") long eventId,
            @Param("divisionId") long divisionId, @Param("categoryId") long categoryId,
            @Param("gender") AwardRegistrationGender gender,
            @Param("contestCategory") AwardRegistrationContestCategory contestCategory);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       select w from AwardPopularContextWindow w
       where w.editionId = :editionId and w.eventId = :eventId and w.divisionId = :divisionId
         and w.categoryId = :categoryId and w.gender = :gender and w.contestCategory = :contestCategory
       """)
    Optional<AwardPopularContextWindow> lockForClose(
            @Param("editionId") UUID editionId, @Param("eventId") long eventId,
            @Param("divisionId") long divisionId, @Param("categoryId") long categoryId,
            @Param("gender") AwardRegistrationGender gender,
            @Param("contestCategory") AwardRegistrationContestCategory contestCategory);
}

package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardPopularVote;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardPopularVoteRepository extends JpaRepository<AwardPopularVote, UUID> {
    Optional<AwardPopularVote> findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategoryAndVoterUserId(
            UUID editionId, long eventId, long divisionId, long categoryId,
            AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory, UUID voterUserId);

    boolean existsByEditionId(UUID editionId);

    @Modifying
    @Query(value = """
        INSERT INTO award_popular_votes
          (id, edition_id, event_id, division_id, category_id, gender,
           contest_category, entry_id, voter_user_id, created_at)
        VALUES (:id, :editionId, :eventId, :divisionId, :categoryId,
                :gender, :contestCategory, :entryId, :voterUserId, CURRENT_TIMESTAMP)
        ON CONFLICT ON CONSTRAINT uk_award_popular_voter_context DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("editionId") UUID editionId,
                       @Param("eventId") long eventId,
                       @Param("divisionId") long divisionId,
                       @Param("categoryId") long categoryId,
                       @Param("gender") String gender,
                       @Param("contestCategory") String contestCategory,
                       @Param("entryId") UUID entryId,
                       @Param("voterUserId") UUID voterUserId);


    @Query("""
        select v.entryId, count(v)
        from AwardPopularVote v
        where v.editionId = :editionId and v.eventId = :eventId
          and v.divisionId = :divisionId and v.categoryId = :categoryId
          and v.gender = :gender and v.contestCategory = :contestCategory
        group by v.entryId
        """)
    List<Object[]> tally(@Param("editionId") UUID editionId,
                         @Param("eventId") long eventId,
                         @Param("divisionId") long divisionId,
                         @Param("categoryId") long categoryId,
                         @Param("gender") AwardRegistrationGender gender,
                         @Param("contestCategory") AwardRegistrationContestCategory contestCategory);
}

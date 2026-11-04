package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardPreVoteFinalist;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AwardPreVoteFinalistRepository extends JpaRepository<AwardPreVoteFinalist, UUID> {
    boolean existsByEditionId(UUID editionId);

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT EXISTS (
          SELECT 1 FROM award_four_finalist_exceptions x
          WHERE x.edition_id = :editionId AND x.event_id = :eventId
            AND x.division_id = :divisionId AND x.category_id = :categoryId
            AND x.gender = :gender AND x.contest_category = :contestCategory AND x.contest_category = 'FREE_KICK_GOAL'
        )
        """, nativeQuery = true)
    boolean allowsFourFinalists(@org.springframework.data.repository.query.Param("editionId") UUID editionId,
                                @org.springframework.data.repository.query.Param("eventId") long eventId,
                                @org.springframework.data.repository.query.Param("divisionId") long divisionId,
                                @org.springframework.data.repository.query.Param("categoryId") long categoryId,
                                @org.springframework.data.repository.query.Param("gender") String gender,
                                @org.springframework.data.repository.query.Param("contestCategory") String contestCategory);

    boolean existsByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
            UUID editionId, long eventId, long divisionId, long categoryId,
            AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory);

    List<AwardPreVoteFinalist> findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategoryOrderByRankAsc(
            UUID editionId, long eventId, long divisionId, long categoryId,
            AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory);
}

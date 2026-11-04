package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardPreVoteFinalist;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AwardPopularVotingFinalistRepository extends Repository<AwardPreVoteFinalist, UUID> {
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

    @Query("""
        select r.eventId, r.divisionId, r.divisionName,
               r.categoryId, r.categoryName, r.gender, f.contestCategory
        from AwardPreVoteFinalist f, AwardRegistrationEntry e, AwardRegistration r
        where f.entryId = e.id and e.registrationId = r.id
          and f.editionId = :editionId and r.editionId = :editionId
        group by r.eventId, r.divisionId, r.divisionName,
                 r.categoryId, r.categoryName, r.gender, f.contestCategory
        having count(f) in (3, 4)
        """)
    List<Object[]> contexts(@Param("editionId") UUID editionId);

    @Query("""
        select f from AwardPreVoteFinalist f
        where f.editionId = :editionId and f.eventId = :eventId
          and f.divisionId = :divisionId and f.categoryId = :categoryId
          and f.gender = :gender and f.contestCategory = :contestCategory
        order by f.rank asc
        """)
    List<AwardPreVoteFinalist> finalists(@Param("editionId") UUID editionId,
                                       @Param("eventId") long eventId,
                                       @Param("divisionId") long divisionId,
                                       @Param("categoryId") long categoryId,
                                       @Param("gender") AwardRegistrationGender gender,
                                       @Param("contestCategory") AwardRegistrationContestCategory contestCategory);
}

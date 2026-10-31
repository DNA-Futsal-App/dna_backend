package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AwardPreVoteQueryRepository extends Repository<AwardRegistrationEntry, UUID> {

    @Query("""
        select distinct r.eventId, r.divisionId, r.divisionName,
                        r.categoryId, r.categoryName, r.gender, e.contestCategory
        from AwardRegistration r, AwardRegistrationEntry e
        where e.registrationId = r.id
          and r.editionId = :editionId
          and r.status = br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus.SUBMITTED
          and e.reviewStatus = br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus.APPROVED
          and e.mediaStatus = br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus.READY
        order by r.eventId, r.divisionId, r.categoryId, r.gender, e.contestCategory
        """)
    List<Object[]> findContexts(@Param("editionId") UUID editionId);

    @Query("""
        select e from AwardRegistrationEntry e, AwardRegistration r
        where e.registrationId = r.id
          and r.editionId = :editionId
          and r.eventId = :eventId and r.divisionId = :divisionId
          and r.categoryId = :categoryId and r.gender = :gender
          and e.contestCategory = :contestCategory
          and r.status = br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus.SUBMITTED
          and e.reviewStatus = br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus.APPROVED
          and e.mediaStatus = br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus.READY
        order by r.registrationNumber
        """)
    List<AwardRegistrationEntry> findApproved(
            @Param("editionId") UUID editionId, @Param("eventId") long eventId,
            @Param("divisionId") long divisionId, @Param("categoryId") long categoryId,
            @Param("gender") AwardRegistrationGender gender,
            @Param("contestCategory") AwardRegistrationContestCategory contestCategory);

    @Query("""
        select count(e) from AwardRegistrationEntry e, AwardRegistration r
        where e.registrationId = r.id
          and r.editionId = :editionId
          and r.eventId = :eventId and r.divisionId = :divisionId
          and r.categoryId = :categoryId and r.gender = :gender
          and e.contestCategory = :contestCategory
          and r.status = br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus.SUBMITTED
          and e.reviewStatus = br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus.PENDING_REVIEW
        """)
    long countPending(
            @Param("editionId") UUID editionId, @Param("eventId") long eventId,
            @Param("divisionId") long divisionId, @Param("categoryId") long categoryId,
            @Param("gender") AwardRegistrationGender gender,
            @Param("contestCategory") AwardRegistrationContestCategory contestCategory);
}

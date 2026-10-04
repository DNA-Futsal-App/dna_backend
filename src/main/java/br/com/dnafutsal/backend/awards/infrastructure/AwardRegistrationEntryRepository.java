package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardRegistrationEntryRepository
        extends JpaRepository<AwardRegistrationEntry, UUID> {

    List<AwardRegistrationEntry> findByRegistrationIdOrderByCreatedAtAsc(
            UUID registrationId
    );

    List<AwardRegistrationEntry> findByRegistrationIdInOrderByCreatedAtAsc(
            Collection<UUID> registrationIds
    );

    Optional<AwardRegistrationEntry> findByIdAndRegistrationId(
            UUID id,
            UUID registrationId
    );

    List<AwardRegistrationEntry> findByMediaStatus(
            AwardRegistrationMediaStatus mediaStatus
    );

    @Query("""
            select count(entry)
            from AwardRegistrationEntry entry
            where entry.reviewStatus = :reviewStatus
              and entry.registrationId in (
                    select registration.id
                    from AwardRegistration registration
                    where registration.editionId = :editionId
                      and registration.status = :registrationStatus
              )
            """)
    long countForEditionAndReviewStatus(
            @Param("editionId") UUID editionId,
            @Param("registrationStatus") AwardRegistrationStatus registrationStatus,
            @Param("reviewStatus") AwardRegistrationReviewStatus reviewStatus
    );
}

package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AwardRegistrationRepository
        extends JpaRepository<AwardRegistration, UUID> {

    Optional<AwardRegistration> findByIdAndRepresentativeUserId(
            UUID id,
            UUID representativeUserId
    );

    Optional<AwardRegistration> findByEditionIdAndRepresentativeUserId(
            UUID editionId,
            UUID representativeUserId
    );

    boolean existsByEditionIdAndRepresentativeUserId(
            UUID editionId,
            UUID representativeUserId
    );

    Page<AwardRegistration> findByEditionIdAndStatus(
            UUID editionId,
            AwardRegistrationStatus status,
            Pageable pageable
    );

    @Query("""
            select registration
            from AwardRegistration registration
            where registration.editionId = :editionId
              and registration.status = :registrationStatus
              and exists (
                    select entry.id
                    from AwardRegistrationEntry entry
                    where entry.registrationId = registration.id
                      and entry.reviewStatus = :reviewStatus
              )
            """)
    Page<AwardRegistration> findAdminReviewPage(
            @Param("editionId") UUID editionId,
            @Param("registrationStatus") AwardRegistrationStatus registrationStatus,
            @Param("reviewStatus")
            br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus reviewStatus,
            Pageable pageable
    );

    boolean existsByEditionIdAndAthleteInstagramNormalized(
            UUID editionId,
            String athleteInstagramNormalized
    );

    @Query(
            value = "select nextval('award_registration_number_seq')",
            nativeQuery = true
    )
    long nextRegistrationNumber();
}

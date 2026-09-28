package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

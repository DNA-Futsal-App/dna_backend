package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardRegistrationEntryRepository
        extends JpaRepository<AwardRegistrationEntry, UUID> {

    List<AwardRegistrationEntry> findByRegistrationIdOrderByCreatedAtAsc(
            UUID registrationId
    );

    Optional<AwardRegistrationEntry> findByIdAndRegistrationId(
            UUID id,
            UUID registrationId
    );

    List<AwardRegistrationEntry> findByMediaStatus(AwardRegistrationMediaStatus mediaStatus);
}

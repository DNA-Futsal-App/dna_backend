package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AwardVideoProcessingFinalizer {

    private final AwardRegistrationEntryRepository entries;
    private final AwardRegistrationRepository registrations;
    private final Clock clock;

    public AwardVideoProcessingFinalizer(
            AwardRegistrationEntryRepository entries,
            AwardRegistrationRepository registrations,
            Clock clock
    ) {
        this.entries = entries;
        this.registrations = registrations;
        this.clock = clock;
    }

    @Transactional
    public boolean complete(
            UUID registrationId,
            UUID entryId,
            String expectedPendingObjectName,
            String newObjectName,
            String displayFilename,
            long durationMs,
            int width,
            int height,
            long fileSizeBytes
    ) {
        AwardRegistrationEntry entry =
                entries.findByIdAndRegistrationId(
                                entryId,
                                registrationId
                        )
                        .orElse(null);

        if (entry == null) {
            return false;
        }

        /*
         * Impede que um processamento antigo sobrescreva
         * um upload mais novo feito pelo usuário.
         */
        if (!Objects.equals(
                expectedPendingObjectName,
                entry.getPendingObjectName()
        )) {
            return false;
        }

        if (entry.getMediaStatus()
                != AwardRegistrationMediaStatus.PROCESSING) {
            return false;
        }

        entry.completeUpload(
                newObjectName,
                displayFilename,
                durationMs,
                width,
                height,
                fileSizeBytes
        );

        entries.saveAndFlush(
                entry
        );

        submitAutomaticallyIfReady(
                registrationId
        );

        return true;
    }

    @Transactional
    public void fail(
            UUID registrationId,
            UUID entryId,
            String expectedPendingObjectName
    ) {
        AwardRegistrationEntry entry =
                entries.findByIdAndRegistrationId(
                                entryId,
                                registrationId
                        )
                        .orElse(null);

        if (entry == null) {
            return;
        }

        if (!Objects.equals(
                expectedPendingObjectName,
                entry.getPendingObjectName()
        )) {
            return;
        }

        entry.failProcessing();

        entries.saveAndFlush(
                entry
        );
    }

    private void submitAutomaticallyIfReady(
            UUID registrationId
    ) {
        AwardRegistration registration =
                registrations.findById(
                                registrationId
                        )
                        .orElse(null);

        if (registration == null
                || registration.getStatus()
                == AwardRegistrationStatus.CANCELLED) {
            return;
        }

        List<AwardRegistrationEntry> mediaEntries =
                entries.findByRegistrationIdOrderByCreatedAtAsc(
                        registrationId
                );

        if (mediaEntries.isEmpty()) {
            return;
        }

        boolean allReady =
                mediaEntries.stream()
                        .allMatch(entry ->
                                entry.getMediaStatus()
                                        == AwardRegistrationMediaStatus.READY
                        );

        if (!allReady) {
            return;
        }

        if (registration.getStatus()
                != AwardRegistrationStatus.SUBMITTED) {

            registration.submit(
                    clock.instant()
            );

            registrations.saveAndFlush(
                    registration
            );
        }
    }
}
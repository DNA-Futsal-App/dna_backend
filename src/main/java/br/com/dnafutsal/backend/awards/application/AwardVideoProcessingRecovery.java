package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AwardVideoProcessingRecovery {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AwardVideoProcessingRecovery.class
            );

    private final AwardRegistrationEntryRepository entries;

    private final AwardVideoProcessingService processing;

    public AwardVideoProcessingRecovery(
            AwardRegistrationEntryRepository entries,
            AwardVideoProcessingService processing
    ) {
        this.entries = entries;
        this.processing = processing;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<AwardRegistrationEntry> pending =
                entries.findByMediaStatus(
                        AwardRegistrationMediaStatus.PROCESSING
                );

        if (pending.isEmpty()) {
            return;
        }

        log.info(
                "Recovering {} award video processing job(s)",
                pending.size()
        );

        for (
                AwardRegistrationEntry entry :
                pending
        ) {
            String pendingObjectName =
                    entry.getPendingObjectName();

            if (
                    pendingObjectName == null
                            || pendingObjectName.isBlank()
            ) {
                log.warn(
                        "Cannot recover award video processing "
                                + "registrationId={} entryId={} "
                                + "because pendingObjectName is empty",
                        entry.getRegistrationId(),
                        entry.getId()
                );

                continue;
            }

            processing.processAsync(
                    entry.getRegistrationId(),
                    entry.getId(),
                    pendingObjectName
            );
        }
    }
}
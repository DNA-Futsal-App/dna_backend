package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AwardVideoProcessingRecovery {

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

        entries.findByMediaStatus(
                        AwardRegistrationMediaStatus.PROCESSING
                )
                .forEach(entry -> {

                    String pendingObject =
                            entry.getPendingObjectName();

                    if (pendingObject == null
                            || pendingObject.isBlank()) {
                        return;
                    }

                    processing.processAsync(
                            entry.getRegistrationId(),
                            entry.getId(),
                            pendingObject
                    );
                });
    }
}
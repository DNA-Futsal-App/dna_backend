package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AwardVideoProcessingService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AwardVideoProcessingService.class
            );

    private final AwardRegistrationEntryRepository entries;
    private final AwardRegistrationRepository registrations;
    private final AwardEditionRepository editions;
    private final OracleAwardVideoStorage storage;
    private final AwardVideoProcessor processor;
    private final AwardVideoProcessingFinalizer finalizer;

    public AwardVideoProcessingService(
            AwardRegistrationEntryRepository entries,
            AwardRegistrationRepository registrations,
            AwardEditionRepository editions,
            OracleAwardVideoStorage storage,
            AwardVideoProcessor processor,
            AwardVideoProcessingFinalizer finalizer
    ) {
        this.entries = entries;
        this.registrations = registrations;
        this.editions = editions;
        this.storage = storage;
        this.processor = processor;
        this.finalizer = finalizer;
    }

    @Async("awardVideoExecutor")
    public void processAsync(
            UUID registrationId,
            UUID entryId,
            String expectedPendingObjectName
    ) {
        Path raw = null;

        AwardVideoProcessor.ProcessedVideo processed =
                null;

        String newObjectName =
                null;

        String pendingParId =
                null;

        String oldObjectName;

        try {
            AwardRegistrationEntry entry =
                    entries.findByIdAndRegistrationId(
                                    entryId,
                                    registrationId
                            )
                            .orElse(null);

            if (entry == null) {
                return;
            }

            if (entry.getMediaStatus()
                    != AwardRegistrationMediaStatus.PROCESSING) {
                return;
            }

            if (!Objects.equals(
                    expectedPendingObjectName,
                    entry.getPendingObjectName()
            )) {
                return;
            }

            pendingParId =
                    entry.getPendingParId();

            oldObjectName =
                    entry.getObjectName();

            raw =
                    Files.createTempFile(
                            "dna-award-upload-",
                            ".bin"
                    );

            storage.download(
                    expectedPendingObjectName,
                    raw
            );

            processed =
                    processor.process(
                            raw
                    );

            AwardRegistration registration =
                    registrations.findById(
                                    registrationId
                            )
                            .orElseThrow();

            AwardEdition edition =
                    editions.findById(
                                    registration.getEditionId()
                            )
                            .orElseThrow();

            newObjectName =
                    processedObjectName(
                            edition.getSeason(),
                            registration,
                            entry
                    );

            storage.uploadProcessedVideo(
                    newObjectName,
                    processed.path()
            );

            boolean applied =
                    finalizer.complete(
                            registrationId,
                            entryId,
                            expectedPendingObjectName,
                            newObjectName,
                            displayFilename(
                                    registration,
                                    entry
                            ),
                            processed.durationMs(),
                            processed.width(),
                            processed.height(),
                            processed.fileSizeBytes()
                    );

            if (!applied) {
                /*
                 * O usuário iniciou outro upload enquanto
                 * este job ainda processava.
                 */
                storage.deleteObjectQuietly(
                        newObjectName
                );

                return;
            }

            /*
             * Só apagamos o vídeo anterior depois do DB
             * apontar com sucesso para o novo.
             */
            if (oldObjectName != null
                    && !oldObjectName.isBlank()
                    && !oldObjectName.equals(
                    newObjectName
            )) {

                storage.deleteObjectQuietly(
                        oldObjectName
                );
            }

            log.info(
                    "Award video processing completed registrationId={} entryId={}",
                    registrationId,
                    entryId
            );

        } catch (Exception exception) {

            if (newObjectName != null) {
                storage.deleteObjectQuietly(
                        newObjectName
                );
            }

            finalizer.fail(
                    registrationId,
                    entryId,
                    expectedPendingObjectName
            );

            log.error(
                    "Award video processing failed registrationId={} entryId={}",
                    registrationId,
                    entryId,
                    exception
            );

        } finally {

            storage.deleteObjectQuietly(
                    expectedPendingObjectName
            );

            storage.deleteParQuietly(
                    pendingParId
            );

            if (processed != null
                    && processed.path() != null
                    && !processed.path()
                    .equals(raw)) {

                AwardVideoProcessor.deleteQuietly(
                        processed.path()
                );
            }

            AwardVideoProcessor.deleteQuietly(
                    raw
            );
        }
    }

    private String processedObjectName(
            int season,
            AwardRegistration registration,
            AwardRegistrationEntry entry
    ) {
        return "premio-dna/"
                + season
                + "/inscricoes/"
                + String.format(
                Locale.ROOT,
                "%06d",
                registration.getRegistrationNumber()
        )
                + "/"
                + entry.getId()
                + "/"
                + UUID.randomUUID()
                + ".mp4";
    }

    private String displayFilename(
            AwardRegistration registration,
            AwardRegistrationEntry entry
    ) {
        return slugPart(
                registration.getDivisionName()
        )
                + "_"
                + slugPart(
                registration.getCategoryName()
        )
                + "_"
                + slugPart(
                registration.getAthleteName()
        )
                + "_"
                + entry.getContestCategory()
                .storageSlug()
                + "_"
                + registration.getRepresentativeUserId()
                + "_"
                + String.format(
                Locale.ROOT,
                "%06d",
                registration.getRegistrationNumber()
        )
                + ".mp4";
    }

    private String slugPart(
            String value
    ) {
        String ascii =
                Normalizer.normalize(
                                value,
                                Normalizer.Form.NFD
                        )
                        .replaceAll(
                                "\\p{M}+",
                                ""
                        )
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^a-z0-9]+",
                                "_"
                        )
                        .replaceAll(
                                "^_+|_+$",
                                ""
                        );

        return ascii.isBlank()
                ? "item"
                : ascii;
    }
}
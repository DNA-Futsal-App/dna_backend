package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AwardRegistrationProperties;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AwardRegistrationService {

    private final CurrentUserService currentUser;
    private final UserAccountRepository users;
    private final AwardEditionRepository editions;
    private final AwardRegistrationRepository registrations;
    private final AwardRegistrationEntryRepository entries;
    private final SportsCatalogService catalog;
    private final AwardCpfCipher cpfCipher;
    private final OracleAwardVideoStorage storage;
    private final AwardVideoProcessor videoProcessor;
    private final AwardRegistrationProperties properties;
    private final Clock clock;
    private static final Duration MEDIA_READ_TTL = Duration.ofMinutes(30);
    private final AwardVideoProcessingService videoProcessing;

    public AwardRegistrationService(
            CurrentUserService currentUser,
            UserAccountRepository users,
            AwardEditionRepository editions,
            AwardRegistrationRepository registrations,
            AwardRegistrationEntryRepository entries,
            SportsCatalogService catalog,
            AwardCpfCipher cpfCipher,
            OracleAwardVideoStorage storage,
            AwardVideoProcessor videoProcessor,
            AwardRegistrationProperties properties,
            Clock clock,
            AwardVideoProcessingService videoProcessing
    ) {
        this.currentUser = currentUser;
        this.users = users;
        this.editions = editions;
        this.registrations = registrations;
        this.entries = entries;
        this.catalog = catalog;
        this.cpfCipher = cpfCipher;
        this.storage = storage;
        this.videoProcessor = videoProcessor;
        this.properties = properties;
        this.clock = clock;
        this.videoProcessing = videoProcessing;
    }

    @Transactional(readOnly = true)
    public AwardRegistrationContextResponse context() {
        AwardEdition edition =
                requireEdition();

        List<AwardRegistrationContestCategoryResponse> contestCategories =
                java.util.Arrays.stream(
                                AwardRegistrationContestCategory.values()
                        )
                        .map(category ->
                                new AwardRegistrationContestCategoryResponse(
                                        category,
                                        category.label()
                                )
                        )
                        .toList();

        return new AwardRegistrationContextResponse(
                edition.getName(),
                edition.getSeason(),
                edition.isRegistrationsOpen(),
                properties.effectiveMaxUploadBytes(),
                properties.effectiveMaxDurationSeconds(),
                contestCategories
        );
    }

    @Transactional(readOnly = true)
    public AwardRegistrationResponse current() {
        AwardEdition edition =
                requireEdition();

        UUID userId =
                currentUser.userId();

        AwardRegistration registration =
                registrations
                        .findByEditionIdAndRepresentativeUserId(
                                edition.getId(),
                                userId
                        )
                        .orElseThrow(() ->
                                Errors.notFound(
                                        "AWARD_REGISTRATION_NOT_FOUND",
                                        "Esta conta ainda não possui inscrição nesta edição."
                                )
                        );

        return response(
                registration,
                entries.findByRegistrationIdOrderByCreatedAtAsc(
                        registration.getId()
                )
        );
    }

    @Transactional
    public AwardRegistrationResponse create(
            CreateAwardRegistrationRequest request
    ) {
        UUID userId =
                currentUser.userId();

        AwardEdition edition =
                requireEdition();

        requireRegistrationsOpen(
                edition
        );

        UserAccount user =
                users.findById(
                                userId
                        )
                        .orElseThrow(() ->
                                Errors.notFound(
                                        "USER_NOT_FOUND",
                                        "Usuário não encontrado."
                                )
                        );

        String athleteInstagram =
                user.getChildInstagram();

        String normalizedInstagram =
                normalizeInstagram(
                        athleteInstagram
                );

        if (normalizedInstagram.isBlank()) {
            throw Errors.badRequest(
                    "AWARD_PROFILE_INSTAGRAM_REQUIRED",
                    "Preencha o Instagram do atleta no perfil antes de realizar a inscrição."
            );
        }

        String cpf =
                BrazilianCpf.normalize(
                        request.representativeCpf()
                );

        if (!BrazilianCpf.isValid(
                cpf
        )) {
            throw Errors.badRequest(
                    "AWARD_CPF_INVALID",
                    "Informe um CPF válido para o representante do atleta."
            );
        }

        validateRequestedEntries(
                request.entries()
        );

        if (registrations
                .existsByEditionIdAndRepresentativeUserId(
                        edition.getId(),
                        userId
                )) {
            throw Errors.conflict(
                    "AWARD_REGISTRATION_ALREADY_EXISTS",
                    "Esta conta já possui uma inscrição nesta edição."
            );
        }

        if (registrations
                .existsByEditionIdAndAthleteInstagramNormalized(
                        edition.getId(),
                        normalizedInstagram
                )) {
            throw Errors.conflict(
                    "AWARD_ATHLETE_ALREADY_REGISTERED",
                    "Este atleta já foi inscrito por outro representante."
            );
        }

        CatalogContext sports =
                validateSportsContext(
                        edition.getSeason(),
                        request.gender(),
                        request.divisionId(),
                        request.categoryId(),
                        request.teamId()
                );

        AwardRegistration registration =
                new AwardRegistration(
                                        registrations.nextRegistrationNumber(),
                                        edition.getId(),
                                        userId,
                                        cpfCipher.encrypt(
                                                cpf
                                        ),
                                        request.athleteName()
                                                .trim(),
                                        athleteInstagram.trim(),
                                        normalizedInstagram,

                        sports.category()
                                                .eventId(),

                        sports.division()
                                                .id(),

                        sports.division()
                                                .name(),

                        sports.category()
                                                .id(),

                        sports.category()
                                                .name(),

                        sports.team()
                                                .id(),

                        sports.team()
                                                .name(),

                        request.gender()
                                );

        try {
            registrations.saveAndFlush(
                    registration
            );

            List<AwardRegistrationEntry> mediaEntries =
                    request.entries()
                            .stream()
                            .map(entry ->
                                    new AwardRegistrationEntry(
                                            registration.getId(),
                                            entry.contestCategory(),
                                            entry.sourceType(),
                                            entry.sourceType()
                                                    == AwardRegistrationMediaSource.LINK
                                                    ? validateExternalUrl(
                                                            entry.externalUrl()
                                                    )
                                                    : null
                                    )
                            )
                            .toList();

            entries.saveAllAndFlush(
                    mediaEntries
            );

            return response(
                    registration,
                    mediaEntries
            );
        } catch (DataIntegrityViolationException exception) {
            throw Errors.conflict(
                    "AWARD_REGISTRATION_ALREADY_EXISTS",
                    "O atleta ou esta conta já possuem uma inscrição nesta edição."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<CatalogItemView> divisions(
            AwardRegistrationGender gender
    ) {
        AwardEdition edition =
                requireEdition();

        return catalog.divisions(
                edition.getSeason(),
                gender.catalogTitle()
        );
    }

    @Transactional(readOnly = true)
    public List<CatalogCategoryView> categories(
            AwardRegistrationGender gender,
            long divisionId
    ) {
        AwardEdition edition =
                requireEdition();

        return catalog.categories(
                edition.getSeason(),
                gender.catalogTitle(),
                divisionId
        );
    }

    @Transactional
    public AwardUploadTicketResponse createUploadTicket(
            UUID registrationId,
            UUID entryId,
            CreateAwardUploadTicketRequest request
    ) {
        if (request.sizeBytes()
                > properties.effectiveMaxUploadBytes()) {
            throw Errors.badRequest(
                    "AWARD_UPLOAD_TOO_LARGE",
                    "O vídeo excede o limite permitido antes da compactação."
            );
        }

        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        requireNotCancelled(
                registration
        );

        AwardRegistrationEntry entry =
                requireEntry(
                        registrationId,
                        entryId
                );

        storage.deleteObjectQuietly(
                entry.getPendingObjectName()
        );

        storage.deleteParQuietly(
                entry.getPendingParId()
        );

        String temporaryObjectName =
                "tmp/award-registration/"
                        + registration.getId()
                        + "/"
                        + entry.getId()
                        + "/"
                        + UUID.randomUUID();

        Instant expiresAt =
                clock.instant()
                        .plus(
                                properties.effectiveUploadTicketTtl()
                        );

        OracleAwardVideoStorage.WriteTicket ticket =
                storage.createWriteTicket(
                        temporaryObjectName,
                        "award-upload-"
                                + entry.getId()
                                + "-"
                                + UUID.randomUUID(),
                        expiresAt
                );

        entry.beginUpload(
                temporaryObjectName,
                ticket.parId()
        );

        entries.saveAndFlush(
                entry
        );

        return new AwardUploadTicketResponse(
                ticket.uploadUrl(),
                ticket.expiresAt(),
                properties.effectiveMaxUploadBytes()
        );
    }

    @Transactional
    public AwardRegistrationEntryResponse completeUpload(
            UUID registrationId,
            UUID entryId
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        requireWritable(
                registration
        );

        AwardRegistrationEntry entry =
                requireEntry(
                        registrationId,
                        entryId
                );

        if (
                entry.getSourceType()
                        != AwardRegistrationMediaSource.UPLOAD
        ) {
            throw Errors.badRequest(
                    "AWARD_ENTRY_NOT_UPLOAD",
                    "Esta categoria não recebe arquivo de vídeo."
            );
        }

        /*
         * Idempotência:
         *
         * caso o frontend repita a chamada,
         * não devemos iniciar outro processamento.
         */
        if (
                entry.getMediaStatus()
                        == AwardRegistrationMediaStatus.PROCESSING
        ) {
            return entryResponse(
                    entry
            );
        }

        if (
                entry.getMediaStatus()
                        == AwardRegistrationMediaStatus.READY
        ) {
            return entryResponse(
                    entry
            );
        }

        String pendingObjectName =
                entry.getPendingObjectName();

        if (
                pendingObjectName == null
                        || pendingObjectName.isBlank()
        ) {
            throw Errors.badRequest(
                    "AWARD_UPLOAD_TICKET_REQUIRED",
                    "Solicite um novo upload antes de concluir o processamento."
            );
        }
        long uploadedSize =
                storage.objectSize(
                        pendingObjectName
                );

        if (
                uploadedSize
                        > properties.effectiveMaxUploadBytes()
        ) {
            throw Errors.badRequest(
                    "AWARD_UPLOAD_TOO_LARGE",
                    "O vídeo excede o limite permitido antes da compactação."
            );
        }

        /*
         * A requisição deixa de ser responsável
         * pelo processamento.
         */
        entry.beginProcessing();

        AwardRegistrationEntry saved =
                entries.saveAndFlush(
                        entry
                );

        /*
         * Só inicia o worker depois do COMMIT.
         *
         * Isso evita o worker enxergar PENDING
         * enquanto a transação atual ainda não
         * terminou.
         */
        Runnable dispatch =
                () ->
                        videoProcessing.processAsync(
                                registrationId,
                                entryId,
                                pendingObjectName
                        );

        if (
                TransactionSynchronizationManager
                        .isSynchronizationActive()
        ) {
            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {

                                @Override
                                public void afterCommit() {
                                    dispatch.run();
                                }
                            }
                    );
        } else {
            dispatch.run();
        }

        return entryResponse(
                saved
        );
    }

    @Transactional
    public AwardRegistrationEntryResponse updateLink(
            UUID registrationId,
            UUID entryId,
            UpdateAwardRegistrationLinkRequest request
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        requireWritable(
                registration
        );

        AwardRegistrationEntry entry =
                requireEntry(
                        registrationId,
                        entryId
                );

        StorageCleanup cleanup =
                storageCleanup(
                        entry
                );

        entry.updateExternalUrl(
                validateExternalUrl(
                        request.url()
                )
        );

        AwardRegistrationEntry saved =
                entries.saveAndFlush(
                        entry
                );

        scheduleStorageCleanupAfterCommit(
                List.of(
                        cleanup
                )
        );

        return entryResponse(
                saved
        );
    }

    @Transactional
    public AwardRegistrationResponse submit(
            UUID registrationId
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        if (registration.getStatus()
                == AwardRegistrationStatus.SUBMITTED) {
            return response(
                    registration,
                    entries.findByRegistrationIdOrderByCreatedAtAsc(
                            registrationId
                    )
            );
        }

        requireWritable(
                registration
        );

        List<AwardRegistrationEntry> mediaEntries =
                entries.findByRegistrationIdOrderByCreatedAtAsc(
                        registrationId
                );

        if (mediaEntries.isEmpty()
                || mediaEntries.size() > 4) {
            throw Errors.badRequest(
                    "AWARD_REGISTRATION_ENTRIES_INVALID",
                    "A inscrição precisa possuir entre 1 e 4 categorias."
            );
        }

        boolean hasPendingMedia =
                mediaEntries.stream()
                        .anyMatch(entry ->
                                entry.getMediaStatus()
                                        != AwardRegistrationMediaStatus.READY
                        );

        if (hasPendingMedia) {
            throw Errors.badRequest(
                    "AWARD_MEDIA_PENDING",
                    "Conclua o envio de todas as mídias antes de registrar a inscrição."
            );
        }

        registration.submit(
                clock.instant()
        );

        AwardRegistration saved =
                registrations.saveAndFlush(
                        registration
                );

        return response(
                saved,
                mediaEntries
        );
    }

    @Transactional
    public AwardRegistrationEntryResponse addEntry(
            UUID registrationId,
            CreateAwardRegistrationEntryRequest request
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        requireRegistrationsOpen(
                registration
        );

        List<AwardRegistrationEntry> currentEntries =
                entries.findByRegistrationIdOrderByCreatedAtAsc(
                        registrationId
                );
        if (currentEntries.size() >= 4) {
            throw Errors.badRequest(
                    "AWARD_REGISTRATION_ENTRIES_INVALID",
                    "A inscrição pode possuir no máximo 4 categorias."
            );
        }

        validateRequestedEntries(
                List.of(
                        request
                )
        );
        boolean categoryAlreadyExists =
                currentEntries.stream()
                        .anyMatch(entry ->
                                entry.getContestCategory()
                                        == request.contestCategory()
                        );

        if (categoryAlreadyExists) {
            throw Errors.badRequest(
                    "AWARD_REGISTRATION_DUPLICATE_CATEGORY",
                    "Esta categoria do prêmio já faz parte da inscrição."
            );
        }
        registration.reopenForEditing();

        String externalUrl = null;

        if (
                request.sourceType()
                        == AwardRegistrationMediaSource.LINK
        ) {
            externalUrl =
                    validateExternalUrl(
                            request.externalUrl()
                    );
        }

        AwardRegistrationEntry entry =
                new AwardRegistrationEntry(
                        registration.getId(),
                        request.contestCategory(),
                        request.sourceType(),
                        externalUrl
                );

        try {
            registrations.saveAndFlush(
                    registration
            );

            AwardRegistrationEntry saved =
                    entries.saveAndFlush(
                            entry
                    );

            return entryResponse(
                    saved
            );

        } catch (DataIntegrityViolationException exception) {

            throw Errors.conflict(
                    "AWARD_REGISTRATION_DUPLICATE_CATEGORY",
                    "Esta categoria do prêmio já faz parte da inscrição."
            );
        }
    }

    @Transactional(readOnly = true)
    public AwardMediaTicketResponse createMediaTicket(
            UUID registrationId,
            UUID entryId
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        if (registration.getStatus()
                == AwardRegistrationStatus.CANCELLED) {
            throw Errors.conflict(
                    "AWARD_REGISTRATION_CANCELLED",
                    "Esta inscrição foi cancelada."
            );
        }

        AwardRegistrationEntry entry =
                requireEntry(
                        registrationId,
                        entryId
                );

        if (entry.getSourceType()
                != AwardRegistrationMediaSource.UPLOAD) {
            throw Errors.badRequest(
                    "AWARD_ENTRY_NOT_UPLOAD",
                    "Esta candidatura utiliza um link externo."
            );
        }

        if (entry.getMediaStatus()
                != AwardRegistrationMediaStatus.READY
                || entry.getObjectName() == null
                || entry.getObjectName().isBlank()) {

            throw Errors.conflict(
                    "AWARD_MEDIA_NOT_READY",
                    "O vídeo desta candidatura ainda não está disponível."
            );
        }

        Instant expiresAt =
                clock.instant()
                        .plus(
                                MEDIA_READ_TTL
                        );

        OracleAwardVideoStorage.ReadTicket ticket =
                storage.createReadTicket(
                        entry.getObjectName(),
                        "award-view-"
                                + entry.getId()
                                + "-"
                                + UUID.randomUUID(),
                        expiresAt
                );

        return new AwardMediaTicketResponse(
                ticket.url(),
                ticket.expiresAt()
        );
    }

    @Transactional
    public AwardRegistrationResponse withdrawEntry(
            UUID registrationId,
            UUID entryId
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        requireWritable(
                registration
        );

        AwardRegistrationEntry entry =
                requireEntry(
                        registrationId,
                        entryId
                );

        StorageCleanup cleanup =
                storageCleanup(
                        entry
                );

        entries.delete(
                entry
        );

        entries.flush();

        List<AwardRegistrationEntry> remaining =
                entries.findByRegistrationIdOrderByCreatedAtAsc(
                        registrationId
                );

        if (remaining.isEmpty()) {
            registration.cancel();

            registrations.saveAndFlush(
                    registration
            );
        }

        scheduleStorageCleanupAfterCommit(
                List.of(
                        cleanup
                )
        );

        return response(
                registration,
                remaining
        );
    }

    @Transactional
    public AwardRegistrationResponse withdrawAll(
            UUID registrationId
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        requireWritable(
                registration
        );

        List<AwardRegistrationEntry> registrationEntries =
                entries.findByRegistrationIdOrderByCreatedAtAsc(
                        registrationId
                );

        List<StorageCleanup> cleanup =
                registrationEntries.stream()
                        .map(
                                this::storageCleanup
                        )
                        .toList();

        entries.deleteAll(
                registrationEntries
        );

        entries.flush();

        registration.cancel();

        AwardRegistration saved =
                registrations.saveAndFlush(
                        registration
                );

        scheduleStorageCleanupAfterCommit(
                cleanup
        );

        return response(
                saved,
                List.of()
        );
    }

    @Transactional
    public void deletePermanently(
            UUID registrationId
    ) {
        AwardRegistration registration =
                requireOwnedRegistration(
                        registrationId
                );

        requireRegistrationsOpen(
                registration
        );

        List<AwardRegistrationEntry> registrationEntries =
                entries.findByRegistrationIdOrderByCreatedAtAsc(
                        registrationId
                );

        List<StorageCleanup> cleanup =
                registrationEntries.stream()
                        .map(
                                this::storageCleanup
                        )
                        .toList();

        /*
         * O FK de award_registration_entries usa ON DELETE CASCADE.
         * Excluímos o registro principal e deixamos o banco remover
         * as entries na mesma transação.
         */
        registrations.delete(
                registration
        );

        registrations.flush();

        /*
         * A Oracle só é limpa depois do commit. Se a transação falhar,
         * nenhum objeto é removido.
         */
        scheduleStorageCleanupAfterCommit(
                cleanup
        );
    }

    private StorageCleanup storageCleanup(
            AwardRegistrationEntry entry
    ) {
        return new StorageCleanup(
                entry.getObjectName(),
                entry.getPendingObjectName(),
                entry.getPendingParId()
        );
    }

    private void scheduleStorageCleanupAfterCommit(
            List<StorageCleanup> cleanup
    ) {
        if (cleanup == null
                || cleanup.isEmpty()) {
            return;
        }

        Runnable action =
                () -> cleanup.forEach(
                        item -> {
                            storage.deleteObjectQuietly(
                                    item.objectName()
                            );

                            storage.deleteObjectQuietly(
                                    item.pendingObjectName()
                            );

                            storage.deleteParQuietly(
                                    item.pendingParId()
                            );
                        }
                );

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            action.run();

            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {
                                action.run();
                            }
                        }
                );
    }

    private AwardEdition requireEdition() {
        java.util.Optional<AwardEdition> configured =
                editions.findBySlug(
                        properties.effectiveEditionSlug()
                );

        if (configured.isPresent()) {
            return configured.get();
        }

        List<AwardEdition> available =
                editions.findAllByOrderBySeasonDescNameAsc();

        if (available.size() == 1) {
            return available.get(0);
        }

        throw Errors.dependencyUnavailable(
                "AWARD_REGISTRATION_EDITION_NOT_CONFIGURED",
                "Configure AWARD_REGISTRATION_EDITION_SLUG com o slug da edição do Prêmio DNA Futsal."
        );
    }

    private AwardRegistration requireOwnedRegistration(
            UUID registrationId
    ) {
        UUID userId =
                currentUser.userId();

        return registrations
                .findByIdAndRepresentativeUserId(
                        registrationId,
                        userId
                )
                .orElseThrow(() ->
                        Errors.notFound(
                                "AWARD_REGISTRATION_NOT_FOUND",
                                "Inscrição não encontrada."
                        )
                );
    }

    private AwardRegistrationEntry requireEntry(
            UUID registrationId,
            UUID entryId
    ) {
        return entries
                .findByIdAndRegistrationId(
                        entryId,
                        registrationId
                )
                .orElseThrow(() ->
                        Errors.notFound(
                                "AWARD_REGISTRATION_ENTRY_NOT_FOUND",
                                "Categoria da inscrição não encontrada."
                        )
                );
    }

    private void requireWritable(
            AwardRegistration registration
    ) {
        requireNotCancelled(
                registration
        );

        requireRegistrationsOpen(
                registration
        );
    }

    private void requireNotCancelled(
            AwardRegistration registration
    ) {
        if (registration.getStatus()
                == AwardRegistrationStatus.CANCELLED) {
            throw Errors.conflict(
                    "AWARD_REGISTRATION_CANCELLED",
                    "Esta inscrição foi cancelada."
            );
        }
    }

    private void requireRegistrationsOpen(
            AwardRegistration registration
    ) {
        AwardEdition edition =
                editions.findById(
                                registration.getEditionId()
                        )
                        .orElseThrow(() ->
                                Errors.notFound(
                                        "AWARD_EDITION_NOT_FOUND",
                                        "Edição do prêmio não encontrada."
                                )
                        );

        requireRegistrationsOpen(
                edition
        );
    }

    private void requireRegistrationsOpen(
            AwardEdition edition
    ) {
        if (!edition.isRegistrationsOpen()) {
            throw Errors.conflict(
                    "AWARD_REGISTRATIONS_CLOSED",
                    "O período de inscrições do Prêmio Legacy está encerrado."
            );
        }
    }

    private CatalogContext validateSportsContext(
            int season,
            AwardRegistrationGender gender,
            long divisionId,
            long categoryId,
            String teamId
    ) {
        String title =
                gender.catalogTitle();

        CatalogItemView division =
                catalog.divisions(
                                season,
                                title
                        )
                        .stream()
                        .filter(item ->
                                item.id()
                                        == divisionId
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                Errors.badRequest(
                                        "AWARD_DIVISION_INVALID",
                                        "A divisão selecionada não pertence ao gênero informado."
                                )
                        );

        CatalogCategoryView category =
                catalog.categories(
                                season,
                                title,
                                divisionId
                        )
                        .stream()
                        .filter(item ->
                                item.id()
                                        == categoryId
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                Errors.badRequest(
                                        "AWARD_CATEGORY_INVALID",
                                        "A categoria selecionada não pertence à divisão e ao gênero informados."
                                )
                        );

        TeamView team =
                catalog.teams(
                                category.eventId()
                        )
                        .stream()
                        .filter(item ->
                                item.id()
                                        .equals(
                                                teamId
                                        )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                Errors.badRequest(
                                        "AWARD_TEAM_INVALID",
                                        "O time selecionado não pertence ao gênero, divisão e categoria informados."
                                )
                        );

        return new CatalogContext(
                division,
                category,
                team
        );
    }

    private void validateRequestedEntries(
            List<CreateAwardRegistrationEntryRequest> requestedEntries
    ) {
        if (requestedEntries == null
                || requestedEntries.isEmpty()
                || requestedEntries.size() > 4) {
            throw Errors.badRequest(
                    "AWARD_REGISTRATION_ENTRIES_INVALID",
                    "Escolha entre 1 e 4 categorias do prêmio."
            );
        }

        Set<AwardRegistrationContestCategory> unique =
                new HashSet<>();

        for (CreateAwardRegistrationEntryRequest entry
                : requestedEntries) {
            if (!unique.add(
                    entry.contestCategory()
            )) {
                throw Errors.badRequest(
                        "AWARD_REGISTRATION_DUPLICATE_CATEGORY",
                        "A mesma categoria do prêmio não pode ser adicionada duas vezes."
                );
            }

            if (entry.sourceType()
                    == AwardRegistrationMediaSource.LINK) {
                validateExternalUrl(
                        entry.externalUrl()
                );
            }

            if (entry.sourceType()
                    == AwardRegistrationMediaSource.UPLOAD
                    && entry.externalUrl() != null
                    && !entry.externalUrl()
                    .isBlank()) {
                throw Errors.badRequest(
                        "AWARD_UPLOAD_URL_NOT_ALLOWED",
                        "Categorias configuradas para upload não devem enviar link externo."
                );
            }
        }
    }

    private String validateExternalUrl(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            throw Errors.badRequest(
                    "AWARD_MEDIA_URL_REQUIRED",
                    "Informe o link do vídeo."
            );
        }

        try {
            URI uri =
                    new URI(
                            value.trim()
                    );

            if (!"https".equalsIgnoreCase(
                    uri.getScheme()
            )
                    || uri.getHost() == null
                    || uri.getHost()
                    .isBlank()) {
                throw new URISyntaxException(
                        value,
                        "HTTPS URL required."
                );
            }

            return uri.toString();
        } catch (URISyntaxException exception) {
            throw Errors.badRequest(
                    "AWARD_MEDIA_URL_INVALID",
                    "Informe um link HTTPS válido para o vídeo."
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

    private String normalizeInstagram(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return normalized.startsWith(
                "@"
        )
                ? normalized.substring(
                1
        )
                : normalized;
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

    private AwardRegistrationResponse response(
            AwardRegistration registration,
            List<AwardRegistrationEntry> mediaEntries
    ) {
        List<AwardRegistrationEntryResponse> mappedEntries =
                mediaEntries.stream()
                        .map(
                                this::entryResponse
                        )
                        .toList();

        return new AwardRegistrationResponse(
                        registration.getId(),
                        registration.getRegistrationNumber(),
                        registration.getStatus(),
                        registration.getAthleteName(),
                        registration.getAthleteInstagram(),
                registration.getGender(),
                registration.getDivisionId(),
                registration.getDivisionName(),
                registration.getCategoryId(),
                registration.getCategoryName(),
                registration.getEventId(),
                registration.getTeamId(),
                registration.getTeamName(),
                registration.getSubmittedAt(),
                mappedEntries
                );
    }

    private AwardRegistrationEntryResponse entryResponse(
            AwardRegistrationEntry entry
    ) {
        return new AwardRegistrationEntryResponse(
                entry.getId(),
                entry.getContestCategory(),
                entry.getContestCategory()
                        .label(),
                entry.getSourceType(),
                entry.getMediaStatus(),
                entry.getReviewStatus(),
                entry.getExternalUrl(),
                entry.getDisplayFilename(),
                entry.getDurationMs(),
                entry.getWidth(),
                entry.getHeight(),
                entry.getFileSizeBytes(),
                entry.getReviewedAt(),
                entry.getReviewReason()
        );
    }

    private record CatalogContext(
            CatalogItemView division,
            CatalogCategoryView category,
            TeamView team
    ) {
    }

    private record StorageCleanup(
            String objectName,
            String pendingObjectName,
            String pendingParId
    ) {
    }
}

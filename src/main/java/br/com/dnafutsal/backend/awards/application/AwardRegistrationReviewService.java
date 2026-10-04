package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardAdminRegistrationEntryResponse;
import br.com.dnafutsal.backend.awards.api.AwardAdminRegistrationPageResponse;
import br.com.dnafutsal.backend.awards.api.AwardAdminRegistrationResponse;
import br.com.dnafutsal.backend.awards.api.AwardMediaTicketResponse;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaSource;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.mail.application.MailOutboxService;
import br.com.dnafutsal.backend.mail.application.MailTemplateFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AwardRegistrationReviewService {

    private static final Duration MEDIA_READ_TTL =
            Duration.ofMinutes(30);

    private final CurrentUserService currentUser;
    private final UserAccountRepository users;
    private final AwardEditionRepository editions;
    private final AwardRegistrationRepository registrations;
    private final AwardRegistrationEntryRepository entries;
    private final OracleAwardVideoStorage storage;
    private final MailTemplateFactory mailTemplates;
    private final MailOutboxService mailOutbox;
    private final Clock clock;

    public AwardRegistrationReviewService(
            CurrentUserService currentUser,
            UserAccountRepository users,
            AwardEditionRepository editions,
            AwardRegistrationRepository registrations,
            AwardRegistrationEntryRepository entries,
            OracleAwardVideoStorage storage,
            MailTemplateFactory mailTemplates,
            MailOutboxService mailOutbox,
            Clock clock
    ) {
        this.currentUser = currentUser;
        this.users = users;
        this.editions = editions;
        this.registrations = registrations;
        this.entries = entries;
        this.storage = storage;
        this.mailTemplates = mailTemplates;
        this.mailOutbox = mailOutbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AwardAdminRegistrationPageResponse page(
            UUID editionId,
            AwardRegistrationReviewStatus reviewStatus,
            int page,
            int size
    ) {
        if (page < 0
                || size < 1
                || size > 50) {
            throw Errors.badRequest(
                    "AWARD_ADMIN_PAGE_INVALID",
                    "Use page >= 0 e size entre 1 e 50."
            );
        }

        requireEdition(
                editionId
        );

        PageRequest pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Order.desc(
                                        "submittedAt"
                                ),
                                Sort.Order.desc(
                                        "registrationNumber"
                                )
                        )
                );

        Page<AwardRegistration> registrationPage =
                reviewStatus == null
                        ? registrations.findByEditionIdAndStatus(
                                editionId,
                                AwardRegistrationStatus.SUBMITTED,
                                pageable
                        )
                        : registrations.findAdminReviewPage(
                                editionId,
                                AwardRegistrationStatus.SUBMITTED,
                                reviewStatus,
                                pageable
                        );

        List<AwardRegistration> pageItems =
                registrationPage.getContent();

        List<UUID> registrationIds =
                pageItems.stream()
                        .map(
                                AwardRegistration::getId
                        )
                        .toList();

        List<AwardRegistrationEntry> pageEntries =
                registrationIds.isEmpty()
                        ? List.of()
                        : entries.findByRegistrationIdInOrderByCreatedAtAsc(
                                registrationIds
                        );

        Map<UUID, List<AwardRegistrationEntry>> entriesByRegistration =
                new HashMap<>();

        for (AwardRegistrationEntry entry
                : pageEntries) {
            entriesByRegistration
                    .computeIfAbsent(
                            entry.getRegistrationId(),
                            ignored ->
                                    new ArrayList<>()
                    )
                    .add(
                            entry
                    );
        }

        Set<UUID> accountIds =
                new HashSet<>();

        pageItems.stream()
                .map(
                        AwardRegistration::getRepresentativeUserId
                )
                .forEach(
                        accountIds::add
                );

        pageEntries.stream()
                .map(
                        AwardRegistrationEntry::getReviewedByUserId
                )
                .filter(
                        java.util.Objects::nonNull
                )
                .forEach(
                        accountIds::add
                );

        Map<UUID, UserAccount> accounts =
                new HashMap<>();

        users.findAllById(
                        accountIds
                )
                .forEach(user ->
                        accounts.put(
                                user.getId(),
                                user
                        )
                );

        List<AwardAdminRegistrationResponse> mapped =
                pageItems.stream()
                        .map(registration ->
                                registrationResponse(
                                        registration,
                                        entriesByRegistration.getOrDefault(
                                                registration.getId(),
                                                List.of()
                                        ),
                                        accounts
                                )
                        )
                        .toList();

        long pending =
                count(
                        editionId,
                        AwardRegistrationReviewStatus.PENDING_REVIEW
                );

        long approved =
                count(
                        editionId,
                        AwardRegistrationReviewStatus.APPROVED
                );

        long rejected =
                count(
                        editionId,
                        AwardRegistrationReviewStatus.REJECTED
                );

        return new AwardAdminRegistrationPageResponse(
                mapped,
                registrationPage.getNumber(),
                registrationPage.getSize(),
                registrationPage.getTotalElements(),
                registrationPage.getTotalPages(),
                pending,
                approved,
                rejected
        );
    }

    @Transactional
    public AwardAdminRegistrationEntryResponse approve(
            UUID registrationId,
            UUID entryId
    ) {
        return decide(
                registrationId,
                entryId,
                true,
                null
        );
    }

    @Transactional
    public AwardAdminRegistrationEntryResponse reject(
            UUID registrationId,
            UUID entryId,
            String reason
    ) {
        String normalizedReason =
                reason == null
                        ? ""
                        : reason.trim();

        if (normalizedReason.length()
                < 5) {
            throw Errors.badRequest(
                    "AWARD_REVIEW_REASON_INVALID",
                    "Informe um motivo de reprovação com pelo menos 5 caracteres."
            );
        }

        return decide(
                registrationId,
                entryId,
                false,
                normalizedReason
        );
    }

    @Transactional(readOnly = true)
    public AwardMediaTicketResponse mediaTicket(
            UUID registrationId,
            UUID entryId
    ) {
        AwardRegistration registration =
                requireSubmittedRegistration(
                        registrationId
                );

        AwardRegistrationEntry entry =
                requireEntry(
                        registration.getId(),
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
                || entry.getObjectName()
                .isBlank()) {
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
                        "award-admin-review-"
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

    private AwardAdminRegistrationEntryResponse decide(
            UUID registrationId,
            UUID entryId,
            boolean approved,
            String reason
    ) {
        AwardRegistration registration =
                requireSubmittedRegistration(
                        registrationId
                );

        AwardRegistrationEntry entry =
                requireEntry(
                        registrationId,
                        entryId
                );

        if (entry.getMediaStatus()
                != AwardRegistrationMediaStatus.READY) {
            throw Errors.conflict(
                    "AWARD_MEDIA_NOT_READY",
                    "Somente candidaturas com a mídia pronta podem ser analisadas."
            );
        }

        if (entry.getReviewStatus()
                != AwardRegistrationReviewStatus.PENDING_REVIEW) {
            throw Errors.conflict(
                    "AWARD_CANDIDATE_ALREADY_REVIEWED",
                    "Esta candidatura já possui uma decisão administrativa."
            );
        }

        UUID reviewerUserId =
                currentUser.userId();

        UserAccount reviewer =
                requireUser(
                        reviewerUserId
                );

        UserAccount representative =
                requireUser(
                        registration.getRepresentativeUserId()
                );

        AwardEdition edition =
                requireEdition(
                        registration.getEditionId()
                );

        Instant reviewedAt =
                clock.instant();

        if (approved) {
            entry.approveReview(
                    reviewerUserId,
                    reviewedAt
            );
        } else {
            entry.rejectReview(
                    reviewerUserId,
                    reviewedAt,
                    reason
            );
        }

        AwardRegistrationEntry saved =
                entries.saveAndFlush(
                        entry
                );

        if (approved) {
            mailOutbox.enqueue(
                    mailTemplates.awardCandidateApproved(
                            representative.getName(),
                            representative.getEmail(),
                            registration.getRegistrationNumber(),
                            registration.getAthleteName(),
                            entry.getContestCategory()
                                    .label(),
                            edition.getName()
                    )
            );
        } else {
            mailOutbox.enqueue(
                    mailTemplates.awardCandidateRejected(
                            representative.getName(),
                            representative.getEmail(),
                            registration.getRegistrationNumber(),
                            registration.getAthleteName(),
                            entry.getContestCategory()
                                    .label(),
                            edition.getName(),
                            saved.getReviewReason()
                    )
            );
        }

        return entryResponse(
                saved,
                reviewer
        );
    }

    private AwardEdition requireEdition(
            UUID editionId
    ) {
        return editions.findById(
                        editionId
                )
                .orElseThrow(() ->
                        Errors.notFound(
                                "AWARD_EDITION_NOT_FOUND",
                                "Edição do prêmio não encontrada."
                        )
                );
    }

    private AwardRegistration requireSubmittedRegistration(
            UUID registrationId
    ) {
        AwardRegistration registration =
                registrations.findById(
                                registrationId
                        )
                        .orElseThrow(() ->
                                Errors.notFound(
                                        "AWARD_REGISTRATION_NOT_FOUND",
                                        "Inscrição não encontrada."
                                )
                        );

        if (registration.getStatus()
                != AwardRegistrationStatus.SUBMITTED) {
            throw Errors.conflict(
                    "AWARD_REGISTRATION_NOT_SUBMITTED",
                    "Somente inscrições enviadas podem ser analisadas."
            );
        }

        return registration;
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
                                "Candidatura não encontrada."
                        )
                );
    }

    private UserAccount requireUser(
            UUID userId
    ) {
        return users.findById(
                        userId
                )
                .orElseThrow(() ->
                        Errors.notFound(
                                "USER_NOT_FOUND",
                                "Usuário não encontrado."
                        )
                );
    }

    private long count(
            UUID editionId,
            AwardRegistrationReviewStatus status
    ) {
        return entries.countForEditionAndReviewStatus(
                editionId,
                AwardRegistrationStatus.SUBMITTED,
                status
        );
    }

    private AwardAdminRegistrationResponse registrationResponse(
            AwardRegistration registration,
            List<AwardRegistrationEntry> registrationEntries,
            Map<UUID, UserAccount> accounts
    ) {
        UserAccount representative =
                accounts.get(
                        registration.getRepresentativeUserId()
                );

        return new AwardAdminRegistrationResponse(
                registration.getId(),
                registration.getRegistrationNumber(),
                registration.getStatus(),
                registration.getRepresentativeUserId(),
                representative != null
                        ? representative.getName()
                        : "Usuário indisponível",
                representative != null
                        ? representative.getEmail()
                        : "",
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
                registrationEntries.stream()
                        .map(entry ->
                                entryResponse(
                                        entry,
                                        accounts.get(
                                                entry.getReviewedByUserId()
                                        )
                                )
                        )
                        .toList()
        );
    }

    private AwardAdminRegistrationEntryResponse entryResponse(
            AwardRegistrationEntry entry,
            UserAccount reviewer
    ) {
        return new AwardAdminRegistrationEntryResponse(
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
                entry.getReviewedByUserId(),
                reviewer != null
                        ? reviewer.getName()
                        : null,
                entry.getReviewReason()
        );
    }
}

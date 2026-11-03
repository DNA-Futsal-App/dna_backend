package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AwardPopularVotingService {
    private final AwardEditionRepository editions;
    private final AwardPreVoteEditionLockRepository editionLocks;
    private final AwardPopularVotingWindowRepository windows;
    private final AwardPopularVotingFinalistRepository finalistQueries;
    private final AwardPopularVoteRepository votes;
    private final AwardRegistrationEntryRepository entries;
    private final AwardRegistrationRepository registrations;
    private final CurrentUserService currentUser;
    private final OracleAwardVideoStorage storage;
    private final Clock clock;

    public AwardPopularVotingService(AwardEditionRepository editions, AwardPreVoteEditionLockRepository editionLocks,
                                     AwardPopularVotingWindowRepository windows,
                                     AwardPopularVotingFinalistRepository finalistQueries, AwardPopularVoteRepository votes,
                                     AwardRegistrationEntryRepository entries, AwardRegistrationRepository registrations,
                                     CurrentUserService currentUser, OracleAwardVideoStorage storage, Clock clock) {
        this.editions = editions;
        this.editionLocks = editionLocks;
        this.windows = windows;
        this.finalistQueries = finalistQueries;
        this.votes = votes;
        this.entries = entries;
        this.registrations = registrations;
        this.currentUser = currentUser;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AwardPopularVotingWindowResponse> editions() {
        Map<UUID, AwardPopularVotingWindow> byEdition = windows.findAll().stream()
                .collect(Collectors.toMap(AwardPopularVotingWindow::getEditionId, Function.identity()));
        return editions.findAllByOrderBySeasonDescNameAsc().stream()
                .filter(edition -> byEdition.containsKey(edition.getId()))
                .map(edition -> windowResponse(edition, byEdition.get(edition.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AwardPopularContextResponse> contexts(UUID editionId) {
        requireVisible(window(editionId));
        return finalistQueries.contexts(editionId).stream().map(row -> {
            AwardRegistrationContestCategory contest = (AwardRegistrationContestCategory) row[6];
            return new AwardPopularContextResponse(((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(), (String) row[2], ((Number) row[3]).longValue(),
                    (String) row[4], (AwardRegistrationGender) row[5], contest, contest.label());
        }).sorted(Comparator.comparingLong(AwardPopularContextResponse::eventId)
                .thenComparingLong(AwardPopularContextResponse::divisionId)
                .thenComparingLong(AwardPopularContextResponse::categoryId)
                .thenComparing(AwardPopularContextResponse::gender)
                .thenComparing(AwardPopularContextResponse::contestCategory)).toList();
    }

    @Transactional(readOnly = true)
    public AwardPopularBoardResponse board(UUID editionId, AwardPopularContext context) {
        AwardPopularVotingWindow window = window(editionId);
        requireVisible(window);
        List<FinalistData> candidates = requireFinalists(editionId, context);
        String status = window.statusAt(clock.instant());
        // Ocultar parciais enquanto a votação está aberta.
        Map<UUID, Long> counts = "CLOSED".equals(status) ? tally(editionId, context) : Map.of();
        List<AwardPopularFinalistResponse> response = candidates.stream().map(candidate ->
                new AwardPopularFinalistResponse(candidate.entry().getId(),
                        candidate.registration().getAthleteName(), candidate.registration().getTeamName(),
                        candidate.entry().getSourceType(), candidate.entry().getSourceType() == AwardRegistrationMediaSource.LINK
                            ? candidate.entry().getExternalUrl() : null,
                        "CLOSED".equals(status) ? counts.getOrDefault(candidate.entry().getId(), 0L) : null)).toList();
        Long total = "CLOSED".equals(status) ? counts.values().stream().mapToLong(Long::longValue).sum() : null;
        return new AwardPopularBoardResponse(status, response, total);
    }

    @Transactional(readOnly = true)
    public AwardMediaTicketResponse mediaTicket(UUID editionId, UUID entryId) {
        requireVisible(window(editionId));
        AwardRegistrationEntry entry = entries.findById(entryId).orElseThrow(() ->
                Errors.notFound("AWARD_POPULAR_MEDIA_NOT_FOUND", "Vídeo não encontrado."));
        AwardRegistration registration = registrations.findById(entry.getRegistrationId()).orElseThrow(() ->
                Errors.notFound("AWARD_POPULAR_REGISTRATION_NOT_FOUND", "Inscrição não encontrada."));
        if (!editionId.equals(registration.getEditionId())) {
            throw Errors.notFound("AWARD_POPULAR_MEDIA_NOT_FOUND", "Vídeo não encontrado nesta edição.");
        }
        List<FinalistData> group = requireFinalists(editionId, contextOf(registration, entry));
        if (group.stream().noneMatch(f -> f.entry().getId().equals(entryId))) {
            throw Errors.notFound("AWARD_POPULAR_NOT_FINALIST", "O vídeo não pertence aos finalistas.");
        }
        if (entry.getSourceType() != AwardRegistrationMediaSource.UPLOAD
                || entry.getObjectName() == null || entry.getObjectName().isBlank()) {
            throw Errors.badRequest("AWARD_POPULAR_NOT_UPLOAD", "Use o link externo do vídeo.");
        }
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(10));
        OracleAwardVideoStorage.ReadTicket ticket = storage.createReadTicket(entry.getObjectName(),
                "award-popular-" + entryId + "-" + UUID.randomUUID(), expiresAt);
        return new AwardMediaTicketResponse(ticket.url(), ticket.expiresAt());
    }

    @Transactional(readOnly = true)
    public AwardPopularVoteResponse myVote(UUID editionId, AwardPopularContext context) {
        UUID userId = currentUser.userId();
        return votes.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategoryAndVoterUserId(
                editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender(), context.contestCategory(), userId)
                .map(v -> new AwardPopularVoteResponse(v.getEntryId(), "VOTED"))
                .orElseGet(() -> new AwardPopularVoteResponse(null, "NOT_VOTED"));
    }

    @Transactional
    public AwardPopularVoteResponse vote(UUID editionId, AwardPopularVoteRequest request) {
        // FOR SHARE permite votos paralelos; CLOSE usa FOR UPDATE na mesma linha.
        AwardPopularVotingWindow window = windows.lockForVoting(editionId).orElseThrow(() ->
                Errors.conflict("AWARD_POPULAR_NOT_SCHEDULED", "A votação popular ainda não foi programada."));
        if (!window.isOpen(clock.instant())) {
            throw Errors.conflict("AWARD_POPULAR_VOTING_CLOSED", "A votação popular não está aberta.");
        }
        AwardPopularContext context = request.context();
        List<FinalistData> group = requireFinalists(editionId, context);
        if (group.stream().noneMatch(f -> f.entry().getId().equals(request.entryId()))) {
            throw Errors.badRequest("AWARD_POPULAR_INVALID_FINALIST", "Escolha um dos três finalistas deste grupo.");
        }
        UUID voterId = currentUser.userId();
        int created = votes.insertIfAbsent(UUID.randomUUID(), editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender().name(),
                context.contestCategory().name(), request.entryId(), voterId);
        if (created == 0) {
            throw Errors.conflict("AWARD_POPULAR_ALREADY_VOTED", "Você já votou nesta categoria. O voto é definitivo.");
        }
        return new AwardPopularVoteResponse(request.entryId(), "VOTED");
    }

    @Transactional(readOnly = true)
    public AwardPopularVotingWindowResponse adminWindow(UUID editionId) {
        AwardEdition edition = edition(editionId);
        return windowResponse(edition, windows.findById(editionId).orElse(null));
    }

    @Transactional
    public AwardPopularVotingWindowResponse schedule(UUID editionId, AwardPopularWindowRequest request) {
        AwardEdition edition = editionLocks.lockEdition(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
        Instant now = clock.instant();
        if (edition.isRegistrationsOpen()) {
            throw Errors.conflict("AWARD_POPULAR_REGISTRATIONS_OPEN", "Encerre as inscrições antes de programar a votação popular.");
        }
        if (!request.closesAt().isAfter(request.opensAt()) || !request.closesAt().isAfter(now)) {
            throw Errors.badRequest("AWARD_POPULAR_INVALID_WINDOW", "Defina uma janela com encerramento futuro e após a abertura.");
        }
        if (!finalistQueries.existsByEditionId(editionId)) {
            throw Errors.conflict("AWARD_POPULAR_NO_FINALISTS", "Confirme os finalistas antes de abrir a votação popular.");
        }
        if (windows.existsById(editionId)) {
            throw Errors.conflict("AWARD_POPULAR_ALREADY_SCHEDULED", "A votação já foi configurada e não pode ser reaberta.");
        }
        AwardPopularVotingWindow saved = windows.saveAndFlush(
                new AwardPopularVotingWindow(editionId, request.opensAt(), request.closesAt()));
        return windowResponse(edition, saved);
    }

    @Transactional
    public AwardPopularVotingWindowResponse close(UUID editionId) {
        AwardEdition edition = edition(editionId);
        AwardPopularVotingWindow window = windows.lock(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_POPULAR_NOT_SCHEDULED", "Não existe votação popular configurada."));
        if (clock.instant().isBefore(window.getOpensAt())) {
            throw Errors.conflict("AWARD_POPULAR_NOT_STARTED", "A votação ainda não começou e não pode ser encerrada antecipadamente.");
        }
        window.close(clock.instant());
        return windowResponse(edition, windows.saveAndFlush(window));
    }

    private AwardPopularContext contextOf(AwardRegistration registration, AwardRegistrationEntry entry) {
        return new AwardPopularContext(registration.getEventId(), registration.getDivisionId(),
                registration.getCategoryId(), registration.getGender(), entry.getContestCategory());
    }

    private AwardEdition edition(UUID editionId) {
        return editions.findById(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
    }

    private AwardPopularVotingWindow window(UUID editionId) {
        return windows.findById(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_POPULAR_NOT_SCHEDULED", "A votação popular ainda não foi programada."));
    }

    private AwardPopularVotingWindowResponse windowResponse(AwardEdition edition, AwardPopularVotingWindow window) {
        return new AwardPopularVotingWindowResponse(edition.getId(), edition.getName(), edition.getSeason(),
                window == null ? "NOT_SCHEDULED" : window.statusAt(clock.instant()),
                window == null ? null : window.getOpensAt(), window == null ? null : window.getClosesAt(),
                window == null ? null : window.getManuallyClosedAt());
    }

    private void requireVisible(AwardPopularVotingWindow window) {
        if (clock.instant().isBefore(window.getOpensAt())) {
            throw Errors.conflict("AWARD_POPULAR_NOT_STARTED", "Os finalistas serão exibidos após a abertura da votação.");
        }
    }

    private List<FinalistData> requireFinalists(UUID editionId, AwardPopularContext context) {
        List<AwardPreVoteFinalist> published = finalistQueries.finalists(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory());
        if (published.size() != 3 || published.get(0).getRank() != 1 || published.get(1).getRank() != 2
                || published.get(2).getRank() != 3) {
            throw Errors.notFound("AWARD_POPULAR_FINALISTS_UNAVAILABLE", "Este grupo ainda não possui três finalistas confirmados.");
        }
        List<UUID> ids = published.stream().map(AwardPreVoteFinalist::getEntryId).toList();
        Map<UUID, AwardRegistrationEntry> entryMap = entries.findAllById(ids).stream()
                .collect(Collectors.toMap(AwardRegistrationEntry::getId, Function.identity()));
        Map<UUID, AwardRegistration> registrationMap = registrations.findAllById(
                entryMap.values().stream().map(AwardRegistrationEntry::getRegistrationId).distinct().toList())
                .stream().collect(Collectors.toMap(AwardRegistration::getId, Function.identity()));
        List<FinalistData> result = new ArrayList<>();
        for (AwardPreVoteFinalist finalist : published) {
            AwardRegistrationEntry entry = entryMap.get(finalist.getEntryId());
            AwardRegistration registration = entry == null ? null : registrationMap.get(entry.getRegistrationId());
            if (registration == null || !editionId.equals(registration.getEditionId())
                    || !context.equals(contextOf(registration, entry))
                    || registration.getStatus() != AwardRegistrationStatus.SUBMITTED
                    || entry.getReviewStatus() != AwardRegistrationReviewStatus.APPROVED
                    || entry.getMediaStatus() != AwardRegistrationMediaStatus.READY
                    || finalist.getApprovedAt() == null
                    || !finalist.getApprovedAt().equals(entry.getReviewedAt())) {
                throw Errors.conflict("AWARD_POPULAR_FINALIST_CHANGED", "A aprovação de um dos vídeos mudou depois da pré-votação. Solicite uma revalidação à organização.");
            }
            result.add(new FinalistData(entry, registration));
        }
        return result;
    }

    private Map<UUID, Long> tally(UUID editionId, AwardPopularContext context) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : votes.tally(editionId, context.eventId(), context.divisionId(),
                context.categoryId(), context.gender(), context.contestCategory())) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private record FinalistData(AwardRegistrationEntry entry, AwardRegistration registration) { }
}

package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.springframework.http.HttpStatus;
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
    private final AwardPopularContextWindowRepository windows;
    private final AwardPopularVotingFinalistRepository finalistQueries;
    private final AwardPreVoteQueryRepository preVoteQueries;
    private final AwardPopularReadinessService readiness;
    private final AwardPreVoteService preVote;
    private final AwardPopularVoteRepository votes;
    private final AwardRegistrationEntryRepository entries;
    private final AwardRegistrationRepository registrations;
    private final CurrentUserService currentUser;
    private final OracleAwardVideoStorage storage;
    private final Clock clock;

    public AwardPopularVotingService(AwardEditionRepository editions,
            AwardPreVoteEditionLockRepository editionLocks,
            AwardPopularContextWindowRepository windows,
            AwardPopularVotingFinalistRepository finalistQueries,
            AwardPreVoteQueryRepository preVoteQueries,
            AwardPopularReadinessService readiness,
            AwardPreVoteService preVote,
            AwardPopularVoteRepository votes,
            AwardRegistrationEntryRepository entries,
            AwardRegistrationRepository registrations,
            CurrentUserService currentUser, OracleAwardVideoStorage storage, Clock clock) {
        this.editions = editions;
        this.editionLocks = editionLocks;
        this.windows = windows;
        this.finalistQueries = finalistQueries;
        this.preVoteQueries = preVoteQueries;
        this.readiness = readiness;
        this.preVote = preVote;
        this.votes = votes;
        this.entries = entries;
        this.registrations = registrations;
        this.currentUser = currentUser;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AwardPopularVotingWindowResponse> editions() {
        Map<UUID, List<AwardPopularContextWindow>> windowsByEdition = windows.findAll().stream()
                .collect(Collectors.groupingBy(AwardPopularContextWindow::getEditionId));
        Instant now = clock.instant();
        return editions.findAllByOrderBySeasonDescNameAsc().stream()
                .filter(edition -> windowsByEdition.containsKey(edition.getId()))
                .map(edition -> {
                    var rows = windowsByEdition.get(edition.getId());
                    String status = rows.stream().anyMatch(w -> w.isOpen(now)) ? "OPEN"
                            : rows.stream().anyMatch(w -> "SCHEDULED".equals(w.statusAt(now)))
                            ? "SCHEDULED" : "CLOSED";
                    // Datas globais não existem mais. Cada grupo possui o seu próprio calendário.
                    return new AwardPopularVotingWindowResponse(edition.getId(), edition.getName(),
                            edition.getSeason(), status, null, null, null);
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<AwardPopularContextResponse> contexts(UUID editionId) {
        edition(editionId);
        Map<AwardPopularContext, AwardPopularContextWindow> scheduled = windows.findByEditionId(editionId)
                .stream().collect(Collectors.toMap(AwardPopularContextWindow::context, Function.identity()));
        return finalistQueries.contexts(editionId).stream()
                .map(row -> contextResponse(row, scheduled.get(contextFrom(row))))
                .filter(c -> c.status() != null && !"NOT_SCHEDULED".equals(c.status()))
                .sorted(contextOrder()).toList();
    }

    @Transactional(readOnly = true)
    public List<AwardPopularContextResponse> adminContexts(UUID editionId) {
        edition(editionId);
        Map<AwardPopularContext, AwardPopularContextWindow> scheduled = windows.findByEditionId(editionId)
                .stream().collect(Collectors.toMap(AwardPopularContextWindow::context, Function.identity()));
        return preVoteQueries.findContexts(editionId).stream()
                .map(row -> contextResponse(row, scheduled.get(contextFrom(row))))
                .sorted(contextOrder()).toList();
    }

    @Transactional(readOnly = true)
    public AwardPopularReadinessResponse readiness(UUID editionId, AwardPopularContext context) {
        return readiness.assess(editionId, context);
    }

    @Transactional(readOnly = true)
    public AwardPopularContextWindowResponse adminWindow(UUID editionId, AwardPopularContext context) {
        edition(editionId);
        return windowResponse(context, windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender(), context.contestCategory()).orElse(null));
    }

    @Transactional(readOnly = true)
    public AwardPopularBatchReadinessResponse batchReadiness(UUID editionId,
                                                              AwardPopularBatchSelection selection) {
        edition(editionId);
        return assessBatch(editionId, selection);
    }

    private AwardPopularBatchReadinessResponse assessBatch(UUID editionId,
                                                            AwardPopularBatchSelection selection) {
        List<AwardPopularBatchItemResponse> items = selection.contexts().stream().map(context -> {
            var existing = windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                    editionId, context.eventId(), context.divisionId(), context.categoryId(),
                    context.gender(), context.contestCategory());
            return new AwardPopularBatchItemResponse(context,
                    existing.map(window -> window.statusAt(clock.instant())).orElse("NOT_SCHEDULED"),
                    readiness.assess(editionId, context));
        }).toList();
        boolean allReady = !items.isEmpty() && items.stream()
                .allMatch(item -> "NOT_SCHEDULED".equals(item.status()) && item.readiness().ready());
        return new AwardPopularBatchReadinessResponse(selection, allReady, items);
    }

    /**
     * All-or-nothing scheduling. Locks the edition before rechecking every selected context;
     * no finalist or window is persisted when any category is incomplete.
     */
    @Transactional
    public List<AwardPopularContextWindowResponse> scheduleBatch(UUID editionId,
                                                                   AwardPopularBatchWindowRequest request) {
        editionLocks.lockEdition(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada."));
        if (request.opensAt() == null || request.closesAt() == null
                || !request.opensAt().isAfter(clock.instant())
                || !request.closesAt().isAfter(request.opensAt())) {
            throw Errors.badRequest("AWARD_POPULAR_INVALID_WINDOW",
                    "A abertura deve ser futura e o encerramento deve ser posterior.");
        }

        AwardPopularBatchReadinessResponse state = assessBatch(editionId, request.selection());
        if (!state.ready()) {
            // Preserve every missing administrator, video, and blocker for each selected modality.
            throw new BusinessException(HttpStatus.CONFLICT, "AWARD_POPULAR_BATCH_NOT_READY",
                    "Não é possível abrir todas as disputas selecionadas. Confira as pendências por modalidade.",
                    null, Map.of("items", state.items()));
        }

        // The preflight is complete. Keep both finalists and all windows in this transaction.
        List<AwardPopularContextWindowResponse> scheduled = new ArrayList<>();
        for (AwardPopularBatchItemResponse item : state.items()) {
            AwardPopularContext context = item.context();
            if (!item.readiness().finalistsConfirmed()) {
                preVote.finalizeContext(editionId, new AwardPreVoteContextRequest(context.eventId(),
                        context.divisionId(), context.categoryId(), context.gender(), context.contestCategory()));
            }
            requireFinalists(editionId, context);
            AwardPopularContextWindow saved = windows.saveAndFlush(
                    new AwardPopularContextWindow(editionId, context,
                            request.opensAt(), request.closesAt()));
            scheduled.add(windowResponse(context, saved));
        }
        return List.copyOf(scheduled);
    }

    @Transactional
    public AwardPopularContextWindowResponse schedule(UUID editionId, AwardPopularContextWindowRequest request) {
        // A mesma linha de edição sincroniza atribuição de notas, publicação do Top 3 e agendamento.
        AwardEdition edition = editionLocks.lockEdition(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada."));
        Instant now = clock.instant();
        if (request.opensAt() == null || request.closesAt() == null
                || !request.closesAt().isAfter(request.opensAt()) || !request.opensAt().isAfter(now)) {
            throw Errors.badRequest("AWARD_POPULAR_INVALID_WINDOW",
                    "A abertura deve ser futura e o encerramento deve ocorrer depois da abertura.");
        }
        AwardPopularContext context = request.context();
        if (windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender(), context.contestCategory()).isPresent()) {
            throw Errors.conflict("AWARD_POPULAR_ALREADY_SCHEDULED",
                    "Este grupo já possui janela configurada. Não é possível programar duas vezes.");
        }
        AwardPopularReadinessResponse state = readiness.assess(editionId, context);
        if (!state.ready()) {
            String names = state.missingAdmins().stream()
                    .map(AwardMissingAdminResponse::adminName).collect(Collectors.joining(", "));
            String detail = names.isBlank()
                    ? "Ainda existem pendências para agendar este grupo: " + String.join(", ", state.blockers())
                    : "Faltam notas dos administradores: " + names + ". Confira os vídeos pendentes.";
            throw new BusinessException(HttpStatus.CONFLICT, "AWARD_POPULAR_NOT_READY", detail,
                    null, Map.of("missingAdmins", state.missingAdmins(),
                            "blockers", state.blockers(), "approvedVideos", state.approvedVideos(),
                            "pendingReview", state.pendingReview()));
        }
        if (edition.isRegistrationsOpen()) {
            throw Errors.conflict("AWARD_POPULAR_REGISTRATIONS_OPEN", "Encerre as inscrições antes de agendar.");
        }
        // Consolida o Top 3 somente quando TODOS os administradores ativos avaliaram TODOS os vídeos.
        if (!state.finalistsConfirmed()) {
            preVote.finalizeContext(editionId, new AwardPreVoteContextRequest(context.eventId(),
                    context.divisionId(), context.categoryId(), context.gender(), context.contestCategory()));
        }
        requireFinalists(editionId, context);
        AwardPopularContextWindow saved = windows.saveAndFlush(
                new AwardPopularContextWindow(editionId, context, request.opensAt(), request.closesAt()));
        return windowResponse(context, saved);
    }

    @Transactional
    public AwardPopularContextWindowResponse close(UUID editionId, AwardPopularContext context) {
        AwardPopularContextWindow window = windows.lockForClose(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory())
                .orElseThrow(() -> Errors.notFound("AWARD_POPULAR_NOT_SCHEDULED",
                        "Não existe votação programada para este grupo."));
        if (clock.instant().isBefore(window.getOpensAt())) {
            throw Errors.conflict("AWARD_POPULAR_NOT_STARTED",
                    "Esta votação ainda não começou. Não é possível encerrá-la antecipadamente.");
        }
        window.close(clock.instant());
        return windowResponse(context, windows.saveAndFlush(window));
    }

    @Transactional(readOnly = true)
    public AwardPopularBoardResponse board(UUID editionId, AwardPopularContext context) {
        // A API publica nunca entrega apuracao, inclusive depois do encerramento.
        return buildBoard(editionId, context, false);
    }

    @Transactional(readOnly = true)
    public AwardPopularBoardResponse adminResults(UUID editionId, AwardPopularContext context) {
        // Somente o controller /api/v1/admin/** deve expor esta chamada.
        return buildBoard(editionId, context, true);
    }

    private AwardPopularBoardResponse buildBoard(UUID editionId, AwardPopularContext context,
                                                  boolean administrative) {
        AwardPopularContextWindow window = requireWindow(editionId, context);
        requireVisible(window);
        List<FinalistData> candidates = requireFinalists(editionId, context);
        String status = window.statusAt(clock.instant());
        boolean showResults = administrative && "CLOSED".equals(status);
        Map<UUID, Long> counts = showResults ? tally(editionId, context) : Map.of();
        List<AwardPopularFinalistResponse> response = candidates.stream().map(candidate ->
                new AwardPopularFinalistResponse(candidate.entry().getId(),
                        candidate.registration().getAthleteName(), candidate.registration().getTeamName(),
                        candidate.entry().getSourceType(),
                        candidate.entry().getSourceType() == AwardRegistrationMediaSource.LINK
                                ? candidate.entry().getExternalUrl() : null,
                        showResults ? counts.getOrDefault(candidate.entry().getId(), 0L) : null))
                .toList();
        Long total = showResults ? counts.values().stream().mapToLong(Long::longValue).sum() : null;
        return new AwardPopularBoardResponse(status, response, total);
    }

    @Transactional(readOnly = true)
    public AwardMediaTicketResponse mediaTicket(UUID editionId, UUID entryId) {
        AwardRegistrationEntry entry = entries.findById(entryId).orElseThrow(() ->
                Errors.notFound("AWARD_POPULAR_MEDIA_NOT_FOUND", "Vídeo não encontrado."));
        AwardRegistration registration = registrations.findById(entry.getRegistrationId()).orElseThrow(() ->
                Errors.notFound("AWARD_POPULAR_REGISTRATION_NOT_FOUND", "Inscrição não encontrada."));
        if (!editionId.equals(registration.getEditionId())) {
            throw Errors.notFound("AWARD_POPULAR_MEDIA_NOT_FOUND", "Vídeo não encontrado nesta edição.");
        }
        AwardPopularContext context = contextOf(registration, entry);
        requireVisible(requireWindow(editionId, context));
        List<FinalistData> group = requireFinalists(editionId, context);
        if (group.stream().noneMatch(f -> f.entry().getId().equals(entryId))) {
            throw Errors.notFound("AWARD_POPULAR_NOT_FINALIST", "Este vídeo não pertence ao Top 3.");
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
        AwardPopularContext context = request.context();
        AwardPopularContextWindow window = windows.lockForVote(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory())
                .orElseThrow(() -> Errors.conflict("AWARD_POPULAR_NOT_SCHEDULED",
                        "Esta disputa ainda não foi programada."));
        if (!window.isOpen(clock.instant())) {
            throw Errors.conflict("AWARD_POPULAR_VOTING_CLOSED", "Esta votação não está aberta.");
        }
        List<FinalistData> group = requireFinalists(editionId, context);
        if (group.stream().noneMatch(f -> f.entry().getId().equals(request.entryId()))) {
            throw Errors.badRequest("AWARD_POPULAR_INVALID_FINALIST", "Escolha um dos finalistas desta disputa.");
        }
        UUID voterId = currentUser.userId();
        int created = votes.insertIfAbsent(UUID.randomUUID(), editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender().name(),
                context.contestCategory().name(), request.entryId(), voterId);
        if (created == 0) {
            throw Errors.conflict("AWARD_POPULAR_ALREADY_VOTED",
                    "Você já votou nesta categoria. O voto é definitivo.");
        }
        return new AwardPopularVoteResponse(request.entryId(), "VOTED");
    }

    private AwardPopularContext contextFrom(Object[] row) {
        return new AwardPopularContext(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                ((Number) row[3]).longValue(), (AwardRegistrationGender) row[5],
                (AwardRegistrationContestCategory) row[6]);
    }
    private AwardPopularContextResponse contextResponse(Object[] row, AwardPopularContextWindow window) {
        AwardRegistrationContestCategory contest = (AwardRegistrationContestCategory) row[6];
        return new AwardPopularContextResponse(((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(), (String) row[2], ((Number) row[3]).longValue(),
                (String) row[4], (AwardRegistrationGender) row[5], contest, contest.label(),
                window == null ? "NOT_SCHEDULED" : window.statusAt(clock.instant()),
                window == null ? null : window.getOpensAt(), window == null ? null : window.getClosesAt());
    }
    private Comparator<AwardPopularContextResponse> contextOrder() {
        return Comparator.comparingLong(AwardPopularContextResponse::eventId)
                .thenComparingLong(AwardPopularContextResponse::divisionId)
                .thenComparingLong(AwardPopularContextResponse::categoryId)
                .thenComparing(AwardPopularContextResponse::gender)
                .thenComparing(AwardPopularContextResponse::contestCategory);
    }
    private AwardPopularContextWindowResponse windowResponse(AwardPopularContext context,
                                                               AwardPopularContextWindow window) {
        return new AwardPopularContextWindowResponse(context,
                window == null ? "NOT_SCHEDULED" : window.statusAt(clock.instant()),
                window == null ? null : window.getOpensAt(),
                window == null ? null : window.getClosesAt(),
                window == null ? null : window.getManuallyClosedAt());
    }
    private AwardPopularContextWindow requireWindow(UUID editionId, AwardPopularContext context) {
        return windows.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender(), context.contestCategory()).orElseThrow(() ->
                Errors.notFound("AWARD_POPULAR_NOT_SCHEDULED", "Este grupo ainda não possui votação programada."));
    }
    private void requireVisible(AwardPopularContextWindow window) {
        if (clock.instant().isBefore(window.getOpensAt())) {
            throw Errors.conflict("AWARD_POPULAR_NOT_STARTED",
                    "Os vídeos dos finalistas serão exibidos depois da abertura desta votação.");
        }
    }
    private AwardPopularContext contextOf(AwardRegistration r, AwardRegistrationEntry e) {
        return new AwardPopularContext(r.getEventId(), r.getDivisionId(), r.getCategoryId(),
                r.getGender(), e.getContestCategory());
    }
    private AwardEdition edition(UUID id) {
        return editions.findById(id).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada."));
    }
    private List<FinalistData> requireFinalists(UUID editionId, AwardPopularContext context) {
        List<AwardPreVoteFinalist> published = finalistQueries.finalists(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory());
        boolean fourAllowed = finalistQueries.allowsFourFinalists(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender().name(), context.contestCategory().name());
        int target = fourAllowed ? 4 : 3;
        if (published.size() != target) {
            throw Errors.notFound("AWARD_POPULAR_FINALISTS_UNAVAILABLE",
                    "Esta disputa ainda não possui o número correto de finalistas confirmados.");
        }
        for (int i = 0; i < published.size(); i++) {
            if (published.get(i).getRank() != i + 1) {
                throw Errors.conflict("AWARD_POPULAR_FINALIST_RANK_INVALID",
                        "A classificação publicada possui posições inconsistentes.");
            }
        }
        List<UUID> ids = published.stream().map(AwardPreVoteFinalist::getEntryId).toList();
        Map<UUID, AwardRegistrationEntry> entryMap = entries.findAllById(ids).stream()
                .collect(Collectors.toMap(AwardRegistrationEntry::getId, Function.identity()));
        Map<UUID, AwardRegistration> registrationsById = registrations.findAllById(
                entryMap.values().stream().map(AwardRegistrationEntry::getRegistrationId).distinct().toList())
                .stream().collect(Collectors.toMap(AwardRegistration::getId, Function.identity()));
        List<FinalistData> result = new ArrayList<>();
        for (AwardPreVoteFinalist finalist : published) {
            AwardRegistrationEntry entry = entryMap.get(finalist.getEntryId());
            AwardRegistration registration = entry == null ? null : registrationsById.get(entry.getRegistrationId());
            if (registration == null || !editionId.equals(registration.getEditionId())
                    || !context.equals(contextOf(registration, entry))
                    || registration.getStatus() != AwardRegistrationStatus.SUBMITTED
                    || entry.getReviewStatus() != AwardRegistrationReviewStatus.APPROVED
                    || entry.getMediaStatus() != AwardRegistrationMediaStatus.READY
                    || finalist.getApprovedAt() == null
                    || !finalist.getApprovedAt().equals(entry.getReviewedAt())) {
                throw Errors.conflict("AWARD_POPULAR_FINALIST_CHANGED",
                        "Um vídeo foi alterado depois da pré-votação. Solicite revalidação administrativa.");
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

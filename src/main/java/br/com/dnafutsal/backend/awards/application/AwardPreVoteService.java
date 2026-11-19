package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardPreVoteBoardResponse;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteContextRequest;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteContextResponse;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteEntryResponse;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardPreVoteFinalist;
import br.com.dnafutsal.backend.awards.domain.AwardPreVoteScore;
import br.com.dnafutsal.backend.awards.domain.AwardRegistration;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationEntry;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationMediaStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationReviewStatus;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteEditionLockRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteFinalistRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteTieDecisionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteQueryRepository;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteTieDecisionRequest;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteTieResponse;
import br.com.dnafutsal.backend.awards.api.AwardPopularContext;
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteScoreRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationEntryRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardRegistrationRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AwardPreVoteService {
    private final CurrentUserService currentUser;
    private final AwardEditionRepository editions;
    private final AwardPreVoteEditionLockRepository editionLocks;
    private final AwardRegistrationRepository registrations;
    private final AwardRegistrationEntryRepository entries;
    private final AwardPreVoteQueryRepository queries;
    private final AwardPreVoteScoreRepository scores;
    private final AwardPreVoteFinalistRepository finalists;
    private final AwardPreVoteTieDecisionRepository tieDecisions;
    private final AwardPopularReadinessService readiness;
    private final Clock clock;
    private final AwardRegistrationReopeningService reopenings;

    public AwardPreVoteService(CurrentUserService currentUser, AwardEditionRepository editions,
                               AwardPreVoteEditionLockRepository editionLocks,
                               AwardRegistrationRepository registrations,
                               AwardRegistrationEntryRepository entries,
                               AwardPreVoteQueryRepository queries,
                               AwardPreVoteScoreRepository scores,
                               AwardPreVoteFinalistRepository finalists,
                               AwardPreVoteTieDecisionRepository tieDecisions,
                               AwardPopularReadinessService readiness, Clock clock,
                               AwardRegistrationReopeningService reopenings) {
        this.currentUser = currentUser;
        this.editions = editions;
        this.editionLocks = editionLocks;
        this.registrations = registrations;
        this.entries = entries;
        this.queries = queries;
        this.scores = scores;
        this.finalists = finalists;
        this.tieDecisions = tieDecisions;
        this.readiness = readiness;
        this.clock = clock;
        this.reopenings = reopenings;
    }

    @Transactional(readOnly = true)
    public List<AwardPreVoteContextResponse> contexts(UUID editionId) {
        requireEdition(editionId);
        return queries.findContexts(editionId).stream()
                .map(row -> {
                    var category = (br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory) row[6];
                    return new AwardPreVoteContextResponse(
                            ((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                            (String) row[2], ((Number) row[3]).longValue(), (String) row[4],
                            (br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender) row[5],
                            category, category.label());
                }).toList();
    }

    @Transactional(readOnly = true)
    public AwardPreVoteBoardResponse board(UUID editionId, AwardPreVoteContextRequest context) {
        return buildBoard(requireEdition(editionId), context, currentUser.userId());
    }

    @Transactional(readOnly = true)
    public AwardPreVoteTieResponse tieReview(UUID editionId, AwardPreVoteContextRequest context) {
        AwardEdition edition = requireEdition(editionId);
        boolean published = !loadFinalists(editionId, context).isEmpty();
        List<RatedEntry> ranked = ranking(editionId, context);
        if (published || edition.isRegistrationsOpen() || reopenings.blocksFinalization(editionId, context)
                || countPending(editionId, context) > 0 || !completePanel(ranked)
                || !allAdministratorsScored(editionId, context)) {
            return new AwardPreVoteTieResponse(false, published, null, List.of(), List.of());
        }
        List<AwardTieDecisionPolicy.Candidate> scores = candidates(ranked);
        if (!AwardTieDecisionPolicy.tiedAtCutoff(scores)) {
            return new AwardPreVoteTieResponse(false, false, null, List.of(), List.of());
        }
        var decision = tieDecisions.find(editionId, context);
        Map<UUID, AwardRegistration> registrationsById = registrations.findAllById(
                ranked.stream().map(r -> r.entry().getRegistrationId()).distinct().toList())
                .stream().collect(Collectors.toMap(AwardRegistration::getId, Function.identity()));
        AwardTieDecisionPolicy.Candidate cutoff = scores.get(2);
        List<AwardPreVoteTieResponse.Candidate> result = ranked.stream().map(item -> {
            AwardRegistration registration = registrationsById.get(item.entry().getRegistrationId());
            if (registration == null) throw Errors.conflict("AWARD_PRE_VOTE_REGISTRATION_NOT_FOUND", "Inscrição inconsistente.");
            int compare = compareScore(item.sum(), item.count(), cutoff.scoreSum(), cutoff.scoreCount());
            return new AwardPreVoteTieResponse.Candidate(item.entry().getId(),
                    registration.getAthleteName(), registration.getTeamName(),
                    (double) item.sum() / item.count(), compare > 0, compare >= 0);
        }).toList();
        return new AwardPreVoteTieResponse(true, false,
                decision.map(AwardPreVoteTieDecisionRepository.Decision::finalistCount).orElse(null),
                decision.map(AwardPreVoteTieDecisionRepository.Decision::entries).orElse(List.of()), result);
    }

    @Transactional
    public AwardPreVoteTieResponse decideTie(UUID editionId, AwardPreVoteTieDecisionRequest request) {
        AwardPreVoteContextRequest context = request.context();
        AwardEdition edition = lockEdition(editionId);
        if (!loadFinalists(editionId, context).isEmpty()) {
            throw Errors.conflict("AWARD_PRE_VOTE_ALREADY_FINALIZED", "Nao e permitido alterar finalistas publicados.");
        }
        if (edition.isRegistrationsOpen() || reopenings.blocksFinalization(editionId, context)
                || countPending(editionId, context) > 0) {
            throw Errors.conflict("AWARD_PRE_VOTE_NOT_READY", "Encerre inscricoes e revisoes antes de resolver o empate.");
        }
        List<RatedEntry> ranked = ranking(editionId, context);
        if (!completePanel(ranked) || !allAdministratorsScored(editionId, context)) {
            throw Errors.conflict("AWARD_PRE_VOTE_INCOMPLETE", "Todos os administradores devem avaliar todos os videos antes do desempate.");
        }
        List<UUID> chosen = AwardTieDecisionPolicy.select(candidates(ranked),
                request.finalistCount(), request.selectedEntryIds()).orElseThrow(() ->
                Errors.conflict("AWARD_PRE_VOTE_TIE_SELECTION_INVALID",
                        "Escolha 3 ou 4 atletas: preserve os melhores e selecione apenas os empatados no corte."));
        tieDecisions.save(editionId, context, chosen, currentUser.userId());
        return tieReview(editionId, context);
    }

    @Transactional
    public AwardPreVoteBoardResponse score(UUID editionId, UUID entryId, int value) {
        if (value < 1 || value > 10) {
            throw Errors.badRequest("AWARD_PRE_VOTE_SCORE_INVALID", "A nota deve estar entre 1 e 10.");
        }
        // Bloqueio global sempre na ordem usuário -> edição, evitando inversões de locks.
        UUID adminId = currentUser.userId();
        AwardEdition edition = lockEdition(editionId);
        AwardRegistrationEntry entry = entries.findById(entryId).orElseThrow(() ->
                Errors.notFound("AWARD_PRE_VOTE_ENTRY_NOT_FOUND", "Vídeo não encontrado."));
        AwardRegistration registration = registrations.findById(entry.getRegistrationId()).orElseThrow(() ->
                Errors.notFound("AWARD_PRE_VOTE_REGISTRATION_NOT_FOUND", "Inscrição não encontrada."));
        if (!editionId.equals(registration.getEditionId())
                || registration.getStatus() != AwardRegistrationStatus.SUBMITTED
                || entry.getReviewStatus() != AwardRegistrationReviewStatus.APPROVED
                || entry.getMediaStatus() != AwardRegistrationMediaStatus.READY) {
            throw Errors.conflict("AWARD_PRE_VOTE_NOT_ELIGIBLE", "Somente vídeos aprovados e prontos podem receber notas.");
        }
        AwardPreVoteContextRequest context = contextOf(registration, entry);
        if (!loadFinalists(editionId, context).isEmpty()) {
            throw Errors.conflict("AWARD_PRE_VOTE_ALREADY_FINALIZED", "Este grupo de finalistas já foi publicado.");
        }
        AwardPreVoteScore saved = scores.findByEntryIdAndAdminUserIdAndApprovalAt(
                        entryId, adminId, entry.getReviewedAt())
                .orElseGet(() -> new AwardPreVoteScore(entryId, adminId, entry.getReviewedAt(), value));
        saved.changeScore(value);
        scores.saveAndFlush(saved);
        return buildBoard(edition, context, adminId);
    }

    @Transactional
    public AwardPreVoteBoardResponse finalizeContext(UUID editionId, AwardPreVoteContextRequest context) {
        UUID administrator = currentUser.userId();
        AwardEdition edition = lockEdition(editionId);
        if (!loadFinalists(editionId, context).isEmpty()) {
            throw Errors.conflict("AWARD_PRE_VOTE_ALREADY_FINALIZED", "Este grupo de finalistas já foi publicado.");
        }
        if (edition.isRegistrationsOpen() || reopenings.blocksFinalization(editionId, context)) {
            throw Errors.conflict("AWARD_PRE_VOTE_REGISTRATIONS_OPEN", "Encerre as inscrições desta modalidade antes de publicar os finalistas.");
        }
        long pending = countPending(editionId, context);
        if (pending > 0) {
            throw Errors.conflict("AWARD_PRE_VOTE_PENDING_REVIEW", "Existem vídeos desta categoria aguardando aprovação ou reprovação.");
        }
        List<RatedEntry> ranking = ranking(editionId, context);
        if (ranking.size() < 3) {
            throw Errors.conflict("AWARD_PRE_VOTE_NOT_ENOUGH_ENTRIES", "São necessários pelo menos três vídeos aprovados.");
        }
        if (!completePanel(ranking)) {
            throw Errors.conflict("AWARD_PRE_VOTE_INCOMPLETE", "Todos os vídeos precisam receber notas do mesmo conjunto de administradores.");
        }
        List<RatedEntry> chosen;
        var tieDecision = tieDecisions.find(editionId, context);
        if (tieDecision.isPresent()) {
            var decision = tieDecision.get();
            List<UUID> chosenIds = AwardTieDecisionPolicy.select(candidates(ranking),
                    decision.finalistCount(), decision.entries()).orElseThrow(() ->
                    Errors.conflict("AWARD_PRE_VOTE_TIE_SELECTION_INVALID",
                            "As notas mudaram depois da decisao. Reavalie o desempate."));
            Map<UUID, RatedEntry> byId = ranking.stream().collect(Collectors.toMap(
                    r -> r.entry().getId(), Function.identity()));
            chosen = chosenIds.stream().map(byId::get).toList();
        } else if (allowsFour(editionId, context)) {
            chosen = exceptionalSelection(editionId, ranking).orElseThrow(() ->
                    Errors.conflict("AWARD_PRE_VOTE_MANUAL_TIE_INVALID",
                            "A decisao administrativa historica nao confere com as notas atuais."));
        } else {
            if (ranking.size() > 3 && compareAverages(ranking.get(2), ranking.get(3)) == 0) {
                throw Errors.conflict("AWARD_PRE_VOTE_TIE_AT_CUTOFF",
                        "Ha empate na terceira colocacao. Resolva o empate antes de publicar.");
            }
            chosen = ranking.subList(0, 3);
        }
        for (int i = 0; i < chosen.size(); i++) {
            RatedEntry rated = chosen.get(i);
            AwardPreVoteFinalist finalist = new AwardPreVoteFinalist(
                    editionId, context.eventId(), context.divisionId(), context.categoryId(),
                    context.gender(), context.contestCategory(), rated.entry().getId(), i + 1,
                    BigDecimal.valueOf(rated.sum()).divide(BigDecimal.valueOf(rated.count()), 4, RoundingMode.HALF_UP),
                    rated.count(), clock.instant(), administrator);
            finalist.setApprovedAt(rated.entry().getReviewedAt());
            finalists.save(finalist);
        }
        finalists.flush();
        return buildBoard(edition, context, administrator);
    }

    private AwardPreVoteBoardResponse buildBoard(AwardEdition edition, AwardPreVoteContextRequest context, UUID adminId) {
        List<RatedEntry> ranking = ranking(edition.getId(), context);
        List<AwardPreVoteFinalist> published = loadFinalists(edition.getId(), context);
        Map<UUID, Integer> finalRanks = published.stream()
                .collect(Collectors.toMap(AwardPreVoteFinalist::getEntryId, v -> (int) v.getRank()));
        Map<UUID, AwardRegistration> registrationMap = registrations.findAllById(
                ranking.stream().map(item -> item.entry().getRegistrationId()).distinct().toList())
                .stream().collect(Collectors.toMap(AwardRegistration::getId, Function.identity()));
        // Avaliação cega: antes da publicação não expomos as médias nem a posição parcial.
        List<RatedEntry> displayOrder = published.isEmpty()
                ? ranking.stream().sorted(Comparator.comparingLong(a -> registrationMap.get(a.entry().getRegistrationId()).getRegistrationNumber())).toList()
                : ranking;
        List<AwardPreVoteEntryResponse> mapped = displayOrder.stream().map(item -> {
            AwardRegistration registration = registrationMap.get(item.entry().getRegistrationId());
            if (registration == null) throw Errors.conflict("AWARD_PRE_VOTE_MISSING_REGISTRATION", "Inscrição inconsistente.");
            Integer mine = item.scores().stream().filter(s -> s.getAdminUserId().equals(adminId))
                    .map(AwardPreVoteScore::getScore).findFirst().orElse(null);
            return new AwardPreVoteEntryResponse(item.entry().getId(), registration.getId(),
                    registration.getRegistrationNumber(), registration.getAthleteName(),
                    registration.getAthleteInstagram(), registration.getTeamName(),
                    item.entry().getSourceType(), item.entry().getExternalUrl(),
                    item.count(), published.isEmpty() || item.count() == 0 ? null : (double) item.sum() / item.count(),
                    mine, finalRanks.get(item.entry().getId()));
        }).toList();
        long pending = countPending(edition.getId(), context);
        int fullyScored = (int) ranking.stream().filter(r -> r.count() > 0).count();
        int panel = ranking.isEmpty() ? 0 : ranking.get(0).count();
        boolean fourAllowed = allowsFour(edition.getId(), context);
        var tieDecision = tieDecisions.find(edition.getId(), context);
        boolean cutoffValid = tieDecision.map(decision -> AwardTieDecisionPolicy.select(candidates(ranking),
                decision.finalistCount(), decision.entries()).isPresent()).orElseGet(() -> fourAllowed ? exceptionalSelection(edition.getId(), ranking).isPresent()
                : ranking.size() >= 3 && (ranking.size() == 3
                || compareAverages(ranking.get(2), ranking.get(3)) != 0));
        boolean ready = published.isEmpty() && !edition.isRegistrationsOpen() && pending == 0
                && ranking.size() >= (fourAllowed ? 4 : 3) && completePanel(ranking) && cutoffValid;
        return new AwardPreVoteBoardResponse(mapped, pending, ranking.size(), fullyScored,
                panel, edition.isRegistrationsOpen(), !published.isEmpty(), ready);
    }

    private List<RatedEntry> ranking(UUID editionId, AwardPreVoteContextRequest context) {
        List<AwardRegistrationEntry> approved = queries.findApproved(
                editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender(), context.contestCategory());
        if (approved.isEmpty()) return List.of();
        Map<UUID, List<AwardPreVoteScore>> byEntry = scores.findByEntryIdIn(
                approved.stream().map(AwardRegistrationEntry::getId).toList()).stream()
                .collect(Collectors.groupingBy(AwardPreVoteScore::getEntryId));
        List<RatedEntry> result = new ArrayList<>();
        for (AwardRegistrationEntry entry : approved) {
            // Uma substituição de mídia exige nova aprovação e invalida as notas anteriores.
            List<AwardPreVoteScore> grades = byEntry.getOrDefault(entry.getId(), List.of())
                    .stream().filter(score -> score.getApprovalAt().equals(entry.getReviewedAt())).toList();
            long sum = grades.stream().mapToLong(AwardPreVoteScore::getScore).sum();
            result.add(new RatedEntry(entry, grades, sum));
        }
        result.sort((a, b) -> {
            if (a.count() == 0 || b.count() == 0) {
                if (a.count() == 0 && b.count() == 0) return a.entry().getId().compareTo(b.entry().getId());
                return a.count() == 0 ? 1 : -1;
            }
            int comparison = compareAverages(a, b);
            return comparison == 0 ? a.entry().getId().compareTo(b.entry().getId()) : -comparison;
        });
        return result;
    }

    private static boolean completePanel(List<RatedEntry> ranking) {
        if (ranking.isEmpty()) return false;
        Set<UUID> expected = reviewers(ranking.get(0));
        if (expected.isEmpty()) return false;
        return ranking.stream().allMatch(r -> reviewers(r).equals(expected));
    }

    private static Set<UUID> reviewers(RatedEntry rated) {
        return rated.scores().stream().map(AwardPreVoteScore::getAdminUserId).collect(Collectors.toSet());
    }

    private static int compareAverages(RatedEntry a, RatedEntry b) {
        return compareScore(a.sum(), a.count(), b.sum(), b.count());
    }

    private static int compareScore(long sumA, int countA, long sumB, int countB) {
        return Long.compare(sumA * countB, sumB * countA);
    }

    private boolean allAdministratorsScored(UUID editionId, AwardPreVoteContextRequest context) {
        var result = readiness.assess(editionId, new AwardPopularContext(context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory()));
        return result.activeAdministrators() > 0 && result.missingAdmins().isEmpty();
    }

    private static List<AwardTieDecisionPolicy.Candidate> candidates(List<RatedEntry> ranked) {
        return ranked.stream().map(r -> new AwardTieDecisionPolicy.Candidate(
                r.entry().getId(), r.sum(), r.count())).toList();
    }

    private boolean allowsFour(UUID editionId, AwardPreVoteContextRequest context) {
        return finalists.allowsFourFinalists(editionId, context.eventId(), context.divisionId(),
                context.categoryId(), context.gender().name(), context.contestCategory().name());
    }

    private java.util.Optional<List<RatedEntry>> exceptionalSelection(UUID editionId, List<RatedEntry> ranking) {
        if (ranking.size() < 5 || compareAverages(ranking.get(2), ranking.get(3)) != 0
                || compareAverages(ranking.get(3), ranking.get(4)) != 0
                || (ranking.size() > 5 && compareAverages(ranking.get(4), ranking.get(5)) == 0)) {
            return java.util.Optional.empty();
        }
        List<UUID> ids = finalists.selectedFourFinalists(editionId);
        if (ids.size() != 4 || !ids.get(0).equals(ranking.get(0).entry().getId())
                || !ids.get(1).equals(ranking.get(1).entry().getId())
                || ids.get(2).equals(ids.get(3))) {
            return java.util.Optional.empty();
        }
        Set<UUID> tied = Set.of(ranking.get(2).entry().getId(),
                ranking.get(3).entry().getId(), ranking.get(4).entry().getId());
        if (!tied.contains(ids.get(2)) || !tied.contains(ids.get(3))) {
            return java.util.Optional.empty();
        }
        Map<UUID, RatedEntry> candidates = ranking.stream()
                .collect(Collectors.toMap(v -> v.entry().getId(), Function.identity()));
        return java.util.Optional.of(ids.stream().map(candidates::get).toList());
    }

    private List<AwardPreVoteFinalist> loadFinalists(UUID editionId, AwardPreVoteContextRequest context) {
        return finalists.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategoryOrderByRankAsc(
                editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender(), context.contestCategory());
    }

    private long countPending(UUID editionId, AwardPreVoteContextRequest context) {
        return queries.countPending(editionId, context.eventId(), context.divisionId(), context.categoryId(),
                context.gender(), context.contestCategory());
    }

    private AwardPreVoteContextRequest contextOf(AwardRegistration r, AwardRegistrationEntry e) {
        return new AwardPreVoteContextRequest(r.getEventId(), r.getDivisionId(), r.getCategoryId(),
                r.getGender(), e.getContestCategory());
    }

    private AwardEdition requireEdition(UUID editionId) {
        return editions.findById(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
    }

    private AwardEdition lockEdition(UUID editionId) {
        return editionLocks.lockEdition(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
    }

    private record RatedEntry(AwardRegistrationEntry entry, List<AwardPreVoteScore> scores, long sum) {
        int count() { return scores.size(); }
    }
}

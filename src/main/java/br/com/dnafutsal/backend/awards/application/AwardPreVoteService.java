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
import br.com.dnafutsal.backend.awards.infrastructure.AwardPreVoteQueryRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final Clock clock;

    public AwardPreVoteService(CurrentUserService currentUser, AwardEditionRepository editions,
                               AwardPreVoteEditionLockRepository editionLocks,
                               AwardRegistrationRepository registrations,
                               AwardRegistrationEntryRepository entries,
                               AwardPreVoteQueryRepository queries,
                               AwardPreVoteScoreRepository scores,
                               AwardPreVoteFinalistRepository finalists, Clock clock) {
        this.currentUser = currentUser;
        this.editions = editions;
        this.editionLocks = editionLocks;
        this.registrations = registrations;
        this.entries = entries;
        this.queries = queries;
        this.scores = scores;
        this.finalists = finalists;
        this.clock = clock;
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

    @Transactional
    public AwardPreVoteBoardResponse score(UUID editionId, UUID entryId, int value) {
        if (value < 1 || value > 10) {
            throw Errors.badRequest("AWARD_PRE_VOTE_SCORE_INVALID", "A nota deve estar entre 1 e 10.");
        }
        // Obrigatório usar a mesma trava no salvamento e na publicação.
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
        UUID adminId = currentUser.userId();
        AwardPreVoteScore saved = scores.findByEntryIdAndAdminUserIdAndApprovalAt(
                        entryId, adminId, entry.getReviewedAt())
                .orElseGet(() -> new AwardPreVoteScore(entryId, adminId, entry.getReviewedAt(), value));
        saved.changeScore(value);
        scores.saveAndFlush(saved);
        return buildBoard(edition, context, adminId);
    }

    @Transactional
    public AwardPreVoteBoardResponse finalizeContext(UUID editionId, AwardPreVoteContextRequest context) {
        AwardEdition edition = lockEdition(editionId);
        if (!loadFinalists(editionId, context).isEmpty()) {
            throw Errors.conflict("AWARD_PRE_VOTE_ALREADY_FINALIZED", "Este grupo de finalistas já foi publicado.");
        }
        if (edition.isRegistrationsOpen()) {
            throw Errors.conflict("AWARD_PRE_VOTE_REGISTRATIONS_OPEN", "Encerre as inscrições antes de publicar os finalistas.");
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
        if (ranking.size() > 3 && compareAverages(ranking.get(2), ranking.get(3)) == 0) {
            throw Errors.conflict("AWARD_PRE_VOTE_TIE_AT_CUTOFF", "Há empate na terceira colocação. Resolva o empate antes de publicar.");
        }
        UUID administrator = currentUser.userId();
        for (int i = 0; i < 3; i++) {
            RatedEntry rated = ranking.get(i);
            finalists.save(new AwardPreVoteFinalist(
                    editionId, context.eventId(), context.divisionId(), context.categoryId(),
                    context.gender(), context.contestCategory(), rated.entry().getId(), i + 1,
                    BigDecimal.valueOf(rated.sum()).divide(BigDecimal.valueOf(rated.count()), 4, RoundingMode.HALF_UP),
                    rated.count(), clock.instant(), administrator));
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
                ? ranking.stream().sorted((a, b) -> Long.compare(
                        registrationMap.get(a.entry().getRegistrationId()).getRegistrationNumber(),
                        registrationMap.get(b.entry().getRegistrationId()).getRegistrationNumber())).toList()
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
        boolean ready = published.isEmpty() && !edition.isRegistrationsOpen() && pending == 0
                && ranking.size() >= 3 && completePanel(ranking)
                && (ranking.size() == 3 || compareAverages(ranking.get(2), ranking.get(3)) != 0);
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
        return Long.compare(a.sum() * b.count(), b.sum() * a.count());
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

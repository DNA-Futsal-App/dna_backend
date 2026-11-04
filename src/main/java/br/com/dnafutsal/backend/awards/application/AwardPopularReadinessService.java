package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.domain.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AwardPopularReadinessService {
    private final AwardEditionRepository editions;
    private final AwardPreVoteQueryRepository queries;
    private final AwardPreVoteScoreRepository scores;
    private final AwardRegistrationRepository registrations;
    private final AwardPopularVotingFinalistRepository finalists;
    private final AwardActiveAdministratorRepository users;

    public AwardPopularReadinessService(AwardEditionRepository editions, AwardPreVoteQueryRepository queries,
            AwardPreVoteScoreRepository scores, AwardRegistrationRepository registrations,
            AwardPopularVotingFinalistRepository finalists, AwardActiveAdministratorRepository users) {
        this.editions = editions;
        this.queries = queries;
        this.scores = scores;
        this.registrations = registrations;
        this.finalists = finalists;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AwardPopularReadinessResponse assess(UUID editionId, AwardPopularContext context) {
        AwardEdition edition = editions.findById(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada."));
        List<UserAccount> activeAdmins = users.findByRoleAndStatusOrderByNameAsc(UserRole.ADMIN, UserStatus.ACTIVE);
        List<AwardRegistrationEntry> approved = queries.findApproved(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory());
        long pending = queries.countPending(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory());
        Map<UUID, AwardRegistration> byRegistration = registrations.findAllById(
                approved.stream().map(AwardRegistrationEntry::getRegistrationId).distinct().toList())
                .stream().collect(Collectors.toMap(AwardRegistration::getId, r -> r));
        Map<UUID, Set<UUID>> voters = new HashMap<>();
        Map<UUID, Long> sums = new HashMap<>();
        if (!approved.isEmpty()) {
            Map<UUID, AwardRegistrationEntry> byEntry = approved.stream()
                    .collect(Collectors.toMap(AwardRegistrationEntry::getId, e -> e));
            Set<UUID> eligibleAdmins = activeAdmins.stream().map(UserAccount::getId).collect(Collectors.toSet());
            for (AwardPreVoteScore score : scores.findByEntryIdIn(byEntry.keySet())) {
                AwardRegistrationEntry entry = byEntry.get(score.getEntryId());
                if (entry != null && entry.getReviewedAt() != null
                        && entry.getReviewedAt().equals(score.getApprovalAt())
                        && eligibleAdmins.contains(score.getAdminUserId())) {
                    voters.computeIfAbsent(entry.getId(), key -> new HashSet<>()).add(score.getAdminUserId());
                    sums.merge(entry.getId(), (long) score.getScore(), Long::sum);
                }
            }
        }
        List<AwardMissingAdminResponse> missing = new ArrayList<>();
        for (UserAccount admin : activeAdmins) {
            List<AwardMissingVideoResponse> unscored = new ArrayList<>();
            for (AwardRegistrationEntry entry : approved) {
                if (!voters.getOrDefault(entry.getId(), Set.of()).contains(admin.getId())) {
                    AwardRegistration registration = byRegistration.get(entry.getRegistrationId());
                    unscored.add(new AwardMissingVideoResponse(entry.getId(),
                            registration == null ? "Inscrição indisponível" : registration.getAthleteName(),
                            registration == null ? 0 : registration.getRegistrationNumber()));
                }
            }
            if (!unscored.isEmpty()) missing.add(new AwardMissingAdminResponse(
                    admin.getId(), admin.getName(), admin.getEmail(), unscored));
        }
        boolean fourAllowed = finalists.allowsFourFinalists(editionId, context.eventId(), context.divisionId(),
                context.categoryId(), context.gender().name(), context.contestCategory().name());
        int target = fourAllowed ? 4 : 3;
        List<AwardPreVoteFinalist> published = finalists.finalists(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory());
        boolean finalistsConfirmed = published.size() == target;
        for (int i = 0; i < published.size(); i++) {
            if (published.get(i).getRank() != i + 1) finalistsConfirmed = false;
        }
        List<String> blockers = new ArrayList<>();
        if (edition.isRegistrationsOpen()) blockers.add("INSCRICOES_ABERTAS");
        if (pending > 0) blockers.add("REVISOES_PENDENTES");
        if (approved.size() < target) blockers.add(fourAllowed
                ? "MENOS_DE_QUATRO_VIDEOS_APROVADOS" : "MENOS_DE_TRES_VIDEOS_APROVADOS");
        if (activeAdmins.isEmpty()) blockers.add("NENHUM_ADMIN_ATIVO");
        if (!missing.isEmpty()) blockers.add("NOTAS_PENDENTES");
        if (missing.isEmpty() && !approved.isEmpty() && activeAdmins.size() > 0 && approved.size() > 3) {
            List<AwardRegistrationEntry> ranking = new ArrayList<>(approved);
            ranking.sort((a, b) -> {
                long sa = sums.getOrDefault(a.getId(), 0L);
                long sb = sums.getOrDefault(b.getId(), 0L);
                int c = Long.compare(sb, sa); // Mesmo número de jurados em todos os vídeos.
                return c == 0 ? a.getId().compareTo(b.getId()) : c;
            });
            boolean tiedAtThird = sums.getOrDefault(ranking.get(2).getId(), 0L).equals(
                    sums.getOrDefault(ranking.get(3).getId(), 0L));
            if (tiedAtThird && !fourAllowed) blockers.add("EMPATE_NO_CORTE_TOP3");
            if (fourAllowed && !finalistsConfirmed) {
                List<UUID> chosen = finalists.selectedFourFinalists(editionId);
                boolean exactlyTripleTie = ranking.size() >= 5 && tiedAtThird
                        && sums.getOrDefault(ranking.get(3).getId(), 0L).equals(
                           sums.getOrDefault(ranking.get(4).getId(), 0L))
                        && (ranking.size() == 5 || !sums.getOrDefault(ranking.get(4).getId(), 0L).equals(
                           sums.getOrDefault(ranking.get(5).getId(), 0L)));
                boolean valid = exactlyTripleTie && chosen.size() == 4
                        && chosen.get(0).equals(ranking.get(0).getId())
                        && chosen.get(1).equals(ranking.get(1).getId())
                        && !chosen.get(2).equals(chosen.get(3));
                if (valid) {
                    Set<UUID> tied = Set.of(ranking.get(2).getId(),
                            ranking.get(3).getId(), ranking.get(4).getId());
                    valid = tied.contains(chosen.get(2)) && tied.contains(chosen.get(3));
                }
                if (!valid) blockers.add("DESEMPATE_ADMINISTRATIVO_INVALIDO");
            }
        }
        return new AwardPopularReadinessResponse(context, approved.size(), pending, activeAdmins.size(),
                finalistsConfirmed, edition.isRegistrationsOpen(), blockers.isEmpty(),
                List.copyOf(blockers), List.copyOf(missing));
    }
}

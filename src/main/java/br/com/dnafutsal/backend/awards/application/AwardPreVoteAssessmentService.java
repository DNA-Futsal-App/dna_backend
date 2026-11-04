package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardPreVoteAssessmentEntryResponse;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteAssessmentReportResponse;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteContextRequest;
import br.com.dnafutsal.backend.awards.api.AwardPreVoteContextResponse;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserRole;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Dados administrativos, inclusive pessoais. Nunca disponibilizar em /api/v1/public. */
@Service
public class AwardPreVoteAssessmentService {
    private final AwardEditionRepository editions;
    private final AwardPreVoteQueryRepository queries;
    private final AwardPreVoteScoreRepository scores;
    private final AwardActiveAdministratorRepository administrators;
    private final UserAccountRepository users;

    public AwardPreVoteAssessmentService(AwardEditionRepository editions,
                                         AwardPreVoteQueryRepository queries,
                                         AwardPreVoteScoreRepository scores,
                                         AwardActiveAdministratorRepository administrators,
                                         UserAccountRepository users) {
        this.editions = editions;
        this.queries = queries;
        this.scores = scores;
        this.administrators = administrators;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AwardPreVoteContextResponse> contexts(UUID editionId) {
        requireEdition(editionId);
        return queries.findAssessmentContexts(editionId).stream()
                .map(row -> {
                    AwardRegistrationContestCategory category = (AwardRegistrationContestCategory) row[6];
                    return new AwardPreVoteContextResponse(
                            ((Number) row[0]).longValue(), ((Number) row[1]).longValue(), (String) row[2],
                            ((Number) row[3]).longValue(), (String) row[4],
                            (AwardRegistrationGender) row[5], category, category.label());
                }).toList();
    }

    @Transactional(readOnly = true)
    public AwardPreVoteAssessmentReportResponse report(UUID editionId, AwardPreVoteContextRequest context) {
        requireEdition(editionId);
        // Todas as inscrições ENVIADAS para a modalidade; pendências permanecem visíveis.
        List<Object[]> rows = queries.findAssessmentEntries(editionId, context.eventId(),
                context.divisionId(), context.categoryId(), context.gender(), context.contestCategory());
        Set<UUID> activeAdminIds = administrators.findByRoleAndStatusOrderByNameAsc(
                UserRole.ADMIN, UserStatus.ACTIVE).stream().map(UserAccount::getId)
                .collect(Collectors.toSet());
        if (rows.isEmpty()) {
            return new AwardPreVoteAssessmentReportResponse(context, activeAdminIds.size(), 0, List.of());
        }

        List<AwardRegistration> registrations = rows.stream()
                .map(row -> (AwardRegistration) row[0]).toList();
        List<AwardRegistrationEntry> entries = rows.stream()
                .map(row -> (AwardRegistrationEntry) row[1]).toList();

        Set<UUID> representativeIds = registrations.stream()
                .map(AwardRegistration::getRepresentativeUserId).collect(Collectors.toSet());
        Map<UUID, UserAccount> representatives = users.findAllById(representativeIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, user -> user));
        Map<UUID, List<AwardPreVoteScore>> gradesByEntry = scores.findByEntryIdIn(
                entries.stream().map(AwardRegistrationEntry::getId).toList()).stream()
                .collect(Collectors.groupingBy(AwardPreVoteScore::getEntryId));

        List<AwardPreVoteAssessmentEntryResponse> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            AwardRegistration r = registrations.get(i);
            AwardRegistrationEntry e = entries.get(i);
            boolean eligible = e.getReviewStatus() == AwardRegistrationReviewStatus.APPROVED
                    && e.getMediaStatus() == AwardRegistrationMediaStatus.READY
                    && e.getReviewedAt() != null;

            // Notas antigas após substituição/reprovação não entram no relatório.
            List<AwardPreVoteScore> valid = eligible
                    ? gradesByEntry.getOrDefault(e.getId(), List.of()).stream()
                        .filter(score -> e.getReviewedAt().equals(score.getApprovalAt()))
                        .filter(score -> activeAdminIds.contains(score.getAdminUserId()))
                        .toList()
                    : List.of();
            Set<UUID> gradedAdminIds = valid.stream().map(AwardPreVoteScore::getAdminUserId)
                    .collect(Collectors.toSet());
            BigDecimal average = valid.isEmpty() ? null
                    : BigDecimal.valueOf(valid.stream().mapToLong(AwardPreVoteScore::getScore).sum())
                        .divide(BigDecimal.valueOf(valid.size()), 4, RoundingMode.HALF_UP);
            UserAccount representative = representatives.get(r.getRepresentativeUserId());
            result.add(new AwardPreVoteAssessmentEntryResponse(
                    r.getId(), e.getId(), r.getRegistrationNumber(),
                    representative == null ? "Responsável indisponível" : representative.getName(),
                    representative == null ? null : representative.getEmail(),
                    r.getAthleteName(), r.getAthleteInstagram(), r.getTeamName(),
                    e.getReviewStatus(), e.getMediaStatus(), e.getSourceType(), e.getExternalUrl(),
                    average, gradedAdminIds.size(), activeAdminIds.size(),
                    !activeAdminIds.isEmpty() && gradedAdminIds.containsAll(activeAdminIds)));
        }
        // Média parcial também é ordenável; a interface sinaliza explicitamente quando incompleta.
        result.sort(Comparator.comparing(AwardPreVoteAssessmentEntryResponse::averageScore,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingLong(AwardPreVoteAssessmentEntryResponse::registrationNumber)
                .thenComparing(AwardPreVoteAssessmentEntryResponse::entryId));
        return new AwardPreVoteAssessmentReportResponse(context, activeAdminIds.size(), result.size(),
                List.copyOf(result));
    }

    private void requireEdition(UUID editionId) {
        if (!editions.existsById(editionId)) {
            throw Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada.");
        }
    }
}

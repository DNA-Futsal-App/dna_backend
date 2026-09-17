
package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardAdminAuditResponse;
import br.com.dnafutsal.backend.awards.api.AwardAdminResultsResponse;
import br.com.dnafutsal.backend.awards.api.AwardAuditIssueResponse;
import br.com.dnafutsal.backend.awards.api.AwardCandidateResultResponse;
import br.com.dnafutsal.backend.awards.api.AwardCategoryResultResponse;
import br.com.dnafutsal.backend.awards.api.AwardContextResultResponse;
import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardBallotVote;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotVoteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AwardResultsService {

    private final AwardEditionRepository editions;
    private final AwardVoteCategoryRepository voteCategories;
    private final AwardCandidateRepository candidates;
    private final AwardCoachVoterRepository voters;
    private final AwardBallotRepository ballots;
    private final AwardBallotVoteRepository ballotVotes;

    public AwardResultsService(
            AwardEditionRepository editions,
            AwardVoteCategoryRepository voteCategories,
            AwardCandidateRepository candidates,
            AwardCoachVoterRepository voters,
            AwardBallotRepository ballots,
            AwardBallotVoteRepository ballotVotes
    ) {
        this.editions = editions;
        this.voteCategories = voteCategories;
        this.candidates = candidates;
        this.voters = voters;
        this.ballots = ballots;
        this.ballotVotes = ballotVotes;
    }

    @Transactional(readOnly = true)
    public AwardAdminAuditResponse audit(
            UUID editionId
    ) {
        Snapshot snapshot = snapshot(editionId);
        return audit(snapshot);
    }

    @Transactional(readOnly = true)
    public AwardAdminResultsResponse results(
            UUID editionId
    ) {
        Snapshot snapshot = snapshot(editionId);

        if (snapshot.edition().getStatus()
                != AwardEditionStatus.CLOSED) {
            throw Errors.conflict(
                    "AWARD_RESULTS_LOCKED_UNTIL_CLOSE",
                    "A apuração por candidato só é liberada depois que a votação for encerrada."
            );
        }

        AwardAdminAuditResponse audit = audit(snapshot);

        if (!audit.integrityOk()) {
            throw Errors.conflict(
                    "AWARD_RESULTS_INTEGRITY_FAILED",
                    "A apuração foi bloqueada porque a auditoria encontrou inconsistências."
            );
        }

        Map<UUID, AwardCoachVoter> voterById =
                snapshot.voters().stream()
                        .collect(Collectors.toMap(
                                AwardCoachVoter::getId,
                                Function.identity()
                        ));

        Map<UUID, List<AwardBallotVote>> votesByBallot =
                snapshot.votes().stream()
                        .collect(Collectors.groupingBy(
                                AwardBallotVote::getBallotId
                        ));

        Map<ContextKey, List<AwardCandidate>> candidatesByContext =
                snapshot.candidates().stream()
                        .filter(AwardCandidate::isActive)
                        .collect(Collectors.groupingBy(candidate ->
                                new ContextKey(
                                        candidate.getEventId(),
                                        candidate.getDivisionId(),
                                        candidate.getCategoryId()
                                )
                        ));

        Map<ContextKey, List<AwardBallot>> ballotsByContext =
                new HashMap<>();

        for (AwardBallot ballot : snapshot.ballots()) {
            AwardCoachVoter voter = voterById.get(ballot.getCoachVoterId());
            if (voter == null) {
                continue;
            }

            ContextKey key = ContextKey.from(voter);
            ballotsByContext
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(ballot);
        }

        Set<ContextKey> contextKeys = new LinkedHashSet<>();
        contextKeys.addAll(candidatesByContext.keySet());
        contextKeys.addAll(ballotsByContext.keySet());

        List<AwardContextResultResponse> contexts =
                contextKeys.stream()
                        .sorted(ContextKey.COMPARATOR)
                        .map(context -> buildContextResult(
                                context,
                                snapshot.categories(),
                                candidatesByContext.getOrDefault(
                                        context,
                                        List.of()
                                ),
                                ballotsByContext.getOrDefault(
                                        context,
                                        List.of()
                                ),
                                votesByBallot
                        ))
                        .toList();

        return new AwardAdminResultsResponse(
                snapshot.edition().getId(),
                snapshot.edition().getName(),
                snapshot.edition().getSeason(),
                snapshot.edition().getStatus().name(),
                snapshot.edition().getVotingClosesAt(),
                snapshot.ballots().size(),
                snapshot.votes().size(),
                true,
                contexts
        );
    }

    private AwardAdminAuditResponse audit(
            Snapshot snapshot
    ) {
        Map<UUID, AwardVoteCategory> categoryById =
                snapshot.categories().stream()
                        .collect(Collectors.toMap(
                                AwardVoteCategory::getId,
                                Function.identity()
                        ));

        Map<UUID, AwardCandidate> candidateById =
                snapshot.candidates().stream()
                        .collect(Collectors.toMap(
                                AwardCandidate::getId,
                                Function.identity()
                        ));

        Map<UUID, AwardCoachVoter> voterById =
                snapshot.voters().stream()
                        .collect(Collectors.toMap(
                                AwardCoachVoter::getId,
                                Function.identity()
                        ));

        Map<UUID, List<AwardBallotVote>> votesByBallot =
                snapshot.votes().stream()
                        .collect(Collectors.groupingBy(
                                AwardBallotVote::getBallotId
                        ));

        Set<UUID> knownBallotIds =
                snapshot.ballots().stream()
                        .map(AwardBallot::getId)
                        .collect(Collectors.toSet());

        List<AwardAuditIssueResponse> issues = new ArrayList<>();
        int complete = 0;

        for (AwardBallotVote vote : snapshot.votes()) {
            if (!knownBallotIds.contains(vote.getBallotId())) {
                issues.add(new AwardAuditIssueResponse(
                        vote.getBallotId(),
                        "ORPHAN_VOTE",
                        "Existe uma linha de voto sem cédula correspondente nesta edição."
                ));
            }
        }

        List<AwardVoteCategory> required =
                snapshot.categories().stream()
                        .filter(AwardVoteCategory::isRequired)
                        .toList();

        for (AwardBallot ballot : snapshot.ballots()) {
            int before = issues.size();
            AwardCoachVoter voter = voterById.get(ballot.getCoachVoterId());

            if (voter == null) {
                issues.add(issue(
                        ballot,
                        "VOTER_NOT_FOUND",
                        "A cédula não possui um treinador-votante válido."
                ));
                continue;
            }

            if (!voter.getEditionId().equals(snapshot.edition().getId())
                    || !voter.getUserId().equals(ballot.getVoterUserId())) {
                issues.add(issue(
                        ballot,
                        "BALLOT_VOTER_MISMATCH",
                        "A cédula não corresponde ao usuário e à edição do treinador-votante."
                ));
            }

            List<AwardBallotVote> selections =
                    votesByBallot.getOrDefault(
                            ballot.getId(),
                            List.of()
                    );

            Set<UUID> categoryIds = new HashSet<>();

            for (AwardBallotVote vote : selections) {
                if (!categoryIds.add(vote.getAwardVoteCategoryId())) {
                    issues.add(issue(
                            ballot,
                            "DUPLICATE_CATEGORY",
                            "A cédula possui mais de um voto para a mesma categoria."
                    ));
                    continue;
                }

                AwardVoteCategory category =
                        categoryById.get(vote.getAwardVoteCategoryId());

                if (category == null) {
                    issues.add(issue(
                            ballot,
                            "CATEGORY_NOT_FOUND",
                            "A cédula referencia uma categoria de votação inválida."
                    ));
                    continue;
                }

                AwardCandidate candidate =
                        candidateById.get(vote.getCandidateId());

                if (candidate == null) {
                    issues.add(issue(
                            ballot,
                            "CANDIDATE_NOT_FOUND",
                            "A cédula referencia um candidato inexistente."
                    ));
                    continue;
                }

                validateSelection(
                        ballot,
                        voter,
                        category,
                        candidate,
                        snapshot.edition().getId(),
                        issues
                );
            }

            for (AwardVoteCategory category : required) {
                if (!categoryIds.contains(category.getId())) {
                    issues.add(issue(
                            ballot,
                            "REQUIRED_CATEGORY_MISSING",
                            "A cédula não possui voto em todas as categorias obrigatórias."
                    ));
                }
            }

            if (issues.size() == before) {
                complete++;
            }
        }

        Set<UUID> invalidBallots =
                issues.stream()
                        .map(AwardAuditIssueResponse::ballotId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        return new AwardAdminAuditResponse(
                snapshot.edition().getId(),
                snapshot.edition().getName(),
                snapshot.edition().getStatus().name(),
                snapshot.ballots().size(),
                snapshot.votes().size(),
                required.size(),
                complete,
                invalidBallots.size(),
                issues.isEmpty(),
                List.copyOf(issues)
        );
    }

    private void validateSelection(
            AwardBallot ballot,
            AwardCoachVoter voter,
            AwardVoteCategory category,
            AwardCandidate candidate,
            UUID editionId,
            List<AwardAuditIssueResponse> issues
    ) {
        if (!candidate.isActive()
                || !candidate.getEditionId().equals(editionId)
                || candidate.getEventId() != voter.getEventId()
                || candidate.getDivisionId() != voter.getDivisionId()
                || candidate.getCategoryId() != voter.getCategoryId()) {
            issues.add(issue(
                    ballot,
                    "CANDIDATE_OUT_OF_CONTEXT",
                    "Um voto referencia candidato fora do contexto permitido ao treinador."
            ));
        }

        if (candidate.getCandidateType() != category.getTargetType()) {
            issues.add(issue(
                    ballot,
                    "CANDIDATE_TYPE_MISMATCH",
                    "Um voto referencia candidato incompatível com a categoria da premiação."
            ));
        }

        if (category.getTargetType() == AwardCandidateType.ATHLETE
                && !Objects.equals(
                category.getPositionCode(),
                candidate.getPositionCode()
        )) {
            issues.add(issue(
                    ballot,
                    "CANDIDATE_POSITION_MISMATCH",
                    "Um voto referencia atleta de posição incompatível."
            ));
        }

        if (category.getTargetType() == AwardCandidateType.COACH
                && candidate.getId().equals(
                voter.getSelfCoachCandidateId()
        )) {
            issues.add(issue(
                    ballot,
                    "SELF_COACH_VOTE",
                    "Foi encontrado voto de treinador nele próprio."
            ));
        }
    }

    private AwardContextResultResponse buildContextResult(
            ContextKey context,
            List<AwardVoteCategory> categories,
            List<AwardCandidate> contextCandidates,
            List<AwardBallot> contextBallots,
            Map<UUID, List<AwardBallotVote>> votesByBallot
    ) {
        Set<UUID> ballotIds =
                contextBallots.stream()
                        .map(AwardBallot::getId)
                        .collect(Collectors.toSet());

        List<AwardBallotVote> contextVotes =
                ballotIds.stream()
                        .flatMap(ballotId ->
                                votesByBallot.getOrDefault(
                                        ballotId,
                                        List.of()
                                ).stream()
                        )
                        .toList();

        List<AwardCategoryResultResponse> categoryResults =
                categories.stream()
                        .sorted(Comparator.comparingInt(
                                AwardVoteCategory::getDisplayOrder
                        ))
                        .map(category -> buildCategoryResult(
                                category,
                                contextCandidates,
                                contextVotes
                        ))
                        .toList();

        return new AwardContextResultResponse(
                context.eventId(),
                context.divisionId(),
                context.categoryId(),
                contextBallots.size(),
                categoryResults
        );
    }

    private AwardCategoryResultResponse buildCategoryResult(
            AwardVoteCategory category,
            List<AwardCandidate> contextCandidates,
            List<AwardBallotVote> contextVotes
    ) {
        Map<UUID, Integer> counts = new HashMap<>();

        for (AwardBallotVote vote : contextVotes) {
            if (category.getId().equals(
                    vote.getAwardVoteCategoryId()
            )) {
                counts.merge(
                        vote.getCandidateId(),
                        1,
                        Integer::sum
                );
            }
        }

        List<AwardCandidate> eligible =
                contextCandidates.stream()
                        .filter(candidate -> matches(
                                category,
                                candidate
                        ))
                        .toList();

        int totalVotes =
                counts.values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();

        List<CandidateCount> ordered =
                eligible.stream()
                        .map(candidate -> new CandidateCount(
                                candidate,
                                counts.getOrDefault(
                                        candidate.getId(),
                                        0
                                )
                        ))
                        .sorted(
                                Comparator
                                        .comparingInt(CandidateCount::votes)
                                        .reversed()
                                        .thenComparing(
                                                item -> item.candidate().getName(),
                                                String.CASE_INSENSITIVE_ORDER
                                        )
                        )
                        .toList();

        List<AwardCandidateResultResponse> candidateResults =
                new ArrayList<>();

        int previousVotes = Integer.MIN_VALUE;
        int previousRank = 0;

        for (int index = 0; index < ordered.size(); index++) {
            CandidateCount item = ordered.get(index);
            int rank;

            if (item.votes() == previousVotes) {
                rank = previousRank;
            } else {
                rank = index + 1;
                previousRank = rank;
                previousVotes = item.votes();
            }

            double percentage =
                    totalVotes == 0
                            ? 0.0
                            : Math.round(
                            (item.votes() * 10000.0) / totalVotes
                    ) / 100.0;

            candidateResults.add(
                    new AwardCandidateResultResponse(
                            rank,
                            item.candidate().getId(),
                            item.candidate().getName(),
                            item.candidate().getTeamId(),
                            item.candidate().getTeamName(),
                            item.candidate().getTeamLogoUrl(),
                            item.votes(),
                            percentage
                    )
            );
        }

        return new AwardCategoryResultResponse(
                category.getId(),
                category.getCode(),
                category.getLabel(),
                category.getTargetType().name(),
                category.getPositionCode(),
                totalVotes,
                candidateResults
        );
    }

    private boolean matches(
            AwardVoteCategory category,
            AwardCandidate candidate
    ) {
        if (!candidate.isActive()
                || candidate.getCandidateType()
                != category.getTargetType()) {
            return false;
        }

        return category.getTargetType()
                != AwardCandidateType.ATHLETE
                || Objects.equals(
                category.getPositionCode(),
                candidate.getPositionCode()
        );
    }

    private Snapshot snapshot(
            UUID editionId
    ) {
        AwardEdition edition =
                editions.findById(editionId)
                        .orElseThrow(() -> Errors.notFound(
                                "AWARD_EDITION_NOT_FOUND",
                                "Edição do prêmio não encontrada."
                        ));

        List<AwardBallot> editionBallots =
                ballots.findByEditionId(editionId);

        List<UUID> ballotIds =
                editionBallots.stream()
                        .map(AwardBallot::getId)
                        .toList();

        List<AwardBallotVote> editionVotes =
                ballotIds.isEmpty()
                        ? List.of()
                        : ballotVotes.findByBallotIdIn(ballotIds);

        return new Snapshot(
                edition,
                voteCategories.findByEditionIdOrderByDisplayOrderAsc(
                        editionId
                ),
                candidates.findByEditionIdOrderByTeamNameAscNameAsc(
                        editionId
                ),
                voters.findByEditionId(editionId),
                editionBallots,
                editionVotes
        );
    }

    private AwardAuditIssueResponse issue(
            AwardBallot ballot,
            String code,
            String detail
    ) {
        return new AwardAuditIssueResponse(
                ballot.getId(),
                code,
                detail
        );
    }

    private record Snapshot(
            AwardEdition edition,
            List<AwardVoteCategory> categories,
            List<AwardCandidate> candidates,
            List<AwardCoachVoter> voters,
            List<AwardBallot> ballots,
            List<AwardBallotVote> votes
    ) {
    }

    private record CandidateCount(
            AwardCandidate candidate,
            int votes
    ) {
    }

    private record ContextKey(
            long eventId,
            long divisionId,
            long categoryId
    ) {
        private static final Comparator<ContextKey> COMPARATOR =
                Comparator
                        .comparingLong(ContextKey::divisionId)
                        .thenComparingLong(ContextKey::categoryId)
                        .thenComparingLong(ContextKey::eventId);

        static ContextKey from(
                AwardCoachVoter voter
        ) {
            return new ContextKey(
                    voter.getEventId(),
                    voter.getDivisionId(),
                    voter.getCategoryId()
            );
        }
    }
}

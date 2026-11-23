package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AdminCoachBallotsResponse;
import br.com.dnafutsal.backend.awards.api.CoachBallotChoiceResponse;
import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardBallotVote;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCoachCredential;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotVoteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachCredentialRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Somente leitura das cédulas efetivamente persistidas, inclusive durante a votação. */
@Service
public class AdminCoachBallotsService {
    private final AwardCoachCredentialRepository credentials;
    private final AwardCoachVoterRepository contexts;
    private final AwardBallotRepository ballots;
    private final AwardBallotVoteRepository ballotVotes;
    private final AwardVoteCategoryRepository voteCategories;
    private final AwardCandidateRepository candidates;

    public AdminCoachBallotsService(AwardCoachCredentialRepository credentials,
                                    AwardCoachVoterRepository contexts,
                                    AwardBallotRepository ballots,
                                    AwardBallotVoteRepository ballotVotes,
                                    AwardVoteCategoryRepository voteCategories,
                                    AwardCandidateRepository candidates) {
        this.credentials = credentials;
        this.contexts = contexts;
        this.ballots = ballots;
        this.ballotVotes = ballotVotes;
        this.voteCategories = voteCategories;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public AdminCoachBallotsResponse findByCredential(UUID editionId, UUID credentialId) {
        // Check the edition before reading ballots; a credential from another edition
        // must not be usable to retrieve a coach's votes outside that edition.
        AwardCoachCredential credential = credentials.findById(credentialId)
                .filter(c -> c.getEditionId().equals(editionId) && !c.isRemoved())
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND",
                        "Credencial não encontrada nesta edição."));

        Map<UUID, AwardVoteCategory> categoryById = voteCategories
                .findByEditionIdOrderByDisplayOrderAsc(editionId).stream()
                .collect(Collectors.toMap(AwardVoteCategory::getId, Function.identity()));

        List<AdminCoachBallotsResponse.ContextBallot> result = new ArrayList<>();
        for (AwardCoachVoter context : contexts.findByCredentialIdOrderByCreatedAtAsc(credentialId)) {
            if (!editionId.equals(context.getEditionId())
                    || !credential.getUserId().equals(context.getUserId())) {
                continue;
            }

            AwardBallot ballot = ballots.findByEditionIdAndCoachVoterId(editionId, context.getId())
                    .orElse(null);
            if (ballot == null) {
                continue;
            }
            if (!credential.getUserId().equals(ballot.getVoterUserId())) {
                throw Errors.conflict("COACH_BALLOT_INCONSISTENT",
                        "A cédula não pertence ao treinador indicado.");
            }

            List<AwardBallotVote> rows = ballotVotes.findByBallotId(ballot.getId());
            Map<UUID, AwardCandidate> candidateById = candidates.findAllById(
                            rows.stream().map(AwardBallotVote::getCandidateId).distinct().toList())
                    .stream().collect(Collectors.toMap(AwardCandidate::getId, Function.identity()));

            List<CoachBallotChoiceResponse> choices = rows.stream().map(row -> {
                AwardVoteCategory category = categoryById.get(row.getAwardVoteCategoryId());
                AwardCandidate candidate = candidateById.get(row.getCandidateId());
                if (category == null || candidate == null
                        || !editionId.equals(category.getEditionId())
                        || !editionId.equals(candidate.getEditionId())) {
                    throw Errors.conflict("COACH_BALLOT_INCONSISTENT",
                            "Não foi possível identificar uma das escolhas registradas.");
                }
                return new CoachBallotChoiceResponse(category.getId(), category.getCode(),
                        category.getLabel(), candidate.getId(), candidate.getName(),
                        candidate.getTeamId(), candidate.getTeamName());
            }).sorted(Comparator.comparingInt(choice ->
                    categoryById.get(choice.voteCategoryId()).getDisplayOrder())).toList();

            String representedTeamName = candidates.findById(context.getSelfCoachCandidateId())
                    .map(AwardCandidate::getTeamName).orElse(context.getTeamId());
            result.add(new AdminCoachBallotsResponse.ContextBallot(
                    context.getId(), ballot.getId(), context.getEventId(),
                    context.getDivisionId(), context.getCategoryId(), context.getTeamId(),
                    representedTeamName, ballot.getSubmittedAt(), choices));
        }
        return new AdminCoachBallotsResponse(credentialId, editionId, List.copyOf(result));
    }
}

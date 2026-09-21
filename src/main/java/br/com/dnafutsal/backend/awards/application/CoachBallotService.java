package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.CoachBallotChoiceResponse;
import br.com.dnafutsal.backend.awards.api.CoachBallotResponse;
import br.com.dnafutsal.backend.awards.api.CoachBallotVoteRequest;
import br.com.dnafutsal.backend.awards.api.SubmitCoachBallotRequest;
import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardBallotVote;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotVoteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CoachBallotService {

    private final CoachVotingAccessService access;
    private final AwardVoteCategoryRepository voteCategories;
    private final AwardCandidateRepository candidates;
    private final AwardBallotRepository ballots;
    private final AwardBallotVoteRepository ballotVotes;
    private final Clock clock;

    public CoachBallotService(
            CoachVotingAccessService access,
            AwardVoteCategoryRepository voteCategories,
            AwardCandidateRepository candidates,
            AwardBallotRepository ballots,
            AwardBallotVoteRepository ballotVotes,
            Clock clock
    ) {
        this.access = access;
        this.voteCategories = voteCategories;
        this.candidates = candidates;
        this.ballots = ballots;
        this.ballotVotes = ballotVotes;
        this.clock = clock;
    }

    @Transactional
    public CoachBallotResponse submit(
            SubmitCoachBallotRequest request
    ) {
        CoachVotingAccessService.Access current =
                access.requireOpenForVoting();

        List<AwardVoteCategory> editionCategories =
                voteCategories.findByEditionIdOrderByDisplayOrderAsc(
                        current.edition()
                                .getId()
                );

        Map<UUID, AwardVoteCategory> categoryById =
                editionCategories.stream()
                        .collect(
                                Collectors.toMap(
                                        AwardVoteCategory::getId,
                                        Function.identity()
                                )
                        );

        Map<UUID, CoachBallotVoteRequest> requestedByCategory =
                new HashMap<>();

        for (CoachBallotVoteRequest vote
                : request.votes()) {
            if (requestedByCategory.putIfAbsent(
                    vote.voteCategoryId(),
                    vote
            ) != null) {
                throw Errors.badRequest(
                        "AWARD_DUPLICATE_VOTE_CATEGORY",
                        "Cada categoria da premiação só pode receber um voto."
                );
            }

            if (!categoryById.containsKey(
                    vote.voteCategoryId()
            )) {
                throw Errors.badRequest(
                        "AWARD_VOTE_CATEGORY_INVALID",
                        "Uma das categorias de votação não pertence a esta edição."
                );
            }
        }

        for (AwardVoteCategory category
                : editionCategories) {
            if (category.isRequired()
                    && !requestedByCategory.containsKey(
                            category.getId()
                    )) {
                throw Errors.badRequest(
                        "AWARD_REQUIRED_VOTE_MISSING",
                        "Preencha todas as categorias obrigatórias antes de registrar o voto."
                );
            }
        }

        Set<UUID> candidateIds =
                request.votes()
                        .stream()
                        .map(
                                CoachBallotVoteRequest::candidateId
                        )
                        .collect(
                                Collectors.toSet()
                        );

        Map<UUID, AwardCandidate> candidateById =
                candidates.findAllById(
                                candidateIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        AwardCandidate::getId,
                                        Function.identity()
                                )
                        );

        if (candidateById.size()
                != candidateIds.size()) {
            throw Errors.badRequest(
                    "AWARD_CANDIDATE_INVALID",
                    "Um dos candidatos selecionados não existe."
            );
        }

        List<CoachBallotChoiceResponse> choices =
                new ArrayList<>();

        for (CoachBallotVoteRequest vote
                : request.votes()) {
            AwardVoteCategory category =
                    categoryById.get(
                            vote.voteCategoryId()
                    );

            AwardCandidate candidate =
                    candidateById.get(
                            vote.candidateId()
                    );

            validateCandidate(
                    current,
                    category,
                    candidate
            );

            choices.add(
                    new CoachBallotChoiceResponse(
                            category.getId(),
                            category.getCode(),
                            category.getLabel(),
                            candidate.getId(),
                            candidate.getName(),
                            candidate.getTeamId(),
                            candidate.getTeamName()
                    )
            );
        }

        Instant submittedAt =
                clock.instant();

        AwardBallot ballot =
                new AwardBallot(
                        current.edition()
                                .getId(),
                        current.voter()
                                .getId(),
                        current.userId(),
                        submittedAt
                );

        try {
            ballots.saveAndFlush(
                    ballot
            );

            ballotVotes.saveAll(
                    request.votes()
                            .stream()
                            .map(vote ->
                                    new AwardBallotVote(
                                            ballot.getId(),
                                            vote.voteCategoryId(),
                                            vote.candidateId()
                                    )
                            )
                            .toList()
            );

            ballotVotes.flush();
        } catch (DataIntegrityViolationException exception) {
            throw Errors.conflict(
                    "AWARD_BALLOT_ALREADY_SUBMITTED",
                    "Seu voto já foi registrado e não pode ser alterado."
            );
        }

        Map<UUID, AwardVoteCategory> categories =
                categoryById;

        choices.sort(
                Comparator.comparingInt(choice ->
                        categories.get(
                                        choice.voteCategoryId()
                                )
                                .getDisplayOrder()
                )
        );

        return new CoachBallotResponse(
                ballot.getId(),
                ballot.getEditionId(),
                ballot.getSubmittedAt(),
                List.copyOf(
                        choices
                )
        );
    }

    private void validateCandidate(
            CoachVotingAccessService.Access current,
            AwardVoteCategory category,
            AwardCandidate candidate
    ) {
        boolean sameContext =
                candidate.isActive()
                        && candidate.getEditionId()
                        .equals(
                                current.edition()
                                        .getId()
                        )
                        && candidate.getEventId()
                        == current.voter()
                        .getEventId()
                        && candidate.getDivisionId()
                        == current.voter()
                        .getDivisionId()
                        && candidate.getCategoryId()
                        == current.voter()
                        .getCategoryId();

        if (!sameContext) {
            throw Errors.badRequest(
                    "AWARD_CANDIDATE_OUT_OF_CONTEXT",
                    "Um dos candidatos selecionados não pertence à divisão e categoria deste treinador."
            );
        }

        if (candidate.getCandidateType()
                != category.getTargetType()) {
            throw Errors.badRequest(
                    "AWARD_CANDIDATE_TYPE_MISMATCH",
                    "Um dos candidatos selecionados não corresponde à categoria de votação."
            );
        }

        if (category.getTargetType()
                == AwardCandidateType.COACH) {
            if (!candidate.isTeamCoachVoteCandidate()) {
                throw Errors.badRequest(
                        "AWARD_COACH_TEAM_SELECTION_REQUIRED",
                        "Na categoria Técnico, selecione uma equipe válida."
                );
            }

            if (candidate.getTeamId()
                    .equals(
                            current.voter()
                                    .getTeamId()
                    )) {
                throw Errors.badRequest(
                        "AWARD_SELF_COACH_VOTE_FORBIDDEN",
                        "O treinador não pode votar na própria equipe para a categoria Técnico."
                );
            }
        }
    }
}

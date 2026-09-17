package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.CoachBallotChoiceResponse;
import br.com.dnafutsal.backend.awards.api.CoachBallotResponse;
import br.com.dnafutsal.backend.awards.api.CoachVotingCandidateResponse;
import br.com.dnafutsal.backend.awards.api.CoachVotingCategoryResponse;
import br.com.dnafutsal.backend.awards.api.CoachVotingContextResponse;
import br.com.dnafutsal.backend.awards.api.CoachVotingTeamResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CoachVotingQueryService {

    private final CoachVotingAccessService access;
    private final AwardVoteCategoryRepository voteCategories;
    private final AwardCandidateRepository candidates;
    private final AwardBallotRepository ballots;
    private final AwardBallotVoteRepository ballotVotes;

    public CoachVotingQueryService(
            CoachVotingAccessService access,
            AwardVoteCategoryRepository voteCategories,
            AwardCandidateRepository candidates,
            AwardBallotRepository ballots,
            AwardBallotVoteRepository ballotVotes
    ) {
        this.access = access;
        this.voteCategories = voteCategories;
        this.candidates = candidates;
        this.ballots = ballots;
        this.ballotVotes = ballotVotes;
    }

    @Transactional(readOnly = true)
    public CoachVotingContextResponse context() {
        CoachVotingAccessService.Access current =
                access.current();

        List<AwardVoteCategory> categories =
                voteCategories.findByEditionIdOrderByDisplayOrderAsc(
                        current.edition()
                                .getId()
                );

        List<AwardCandidate> contextCandidates =
                candidates.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndActiveTrueOrderByTeamNameAscNameAsc(
                        current.edition()
                                .getId(),
                        current.voter()
                                .getEventId(),
                        current.voter()
                                .getDivisionId(),
                        current.voter()
                                .getCategoryId()
                );

        Map<String, CoachVotingTeamResponse> teams =
                new LinkedHashMap<>();

        for (AwardCandidate candidate
                : contextCandidates) {
            teams.putIfAbsent(
                    candidate.getTeamId(),
                    new CoachVotingTeamResponse(
                            candidate.getTeamId(),
                            candidate.getTeamName(),
                            candidate.getTeamLogoUrl()
                    )
            );
        }

        return new CoachVotingContextResponse(
                current.edition()
                        .getId(),
                current.edition()
                        .getSlug(),
                current.edition()
                        .getName(),
                current.edition()
                        .getSeason(),
                current.state()
                        .name(),
                current.edition()
                        .getVotingOpensAt(),
                current.edition()
                        .getVotingClosesAt(),
                current.voter()
                        .getId(),
                current.coach()
                        .getName(),
                current.voter()
                        .getEventId(),
                current.voter()
                        .getDivisionId(),
                current.voter()
                        .getCategoryId(),
                current.voter()
                        .getTeamId(),
                current.coach()
                        .getTeamName(),
                current.submitted(),
                categories.stream()
                        .map(
                                CoachVotingCategoryResponse::from
                        )
                        .toList(),
                List.copyOf(
                        teams.values()
                )
        );
    }

    @Transactional(readOnly = true)
    public List<CoachVotingCandidateResponse> candidates(
            UUID voteCategoryId,
            String teamId
    ) {
        CoachVotingAccessService.Access current =
                access.requireOpenForVoting();

        AwardVoteCategory category =
                voteCategories.findById(
                                voteCategoryId
                        )
                        .orElseThrow(() -> Errors.badRequest(
                                "AWARD_VOTE_CATEGORY_INVALID",
                                "A categoria de votação informada não existe."
                        ));

        if (!category.getEditionId()
                .equals(
                        current.edition()
                                .getId()
                )) {
            throw Errors.badRequest(
                    "AWARD_VOTE_CATEGORY_INVALID",
                    "A categoria de votação não pertence a esta edição."
            );
        }

        String normalizedTeamId =
                teamId == null
                        ? ""
                        : teamId.trim();

        if (normalizedTeamId.isBlank()
                || !candidates.existsByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndTeamIdAndActiveTrue(
                        current.edition()
                                .getId(),
                        current.voter()
                                .getEventId(),
                        current.voter()
                                .getDivisionId(),
                        current.voter()
                                .getCategoryId(),
                        normalizedTeamId
                )) {
            throw Errors.badRequest(
                    "AWARD_TEAM_INVALID",
                    "O time informado não pertence ao contexto desta votação."
            );
        }

        return candidates
                .findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndTeamIdAndActiveTrueOrderByNameAsc(
                        current.edition()
                                .getId(),
                        current.voter()
                                .getEventId(),
                        current.voter()
                                .getDivisionId(),
                        current.voter()
                                .getCategoryId(),
                        normalizedTeamId
                )
                .stream()
                .filter(candidate ->
                        candidate.getCandidateType()
                                == category.getTargetType()
                )
                .filter(candidate ->
                        category.getTargetType()
                                != AwardCandidateType.ATHLETE
                                || Objects.equals(
                                        category.getPositionCode(),
                                        candidate.getPositionCode()
                                )
                )
                .filter(candidate ->
                        category.getTargetType()
                                != AwardCandidateType.COACH
                                || !candidate.getId()
                                .equals(
                                        current.voter()
                                                .getSelfCoachCandidateId()
                                )
                )
                .map(
                        CoachVotingCandidateResponse::from
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public CoachBallotResponse ballot() {
        CoachVotingAccessService.Access current =
                access.current();

        AwardBallot ballot =
                ballots.findByEditionIdAndVoterUserId(
                                current.edition()
                                        .getId(),
                                current.userId()
                        )
                        .orElseThrow(() -> Errors.notFound(
                                "AWARD_BALLOT_NOT_FOUND",
                                "Nenhum voto registrado foi encontrado para esta conta."
                        ));

        return response(
                ballot
        );
    }

    private CoachBallotResponse response(
            AwardBallot ballot
    ) {
        List<AwardBallotVote> votes =
                ballotVotes.findByBallotId(
                        ballot.getId()
                );

        Map<UUID, AwardVoteCategory> categories =
                voteCategories.findByEditionIdOrderByDisplayOrderAsc(
                                ballot.getEditionId()
                        )
                        .stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AwardVoteCategory::getId,
                                        category -> category
                                )
                        );

        Map<UUID, AwardCandidate> selected =
                candidates.findAllById(
                                votes.stream()
                                        .map(
                                                AwardBallotVote::getCandidateId
                                        )
                                        .toList()
                        )
                        .stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AwardCandidate::getId,
                                        candidate -> candidate
                                )
                        );

        List<CoachBallotChoiceResponse> choices =
                votes.stream()
                        .map(vote -> {
                            AwardVoteCategory category =
                                    categories.get(
                                            vote.getAwardVoteCategoryId()
                                    );

                            AwardCandidate candidate =
                                    selected.get(
                                            vote.getCandidateId()
                                    );

                            if (category == null
                                    || candidate == null) {
                                throw Errors.conflict(
                                        "AWARD_BALLOT_INCONSISTENT",
                                        "O voto registrado está inconsistente."
                                );
                            }

                            return new CoachBallotChoiceResponse(
                                    category.getId(),
                                    category.getCode(),
                                    category.getLabel(),
                                    candidate.getId(),
                                    candidate.getName(),
                                    candidate.getTeamId(),
                                    candidate.getTeamName()
                            );
                        })
                        .sorted(
                                Comparator.comparingInt(choice ->
                                        categories.get(
                                                        choice.voteCategoryId()
                                                )
                                                .getDisplayOrder()
                                )
                        )
                        .toList();

        return new CoachBallotResponse(
                ballot.getId(),
                ballot.getEditionId(),
                ballot.getSubmittedAt(),
                choices
        );
    }
}

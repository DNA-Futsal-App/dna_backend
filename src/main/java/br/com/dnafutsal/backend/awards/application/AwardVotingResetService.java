package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.AwardEditionAdminResponse;
import br.com.dnafutsal.backend.awards.api.ResetAwardVotingResponse;
import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardBallotVote;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotVoteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AwardVotingResetService {

    private final AwardEditionRepository editions;
    private final AwardBallotRepository ballots;
    private final AwardBallotVoteRepository ballotVotes;

    public AwardVotingResetService(
            AwardEditionRepository editions,
            AwardBallotRepository ballots,
            AwardBallotVoteRepository ballotVotes
    ) {
        this.editions = editions;
        this.ballots = ballots;
        this.ballotVotes = ballotVotes;
    }

    @Transactional
    public ResetAwardVotingResponse reset(
            UUID editionId
    ) {
        AwardEdition edition =
                editions.findById(
                                editionId
                        )
                        .orElseThrow(() -> Errors.notFound(
                                "AWARD_EDITION_NOT_FOUND",
                                "EdiÃ§Ã£o do prÃªmio nÃ£o encontrada."
                        ));

        if (edition.getStatus() == AwardEditionStatus.DRAFT) {
            throw Errors.conflict(
                    "AWARD_VOTING_RESET_NOT_ALLOWED",
                    "A votaÃ§Ã£o jÃ¡ estÃ¡ em rascunho."
            );
        }

        List<AwardBallot> editionBallots =
                ballots.findByEditionId(
                        editionId
                );

        List<UUID> ballotIds =
                editionBallots.stream()
                        .map(AwardBallot::getId)
                        .toList();

        List<AwardBallotVote> editionVotes =
                ballotIds.isEmpty()
                        ? List.of()
                        : ballotVotes.findByBallotIdIn(
                                ballotIds
                        );

        if (!editionVotes.isEmpty()) {
            ballotVotes.deleteAllInBatch(
                    editionVotes
            );
        }

        if (!editionBallots.isEmpty()) {
            ballots.deleteAllInBatch(
                    editionBallots
            );
            ballots.flush();
        }

        edition.resetVoting();

        AwardEdition savedEdition =
                editions.saveAndFlush(
                        edition
                );

        return new ResetAwardVotingResponse(
                editionId,
                editionBallots.size(),
                editionVotes.size(),
                AwardEditionAdminResponse.from(
                        savedEdition
                )
        );
    }
}
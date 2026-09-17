
package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardBallotVote;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.AwardPlayerPosition;
import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotVoteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardResultsServiceTest {

    @Mock AwardEditionRepository editions;
    @Mock AwardVoteCategoryRepository categories;
    @Mock AwardCandidateRepository candidates;
    @Mock AwardCoachVoterRepository voters;
    @Mock AwardBallotRepository ballots;
    @Mock AwardBallotVoteRepository votes;

    AwardResultsService service;

    final Instant now =
            Instant.parse("2026-09-20T18:00:00Z");

    @BeforeEach
    void setUp() {
        service = new AwardResultsService(
                editions,
                categories,
                candidates,
                voters,
                ballots,
                votes
        );
    }

    @Test
    void blocksCandidateResultsBeforeVotingCloses() {
        AwardEdition edition = edition(AwardEditionStatus.OPEN);
        emptySnapshot(edition);

        assertThatThrownBy(() ->
                service.results(edition.getId())
        )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("depois que a votação for encerrada");
    }

    @Test
    void aggregatesClosedResultsBySportsContext() {
        AwardEdition edition = edition(AwardEditionStatus.CLOSED);

        AwardVoteCategory goalkeeper =
                new AwardVoteCategory(
                        edition.getId(),
                        "GOLEIRO",
                        "Goleiro",
                        AwardCandidateType.ATHLETE,
                        "GOLEIRO",
                        10,
                        true
                );

        AwardCandidate first = athlete(
                edition,
                "a1",
                "Atleta Um",
                "10",
                "Time A"
        );
        AwardCandidate second = athlete(
                edition,
                "a2",
                "Atleta Dois",
                "20",
                "Time B"
        );

        AwardCandidate coachOne = coach(
                edition,
                "c1",
                "Treinador Um",
                "10",
                "Time A"
        );
        AwardCandidate coachTwo = coach(
                edition,
                "c2",
                "Treinador Dois",
                "20",
                "Time B"
        );
        AwardCandidate coachThree = coach(
                edition,
                "c3",
                "Treinador Três",
                "30",
                "Time C"
        );

        AwardCoachVoter voterOne = voter(edition, coachOne, "10");
        AwardCoachVoter voterTwo = voter(edition, coachTwo, "20");
        AwardCoachVoter voterThree = voter(edition, coachThree, "30");

        AwardBallot ballotOne = ballot(edition, voterOne);
        AwardBallot ballotTwo = ballot(edition, voterTwo);
        AwardBallot ballotThree = ballot(edition, voterThree);

        List<AwardBallotVote> ballotVotes = List.of(
                new AwardBallotVote(
                        ballotOne.getId(),
                        goalkeeper.getId(),
                        first.getId()
                ),
                new AwardBallotVote(
                        ballotTwo.getId(),
                        goalkeeper.getId(),
                        first.getId()
                ),
                new AwardBallotVote(
                        ballotThree.getId(),
                        goalkeeper.getId(),
                        second.getId()
                )
        );

        when(editions.findById(edition.getId()))
                .thenReturn(Optional.of(edition));
        when(categories.findByEditionIdOrderByDisplayOrderAsc(edition.getId()))
                .thenReturn(List.of(goalkeeper));
        when(candidates.findByEditionIdOrderByTeamNameAscNameAsc(edition.getId()))
                .thenReturn(List.of(
                        first,
                        second,
                        coachOne,
                        coachTwo,
                        coachThree
                ));
        when(voters.findByEditionId(edition.getId()))
                .thenReturn(List.of(
                        voterOne,
                        voterTwo,
                        voterThree
                ));
        when(ballots.findByEditionId(edition.getId()))
                .thenReturn(List.of(
                        ballotOne,
                        ballotTwo,
                        ballotThree
                ));
        when(votes.findByBallotIdIn(List.of(
                ballotOne.getId(),
                ballotTwo.getId(),
                ballotThree.getId()
        ))).thenReturn(ballotVotes);

        var result = service.results(edition.getId());

        assertThat(result.integrityOk()).isTrue();
        assertThat(result.contexts()).hasSize(1);

        var category = result.contexts().get(0).categories().get(0);
        assertThat(category.totalVotes()).isEqualTo(3);
        assertThat(category.candidates()).hasSize(2);
        assertThat(category.candidates().get(0).candidateName())
                .isEqualTo("Atleta Um");
        assertThat(category.candidates().get(0).rank())
                .isEqualTo(1);
        assertThat(category.candidates().get(0).votes())
                .isEqualTo(2);
        assertThat(category.candidates().get(0).percentage())
                .isEqualTo(66.67);
        assertThat(category.candidates().get(1).rank())
                .isEqualTo(2);
    }

    @Test
    void auditDetectsCandidateOutsideCoachContext() {
        AwardEdition edition = edition(AwardEditionStatus.CLOSED);

        AwardVoteCategory goalkeeper =
                new AwardVoteCategory(
                        edition.getId(),
                        "GOLEIRO",
                        "Goleiro",
                        AwardCandidateType.ATHLETE,
                        "GOLEIRO",
                        10,
                        true
                );

        AwardCandidate coach = coach(
                edition,
                "c1",
                "Treinador",
                "10",
                "Time A"
        );

        AwardCandidate wrongContext =
                new AwardCandidate(
                        edition.getId(),
                        AwardCandidateType.ATHLETE,
                        "athlete-x",
                        "Atleta Fora",
                        null,
                        999,
                        99,
                        77,
                        "99",
                        "Outro Time",
                        null,
                        null
                );
        wrongContext.assignPosition(AwardPlayerPosition.GOLEIRO);

        AwardCoachVoter voter = voter(edition, coach, "10");
        AwardBallot ballot = ballot(edition, voter);
        AwardBallotVote vote =
                new AwardBallotVote(
                        ballot.getId(),
                        goalkeeper.getId(),
                        wrongContext.getId()
                );

        when(editions.findById(edition.getId()))
                .thenReturn(Optional.of(edition));
        when(categories.findByEditionIdOrderByDisplayOrderAsc(edition.getId()))
                .thenReturn(List.of(goalkeeper));
        when(candidates.findByEditionIdOrderByTeamNameAscNameAsc(edition.getId()))
                .thenReturn(List.of(coach, wrongContext));
        when(voters.findByEditionId(edition.getId()))
                .thenReturn(List.of(voter));
        when(ballots.findByEditionId(edition.getId()))
                .thenReturn(List.of(ballot));
        when(votes.findByBallotIdIn(List.of(ballot.getId())))
                .thenReturn(List.of(vote));

        var audit = service.audit(edition.getId());

        assertThat(audit.integrityOk()).isFalse();
        assertThat(audit.invalidBallots()).isEqualTo(1);
        assertThat(audit.issues())
                .extracting(br.com.dnafutsal.backend.awards.api.AwardAuditIssueResponse::code)
                .contains("CANDIDATE_OUT_OF_CONTEXT");
    }

    private AwardEdition edition(
            AwardEditionStatus status
    ) {
        return new AwardEdition(
                "premio-dna-2026",
                "Prêmio DNA Futsal 2026",
                2026,
                status,
                now.minusSeconds(3600),
                now.minusSeconds(60)
        );
    }

    private AwardCandidate athlete(
            AwardEdition edition,
            String externalId,
            String name,
            String teamId,
            String teamName
    ) {
        AwardCandidate candidate = new AwardCandidate(
                edition.getId(),
                AwardCandidateType.ATHLETE,
                externalId,
                name,
                null,
                904,
                3,
                7,
                teamId,
                teamName,
                null,
                null
        );
        candidate.assignPosition(AwardPlayerPosition.GOLEIRO);
        return candidate;
    }

    private AwardCandidate coach(
            AwardEdition edition,
            String externalId,
            String name,
            String teamId,
            String teamName
    ) {
        return new AwardCandidate(
                edition.getId(),
                AwardCandidateType.COACH,
                externalId,
                name,
                null,
                904,
                3,
                7,
                teamId,
                teamName,
                null,
                null
        );
    }

    private AwardCoachVoter voter(
            AwardEdition edition,
            AwardCandidate coach,
            String teamId
    ) {
        return new AwardCoachVoter(
                edition.getId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                coach.getId(),
                904,
                3,
                7,
                teamId
        );
    }

    private AwardBallot ballot(
            AwardEdition edition,
            AwardCoachVoter voter
    ) {
        return new AwardBallot(
                edition.getId(),
                voter.getId(),
                voter.getUserId(),
                now
        );
    }

    private void emptySnapshot(
            AwardEdition edition
    ) {
        when(editions.findById(edition.getId()))
                .thenReturn(Optional.of(edition));
        when(categories.findByEditionIdOrderByDisplayOrderAsc(edition.getId()))
                .thenReturn(List.of());
        when(candidates.findByEditionIdOrderByTeamNameAscNameAsc(edition.getId()))
                .thenReturn(List.of());
        when(voters.findByEditionId(edition.getId()))
                .thenReturn(List.of());
        when(ballots.findByEditionId(edition.getId()))
                .thenReturn(List.of());
    }
}

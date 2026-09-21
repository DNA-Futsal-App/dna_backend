package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.CoachBallotVoteRequest;
import br.com.dnafutsal.backend.awards.api.SubmitCoachBallotRequest;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.domain.AwardVoteCategory;
import br.com.dnafutsal.backend.awards.domain.CoachVotingState;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotVoteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardVoteCategoryRepository;
import br.com.dnafutsal.backend.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachBallotServiceTest {

    @Mock
    CoachVotingAccessService access;

    @Mock
    AwardVoteCategoryRepository voteCategories;

    @Mock
    AwardCandidateRepository candidates;

    @Mock
    AwardBallotRepository ballots;

    @Mock
    AwardBallotVoteRepository ballotVotes;

    CoachBallotService service;

    final Instant now =
            Instant.parse(
                    "2026-09-16T18:00:00Z"
            );

    @BeforeEach
    void setUp() {
        service = new CoachBallotService(
                access,
                voteCategories,
                candidates,
                ballots,
                ballotVotes,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void acceptsAthleteWithoutPredefinedPosition() {
        AwardEdition edition =
                new AwardEdition(
                        "premio-dna-2026",
                        "Prêmio DNA Futsal 2026",
                        2026,
                        AwardEditionStatus.OPEN,
                        now.minusSeconds(60),
                        now.plusSeconds(3600)
                );

        AwardCandidate selfCoach =
                new AwardCandidate(
                        edition.getId(),
                        AwardCandidateType.COACH,
                        "coach-1",
                        "Treinador Um",
                        null,
                        904,
                        3,
                        7,
                        "123",
                        "Time A",
                        null,
                        null
                );

        AwardCandidate athlete =
                new AwardCandidate(
                        edition.getId(),
                        AwardCandidateType.ATHLETE,
                        "athlete-1",
                        "Atleta Um",
                        null,
                        904,
                        3,
                        7,
                        "456",
                        "Time B",
                        null,
                        null
                );

        AwardCandidate ownTeamVote =
                AwardCandidate.imported(
                        edition.getId(),
                        AwardCandidateType.COACH,
                        null,
                        "Time A",
                        null,
                        AwardCandidate.TEAM_COACH_VOTE_ROLE,
                        UUID.randomUUID().toString(),
                        904,
                        3,
                        7,
                        "123",
                        "Time A",
                        null,
                        null,
                        now
                );

        AwardCoachVoter voter =
                new AwardCoachVoter(
                        edition.getId(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        selfCoach.getId(),
                        904,
                        3,
                        7,
                        "123"
                );

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

        when(access.requireOpenForVoting()).thenReturn(
                new CoachVotingAccessService.Access(
                        voter.getUserId(),
                        voter,
                        edition,
                        selfCoach,
                        false,
                        CoachVotingState.OPEN,
                        now
                )
        );
        when(voteCategories.findByEditionIdOrderByDisplayOrderAsc(edition.getId()))
                .thenReturn(List.of(goalkeeper));
        when(candidates.findAllById(any()))
                .thenReturn(List.of(athlete));

        var result = service.submit(
                new SubmitCoachBallotRequest(
                        List.of(
                                new CoachBallotVoteRequest(
                                        goalkeeper.getId(),
                                        athlete.getId()
                                )
                        )
                )
        );

        assertThat(result.votes()).hasSize(1);
        assertThat(result.votes().get(0).candidateName())
                .isEqualTo("Atleta Um");
    }

    @Test
    void rejectsVoteForTheCoachHimself() {
        AwardEdition edition =
                new AwardEdition(
                        "premio-dna-2026",
                        "Prêmio DNA Futsal 2026",
                        2026,
                        AwardEditionStatus.OPEN,
                        now.minusSeconds(
                                60
                        ),
                        now.plusSeconds(
                                3600
                        )
                );

        AwardCandidate selfCoach =
                new AwardCandidate(
                        edition.getId(),
                        AwardCandidateType.COACH,
                        "coach-1",
                        "Treinador Um",
                        null,
                        904,
                        3,
                        7,
                        "123",
                        "Time A",
                        null,
                        null
                );

        AwardCoachVoter voter =
                new AwardCoachVoter(
                        edition.getId(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        selfCoach.getId(),
                        904,
                        3,
                        7,
                        "123"
                );

        AwardVoteCategory coachCategory =
                new AwardVoteCategory(
                        edition.getId(),
                        "TECNICO",
                        "Técnico",
                        AwardCandidateType.COACH,
                        null,
                        50,
                        true
                );

        CoachVotingAccessService.Access current =
                new CoachVotingAccessService.Access(
                        voter.getUserId(),
                        voter,
                        edition,
                        selfCoach,
                        false,
                        CoachVotingState.OPEN,
                        now
                );

        when(access.requireOpenForVoting())
                .thenReturn(
                        current
                );

        when(voteCategories.findByEditionIdOrderByDisplayOrderAsc(
                edition.getId()
        )).thenReturn(
                List.of(
                        coachCategory
                )
        );

        when(candidates.findAllById(
                any()
        )).thenReturn(
                List.of(
                        ownTeamVote
                )
        );

        SubmitCoachBallotRequest request =
                new SubmitCoachBallotRequest(
                        List.of(
                                new CoachBallotVoteRequest(
                                        coachCategory.getId(),
                                        ownTeamVote.getId()
                                )
                        )
                );

        assertThatThrownBy(() ->
                service.submit(
                        request
                )
        )
                .isInstanceOf(
                        BusinessException.class
                )
                .hasMessageContaining(
                        "própria equipe"
                );
    }
}

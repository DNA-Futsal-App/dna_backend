package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCoachBallotsServiceTest {
    @Mock AwardCoachCredentialRepository credentials;
    @Mock AwardCoachVoterRepository contexts;
    @Mock AwardBallotRepository ballots;
    @Mock AwardBallotVoteRepository ballotVotes;
    @Mock AwardVoteCategoryRepository voteCategories;
    @Mock AwardCandidateRepository candidates;
    @InjectMocks AdminCoachBallotsService service;

    private final UUID editionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void showsEveryPersistedVoteOfTheCoachAcrossContexts() {
        AwardCoachCredential credential = new AwardCoachCredential(editionId, userId, null);
        AwardCoachVoter first = context(credential, 7L, "10");
        AwardCoachVoter second = context(credential, 12L, "20");
        AwardBallot firstBallot = new AwardBallot(editionId, first.getId(), userId,
                Instant.parse("2026-11-20T12:00:00Z"));
        AwardBallot secondBallot = new AwardBallot(editionId, second.getId(), userId,
                Instant.parse("2026-11-21T12:00:00Z"));
        AwardVoteCategory pivot = new AwardVoteCategory(editionId, "PIVO", "Melhor pivô",
                AwardCandidateType.ATHLETE, "PIVO", 10, true);
        AwardVoteCategory coach = new AwardVoteCategory(editionId, "COACH", "Melhor técnico",
                AwardCandidateType.COACH, null, 20, true);
        AwardCandidate athlete = candidate(AwardCandidateType.ATHLETE, 7L, "11", "Atleta A");
        AwardCandidate namedCoach = candidate(AwardCandidateType.COACH, 12L, "21", "Técnico B");
        AwardBallotVote pivotVote = new AwardBallotVote(firstBallot.getId(), pivot.getId(), athlete.getId());
        AwardBallotVote extraVote = new AwardBallotVote(firstBallot.getId(), coach.getId(), namedCoach.getId());
        AwardBallotVote coachVote = new AwardBallotVote(secondBallot.getId(), coach.getId(), namedCoach.getId());

        when(credentials.findById(credential.getId())).thenReturn(Optional.of(credential));
        when(contexts.findByCredentialIdOrderByCreatedAtAsc(credential.getId()))
                .thenReturn(List.of(first, second));
        when(voteCategories.findByEditionIdOrderByDisplayOrderAsc(editionId))
                .thenReturn(List.of(pivot, coach));
        when(ballots.findByEditionIdAndCoachVoterId(editionId, first.getId()))
                .thenReturn(Optional.of(firstBallot));
        when(ballots.findByEditionIdAndCoachVoterId(editionId, second.getId()))
                .thenReturn(Optional.of(secondBallot));
        when(ballotVotes.findByBallotId(firstBallot.getId())).thenReturn(List.of(extraVote, pivotVote));
        when(ballotVotes.findByBallotId(secondBallot.getId())).thenReturn(List.of(coachVote));
        when(candidates.findAllById(List.of(namedCoach.getId(), athlete.getId())))
                .thenReturn(List.of(athlete, namedCoach));
        when(candidates.findAllById(List.of(namedCoach.getId()))).thenReturn(List.of(namedCoach));

        var response = service.findByCredential(editionId, credential.getId());

        assertThat(response.credentialId()).isEqualTo(credential.getId());
        assertThat(response.ballots()).hasSize(2);
        assertThat(response.ballots().get(0).contextId()).isEqualTo(first.getId());
        assertThat(response.ballots().get(0).votes()).hasSize(2);
        assertThat(response.ballots().get(0).votes().get(0).candidateName()).isEqualTo("Atleta A");
        assertThat(response.ballots().get(0).votes().get(0).voteCategoryLabel()).isEqualTo("Melhor pivô");
        assertThat(response.ballots().get(0).votes().get(1).voteCategoryLabel()).isEqualTo("Melhor técnico");
        assertThat(response.ballots().get(1).votes().get(0).candidateName()).isEqualTo("Técnico B");
        assertThat(response.ballots().get(1).votes().get(0).voteCategoryLabel()).isEqualTo("Melhor técnico");
    }

    @Test
    void rejectsCredentialFromAnotherEditionBeforeReadingAnyBallots() {
        AwardCoachCredential credential = new AwardCoachCredential(UUID.randomUUID(), userId, null);
        when(credentials.findById(credential.getId())).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.findByCredential(editionId, credential.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Credencial não encontrada");
        verifyNoInteractions(contexts, ballots, ballotVotes, voteCategories, candidates);
    }

    @Test
    void returnsEmptyListForCoachWhoHasNotVoted() {
        AwardCoachCredential credential = new AwardCoachCredential(editionId, userId, null);
        AwardCoachVoter context = context(credential, 7L, "10");
        when(credentials.findById(credential.getId())).thenReturn(Optional.of(credential));
        when(contexts.findByCredentialIdOrderByCreatedAtAsc(credential.getId()))
                .thenReturn(List.of(context));
        when(voteCategories.findByEditionIdOrderByDisplayOrderAsc(editionId))
                .thenReturn(List.of());
        when(ballots.findByEditionIdAndCoachVoterId(editionId, context.getId()))
                .thenReturn(Optional.empty());

        assertThat(service.findByCredential(editionId, credential.getId()).ballots()).isEmpty();
        verifyNoInteractions(ballotVotes, candidates);
    }

    private AwardCoachVoter context(AwardCoachCredential credential, long categoryId, String teamId) {
        return new AwardCoachVoter(editionId, userId, credential.getId(), UUID.randomUUID(),
                917L, 3L, categoryId, teamId, AwardCoachVoterContextSource.SELF_SELECTED);
    }

    private AwardCandidate candidate(AwardCandidateType type, long categoryId,
                                     String teamId, String name) {
        return new AwardCandidate(editionId, type, null, name, null, 917L,
                3L, categoryId, teamId, "Clube " + teamId, null, null);
    }
}

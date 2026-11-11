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
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachGoldEligibilityVotingTest {
    @Mock CoachVotingAccessService access;
    @Mock AwardVoteCategoryRepository voteCategories;
    @Mock AwardCandidateRepository candidates;
    @Mock AwardBallotRepository ballots;
    @Mock AwardBallotVoteRepository ballotVotes;
    @Mock AwardGoldEligibilityService eligibility;

    @Test
    void rejectsDirectSubmissionOfAthleteFromNonGoldTeamBeforeSavingBallot() {
        Instant now = Instant.parse("2026-11-08T18:00:00Z");
        AwardEdition edition = new AwardEdition("legacy-2026", "Premio DNA", 2026,
                AwardEditionStatus.OPEN, now.minusSeconds(60), now.plusSeconds(3600));
        AwardCandidate self = new AwardCandidate(edition.getId(), AwardCandidateType.COACH,
                "coach-id", "Treinador", null, 904L, 3L, 7L, "123", "Clube A", null, null);
        AwardCandidate silver = new AwardCandidate(edition.getId(), AwardCandidateType.ATHLETE,
                "athlete-id", "Atleta Prata", null, 904L, 3L, 7L, "456", "Clube Prata", null, null);
        AwardCoachVoter voter = new AwardCoachVoter(edition.getId(), UUID.randomUUID(), UUID.randomUUID(),
                self.getId(), 904L, 3L, 7L, "123");
        AwardVoteCategory prize = new AwardVoteCategory(edition.getId(), "GOLEIRO", "Goleiro",
                AwardCandidateType.ATHLETE, "GOLEIRO", 10, true);
        when(access.requireOpenForVoting()).thenReturn(new CoachVotingAccessService.Access(
                voter.getUserId(), voter, edition, self, false, CoachVotingState.OPEN, now));
        when(voteCategories.findByEditionIdOrderByDisplayOrderAsc(edition.getId()))
                .thenReturn(List.of(prize));
        when(candidates.findAllById(any())).thenReturn(List.of(silver));
        doThrow(Errors.badRequest("AWARD_TEAM_NOT_GOLD", "Somente atletas da Chave Ouro."))
                .when(eligibility).requireEligible(edition.getId(), 904L, 3L, 7L, "456");

        CoachBallotService service = new CoachBallotService(access, voteCategories, candidates,
                ballots, ballotVotes, eligibility, Clock.fixed(now, ZoneOffset.UTC));
        SubmitCoachBallotRequest request = new SubmitCoachBallotRequest(List.of(
                new CoachBallotVoteRequest(prize.getId(), silver.getId())));

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chave Ouro");
        verify(ballots, never()).saveAndFlush(any());
    }
}

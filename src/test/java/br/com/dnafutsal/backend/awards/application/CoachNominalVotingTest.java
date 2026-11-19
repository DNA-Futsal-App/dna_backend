package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.CoachBallotVoteRequest;
import br.com.dnafutsal.backend.awards.api.SubmitCoachBallotV2Request;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachNominalVotingTest {
    @Mock CurrentUserService currentUser;
    @Mock AwardCoachCredentialRepository credentials;
    @Mock AwardCoachVoterRepository contexts;
    @Mock AwardEditionRepository editions;
    @Mock AwardVoteCategoryRepository voteCategories;
    @Mock AwardCandidateRepository candidates;
    @Mock AwardBallotRepository ballots;
    @Mock AwardBallotVoteRepository ballotVotes;
    @Mock AwardLiveRosterService liveRoster;
    @Mock AwardGoldEligibilityService eligibility;
    @Mock UserAccountRepository users;

    private final Instant now = Instant.parse("2026-11-19T12:00:00Z");
    private final UUID contextId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void returnsNamedStaffCandidatesAlreadySavedWithoutQueryingFederation() {
        Setup setup = setup();
        AwardCandidate headCoach = namedCoach(setup.edition().getId());
        when(voteCategories.findById(setup.voteCategory().getId()))
                .thenReturn(Optional.of(setup.voteCategory()));
        when(candidates.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndTeamIdAndActiveTrueOrderByNameAsc(
                setup.edition().getId(), 908L, 3L, 1L, "12253"))
                .thenReturn(List.of(headCoach));

        var result = setup.service().candidates(contextId, setup.voteCategory().getId(), "12253");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Maria Técnica");
        assertThat(result.get(0).id()).isEqualTo(headCoach.getId());
        verify(liveRoster, never()).coaches(any(), anyLong(), anyLong(), anyLong(), anyString());
        verify(liveRoster, never()).coachTeamVote(any(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void importsNamedStaffOnlyWhenCoachCacheIsEmpty() {
        Setup setup = setup();
        AwardCandidate headCoach = namedCoach(setup.edition().getId());
        when(voteCategories.findById(setup.voteCategory().getId()))
                .thenReturn(Optional.of(setup.voteCategory()));
        when(candidates.findByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndTeamIdAndActiveTrueOrderByNameAsc(
                setup.edition().getId(), 908L, 3L, 1L, "12253"))
                .thenReturn(List.of());
        when(liveRoster.coaches(setup.edition().getId(), 908L, 3L, 1L, "12253"))
                .thenReturn(List.of(headCoach));

        var result = setup.service().candidates(contextId, setup.voteCategory().getId(), "12253");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Maria Técnica");
        verify(liveRoster).coaches(setup.edition().getId(), 908L, 3L, 1L, "12253");
        verify(liveRoster, never()).coachTeamVote(any(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void rejectsSyntheticTeamNameCandidateForNewCoachBallot() {
        Setup setup = setup();
        AwardCandidate teamPlaceholder = AwardCandidate.imported(
                setup.edition().getId(), AwardCandidateType.COACH, null, "SE Palmeiras",
                null, AwardCandidate.TEAM_COACH_VOTE_ROLE, UUID.randomUUID().toString(),
                908L, 3L, 1L, "12253", "SE Palmeiras", null, null, now);
        when(voteCategories.findByEditionIdOrderByDisplayOrderAsc(setup.edition().getId()))
                .thenReturn(List.of(setup.voteCategory()));
        when(candidates.findAllById(any())).thenReturn(List.of(teamPlaceholder));

        var request = new SubmitCoachBallotV2Request(contextId, List.of(
                new CoachBallotVoteRequest(setup.voteCategory().getId(), teamPlaceholder.getId())));

        assertThatThrownBy(() -> setup.service().submit(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nome de um técnico");
        verify(ballots, never()).saveAndFlush(any());
    }

    private AwardCandidate namedCoach(UUID editionId) {
        return AwardCandidate.imported(
                editionId, AwardCandidateType.COACH, "coach-fpfs", "Maria Técnica",
                null, "Técnico", UUID.randomUUID().toString(), 908L, 3L, 1L,
                "12253", "SE Palmeiras", null, null, now);
    }

    private Setup setup() {
        AwardEdition edition = new AwardEdition("premio-dna-2026", "Prêmio DNA Futsal 2026",
                2026, AwardEditionStatus.OPEN, now.minusSeconds(60), now.plusSeconds(3600));
        AwardVoteCategory category = new AwardVoteCategory(edition.getId(), "TECNICO", "Técnico",
                AwardCandidateType.COACH, null, 50, true);
        UUID credentialId = UUID.randomUUID();
        AwardCoachVoter voter = mock(AwardCoachVoter.class);
        AwardCoachCredential credential = mock(AwardCoachCredential.class);
        when(currentUser.userId()).thenReturn(userId);
        when(contexts.findById(contextId)).thenReturn(Optional.of(voter));
        when(voter.isActive()).thenReturn(true);
        when(voter.getUserId()).thenReturn(userId);
        when(voter.getCredentialId()).thenReturn(credentialId);
        when(voter.getEditionId()).thenReturn(edition.getId());
        when(voter.getEventId()).thenReturn(908L);
        when(voter.getDivisionId()).thenReturn(3L);
        when(voter.getCategoryId()).thenReturn(1L);
        when(voter.getTeamId()).thenReturn("12321");
        when(credentials.findByIdAndActiveTrue(credentialId)).thenReturn(Optional.of(credential));
        when(credential.getUserId()).thenReturn(userId);
        when(editions.findById(edition.getId())).thenReturn(Optional.of(edition));
        when(users.findById(userId)).thenReturn(Optional.of(mock(UserAccount.class)));
        return new Setup(new CoachVotingV2Service(currentUser, credentials, contexts, editions,
                voteCategories, candidates, ballots, ballotVotes, liveRoster, eligibility,
                users, Clock.fixed(now, ZoneOffset.UTC)), edition, category);
    }

    private record Setup(CoachVotingV2Service service, AwardEdition edition,
                         AwardVoteCategory voteCategory) { }
}

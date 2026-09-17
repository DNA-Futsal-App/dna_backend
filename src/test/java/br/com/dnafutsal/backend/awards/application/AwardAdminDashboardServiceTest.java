package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardBallot;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInvite;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardBallotRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachInviteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwardAdminDashboardServiceTest {

    @Mock
    AwardEditionRepository editions;

    @Mock
    AwardCandidateRepository candidates;

    @Mock
    AwardCoachInviteRepository invites;

    @Mock
    AwardCoachVoterRepository voters;

    @Mock
    AwardBallotRepository ballots;

    @Mock
    UserAccountRepository users;

    AwardAdminDashboardService service;

    Instant now =
            Instant.parse("2026-09-16T23:30:00Z");

    @BeforeEach
    void setUp() {
        service = new AwardAdminDashboardService(
                editions,
                candidates,
                invites,
                voters,
                ballots,
                users,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    @Test
    void marksCoachAsVotedWhenBallotExists() {
        AwardEdition edition =
                new AwardEdition(
                        "premio-dna-2026",
                        "Prêmio DNA Futsal 2026",
                        2026,
                        AwardEditionStatus.OPEN,
                        now.minusSeconds(60),
                        now.plusSeconds(3600)
                );

        AwardCandidate coach =
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

        UUID userId = UUID.randomUUID();

        AwardCoachInvite invite =
                new AwardCoachInvite(
                        edition.getId(),
                        coach.getId(),
                        "hash",
                        now.plusSeconds(3600)
                );

        AwardCoachVoter voter =
                new AwardCoachVoter(
                        edition.getId(),
                        userId,
                        invite.getId(),
                        coach.getId(),
                        904,
                        3,
                        7,
                        "123"
                );

        AwardBallot ballot =
                new AwardBallot(
                        edition.getId(),
                        voter.getId(),
                        userId,
                        now
                );

        when(editions.findById(edition.getId()))
                .thenReturn(Optional.of(edition));

        when(candidates.findByEditionIdOrderByTeamNameAscNameAsc(
                edition.getId()
        )).thenReturn(List.of(coach));

        when(invites.findByEditionIdOrderByCreatedAtDesc(
                edition.getId()
        )).thenReturn(List.of(invite));

        when(voters.findByEditionId(edition.getId()))
                .thenReturn(List.of(voter));

        when(ballots.findByEditionId(edition.getId()))
                .thenReturn(List.of(ballot));

        when(users.findAllById(any()))
                .thenReturn(List.of());

        assertThat(
                service.coaches(edition.getId())
        )
                .singleElement()
                .extracting(
                        br.com.dnafutsal.backend.awards.api.AwardAdminCoachResponse::accessState
                )
                .isEqualTo("VOTED");
    }
}

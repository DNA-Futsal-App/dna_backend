package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.ClaimCoachInviteRequest;
import br.com.dnafutsal.backend.awards.api.CoachAccessResponse;
import br.com.dnafutsal.backend.awards.api.CreateCoachInviteRequest;
import br.com.dnafutsal.backend.awards.api.CreateCoachInviteResponse;
import br.com.dnafutsal.backend.awards.domain.AwardCandidate;
import br.com.dnafutsal.backend.awards.domain.AwardCandidateType;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInvite;
import br.com.dnafutsal.backend.awards.domain.AwardCoachInviteStatus;
import br.com.dnafutsal.backend.awards.domain.AwardCoachVoter;
import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCandidateRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachInviteRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardCoachVoterRepository;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.AwardProperties;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachInviteServiceTest {

    @Mock
    AwardEditionRepository editions;

    @Mock
    AwardCandidateRepository candidates;

    @Mock
    AwardCoachInviteRepository invites;

    @Mock
    AwardCoachVoterRepository voters;

    @Mock
    TokenSupport tokens;

    @Mock
    CurrentUserService currentUser;

    private CoachInviteService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-16T22:00:00Z"),
                ZoneOffset.UTC
        );

        service = new CoachInviteService(
                editions,
                candidates,
                invites,
                voters,
                tokens,
                currentUser,
                new AppProperties(
                        "https://dnafutsaloficial.com.br/",
                        List.of("https://dnafutsaloficial.com.br"),
                        "America/Sao_Paulo"
                ),
                new AwardProperties(Duration.ofDays(30)),
                clock
        );
    }

    @Test
    void createsInviteWithOnlyHashedTokenPersisted() {
        AwardEdition edition = edition();
        AwardCandidate coach = coach(edition);

        when(editions.findById(edition.getId()))
                .thenReturn(Optional.of(edition));
        when(candidates.findByIdAndEditionId(
                coach.getId(),
                edition.getId()
        )).thenReturn(Optional.of(coach));
        when(invites.findByEditionIdAndCoachCandidateIdAndStatus(
                edition.getId(),
                coach.getId(),
                AwardCoachInviteStatus.PENDING
        )).thenReturn(Optional.empty());
        when(tokens.generate()).thenReturn("raw-token");
        when(tokens.hash("raw-token")).thenReturn("hashed-token");
        when(invites.saveAndFlush(any(AwardCoachInvite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateCoachInviteResponse response = service.create(
                new CreateCoachInviteRequest(
                        edition.getId(),
                        coach.getId()
                )
        );

        ArgumentCaptor<AwardCoachInvite> captor =
                ArgumentCaptor.forClass(AwardCoachInvite.class);
        verify(invites).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getTokenHash())
                .isEqualTo("hashed-token");
        assertThat(response.inviteUrl())
                .isEqualTo(
                        "https://dnafutsaloficial.com.br"
                                + "/premio-dna/treinadores/convite/raw-token"
                );
        assertThat(response.expiresAt())
                .isEqualTo(
                        Instant.parse("2026-10-16T22:00:00Z")
                );
    }

    @Test
    void claimsInviteAndCreatesCoachVoter() {
        AwardEdition edition = edition();
        AwardCandidate coach = coach(edition);
        UUID userId = UUID.randomUUID();

        AwardCoachInvite invite = new AwardCoachInvite(
                edition.getId(),
                coach.getId(),
                "hashed-token",
                Instant.parse("2026-10-16T22:00:00Z")
        );

        when(currentUser.userId()).thenReturn(userId);
        when(tokens.hash("raw-token")).thenReturn("hashed-token");
        when(invites.findByTokenHashForUpdate("hashed-token"))
                .thenReturn(Optional.of(invite));
        when(editions.findById(edition.getId()))
                .thenReturn(Optional.of(edition));
        when(candidates.findByIdAndEditionId(
                coach.getId(),
                edition.getId()
        )).thenReturn(Optional.of(coach));
        when(voters.existsByEditionIdAndUserId(
                edition.getId(),
                userId
        )).thenReturn(false);
        when(voters.existsByEditionIdAndSelfCoachCandidateId(
                edition.getId(),
                coach.getId()
        )).thenReturn(false);
        when(voters.saveAndFlush(any(AwardCoachVoter.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CoachAccessResponse response = service.claim(
                new ClaimCoachInviteRequest(
                        "raw-token",
                        904L,
                        3L,
                        7L,
                        "CORINTHIANS"
                )
        );

        assertThat(invite.getStatus())
                .isEqualTo(AwardCoachInviteStatus.CLAIMED);
        assertThat(invite.getClaimedByUserId())
                .isEqualTo(userId);
        assertThat(response.editionId())
                .isEqualTo(edition.getId());
        assertThat(response.teamId())
                .isEqualTo("CORINTHIANS");

        verify(invites).save(invite);
        verify(voters).saveAndFlush(any(AwardCoachVoter.class));
    }

    @Test
    void rejectsWrongTeamBeforeCreatingVoter() {
        AwardEdition edition = edition();
        AwardCandidate coach = coach(edition);

        AwardCoachInvite invite = new AwardCoachInvite(
                edition.getId(),
                coach.getId(),
                "hashed-token",
                Instant.parse("2026-10-16T22:00:00Z")
        );

        when(currentUser.userId()).thenReturn(UUID.randomUUID());
        when(tokens.hash("raw-token")).thenReturn("hashed-token");
        when(invites.findByTokenHashForUpdate("hashed-token"))
                .thenReturn(Optional.of(invite));
        when(editions.findById(edition.getId()))
                .thenReturn(Optional.of(edition));
        when(candidates.findByIdAndEditionId(
                coach.getId(),
                edition.getId()
        )).thenReturn(Optional.of(coach));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.claim(
                        new ClaimCoachInviteRequest(
                                "raw-token",
                                904L,
                                3L,
                                7L,
                                "OUTRO-TIME"
                        )
                )
        ).hasMessageContaining(
                "não correspondem ao convite"
        );

        verify(voters, never())
                .saveAndFlush(any(AwardCoachVoter.class));
    }

    private AwardEdition edition() {
        return new AwardEdition(
                "premio-dna-2026",
                "Prêmio DNA Futsal 2026",
                2026,
                AwardEditionStatus.DRAFT,
                null,
                null
        );
    }

    private AwardCandidate coach(AwardEdition edition) {
        return new AwardCandidate(
                edition.getId(),
                AwardCandidateType.COACH,
                "coach-1",
                "Carlos Silva",
                null,
                904L,
                3L,
                7L,
                "CORINTHIANS",
                "Corinthians",
                null,
                null
        );
    }
}

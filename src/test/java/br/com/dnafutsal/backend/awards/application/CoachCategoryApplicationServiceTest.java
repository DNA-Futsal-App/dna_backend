package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardEditionStatus;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.BusinessException;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachCategoryApplicationServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock CoachCategoryLinkService links;
    @Mock SportsCatalogService catalog;
    @Mock AwardGoldEligibilityService gold;
    @Mock AwardEditionRepository editions;
    @Mock AwardCoachCredentialRepository credentials;
    @Mock AwardCoachVoterRepository contexts;
    @Mock AwardLiveRosterService rosters;
    @Mock UserAccountRepository users;
    @Mock CurrentUserService currentUser;

    private final Instant now = Instant.parse("2026-11-12T12:00:00Z");
    private final UUID editionId = UUID.randomUUID();
    private final UUID linkId = UUID.randomUUID();
    private CoachCategoryApplicationService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        service = new CoachCategoryApplicationService(jdbc, links, catalog, gold,
                editions, credentials, contexts, rosters, users, currentUser,
                Clock.fixed(now, ZoneOffset.UTC));
        user = new UserAccount("Treinador Teste", "treinador@example.com", "11999999999",
                "hash", null, null, null, null, null);
    }

    private void validInvite() {
        when(links.inspect("token-para-categoria-com-primeiro-uso"))
                .thenReturn(new CoachCategoryLinkService.LinkView(linkId, editionId,
                        917L, 3L, 7L, "MALE", "AVAILABLE", true,
                        now.plusSeconds(3600), now, null));
        when(catalog.teams(917L)).thenReturn(List.of(new TeamView("10", "Clube X", null, null)));
        lenient().when(editions.findById(editionId)).thenReturn(Optional.of(
                new AwardEdition("premio-dna-2026", "Prêmio DNA", 2026,
                        AwardEditionStatus.DRAFT, null, null)));
    }

    private void allowCoachVotingContext() {
        when(gold.eligibleTeamIds(editionId, 917L, 3L, 7L))
                .thenReturn(Set.of("11"));
        var credential = new br.com.dnafutsal.backend.awards.domain.AwardCoachCredential(
                editionId, user.getId(), null);
        when(credentials.findByEditionIdAndUserId(editionId, user.getId()))
                .thenReturn(Optional.of(credential));
        when(contexts.findByCredentialIdAndActiveTrueOrderByCreatedAtAsc(credential.getId()))
                .thenReturn(List.of());
        var ownTeam = mock(br.com.dnafutsal.backend.awards.domain.AwardCandidate.class);
        when(ownTeam.getId()).thenReturn(UUID.randomUUID());
        when(rosters.coachTeamVote(editionId, 917L, 3L, 7L, "10")).thenReturn(ownTeam);
    }

    @Test
    void newRegistrationImmediatelyApprovesWithoutEvidenceOrEmailVerification() {
        validInvite();
        allowCoachVotingContext();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        var result = service.applyForNewUser(user, "token-para-categoria-com-primeiro-uso",
                "10", "Treinador principal", "https://fpfs.example/prova");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.evidenceUrl()).isNull();
        assertThat(result.teamId()).isEqualTo("10");
        assertThat(user.getStatus()).isEqualTo(br.com.dnafutsal.backend.identity.domain.UserStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(br.com.dnafutsal.backend.identity.domain.UserRole.COACH);
        assertThat(user.getEmailVerifiedAt()).isNull();
        var ordered = inOrder(users, jdbc, contexts);
        ordered.verify(users).flush();
        ordered.verify(jdbc).update(contains("INSERT INTO award_coach_category_applications"), any(Object[].class));
        ordered.verify(contexts).saveAndFlush(any(br.com.dnafutsal.backend.awards.domain.AwardCoachVoter.class));
        ordered.verify(users).saveAndFlush(user);
    }

    @ParameterizedTest
    @CsvSource({
            "23505, uk_coach_category_app_active_context, 409, COACH_CATEGORY_ALREADY_REQUESTED",
            "23505, award_coach_category_applications_pkey, 500, COACH_CATEGORY_PERSISTENCE_ERROR",
            "23503, award_coach_category_applications_user_id_fkey, 500, COACH_CATEGORY_PERSISTENCE_ERROR",
            "23514, ck_coach_category_app_review, 500, COACH_CATEGORY_PERSISTENCE_ERROR",
            "23502, , 500, COACH_CATEGORY_PERSISTENCE_ERROR"
    })
    void registrationDistinguishesActualDuplicateFromOtherIntegrityFailures(
            String sqlState, String constraint, int expectedStatus, String expectedCode) {
        validInvite();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        var sql = mock(PSQLException.class);
        var server = mock(ServerErrorMessage.class);
        when(sql.getSQLState()).thenReturn(sqlState);
        when(sql.getServerErrorMessage()).thenReturn(server);
        when(server.getConstraint()).thenReturn(constraint);
        when(jdbc.update(contains("INSERT INTO award_coach_category_applications"), any(Object[].class)))
                .thenThrow(new DataIntegrityViolationException("database detail must not leak", sql));

        assertThatThrownBy(() -> service.applyForNewUser(user,
                "token-para-categoria-com-primeiro-uso", "10", "Treinador", null))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.code()).isEqualTo(expectedCode);
                    assertThat(error.status().value()).isEqualTo(expectedStatus);
                    assertThat(error.diagnostics()).containsEntry("sqlState", sqlState)
                            .containsEntry("constraint", constraint == null ? "UNKNOWN" : constraint);
                    assertThat(error.getMessage()).doesNotContain("database detail");
                    assertThat(error.getCause()).isNull();
                });
        verify(users).flush();
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(credentials, contexts, rosters);
        assertThat(user.getRole()).isEqualTo(br.com.dnafutsal.backend.identity.domain.UserRole.USER);
    }

    @Test
    void unknownIntegrityFailureIsNotReportedAsDuplicate() {
        validInvite();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(jdbc.update(contains("INSERT INTO award_coach_category_applications"), any(Object[].class)))
                .thenThrow(new DataIntegrityViolationException("unclassified integrity failure"));
        assertThatThrownBy(() -> service.applyForNewUser(user,
                "token-para-categoria-com-primeiro-uso", "10", "Treinador", null))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.code()).isEqualTo("COACH_CATEGORY_PERSISTENCE_ERROR");
                    assertThat(error.status().value()).isEqualTo(500);
                    assertThat(error.diagnostics()).containsEntry("sqlState", "UNKNOWN");
                });
        verifyNoInteractions(credentials, contexts, rosters);
    }

    @Test
    void pendingAccountLoginWithValidInvitationBecomesActiveCoach() {
        user.requireEmailVerification();
        validInvite();
        allowCoachVotingContext();
        var result = service.applyForInvitedLogin(user,
                "token-para-categoria-com-primeiro-uso", "10", "Treinador principal");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(user.getStatus()).isEqualTo(br.com.dnafutsal.backend.identity.domain.UserStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(br.com.dnafutsal.backend.identity.domain.UserRole.COACH);
        assertThat(user.getEmailVerifiedAt()).isNull();
    }

    @Test
    void revokedInvitationCannotActivatePendingAccount() {
        user.requireEmailVerification();
        when(links.inspect("token-para-categoria-com-primeiro-uso"))
                .thenReturn(new CoachCategoryLinkService.LinkView(linkId, editionId,
                        917L, 3L, 7L, "MALE", "REVOKED", false,
                        now.minusSeconds(1), now, null));
        assertThatThrownBy(() -> service.applyForInvitedLogin(user,
                "token-para-categoria-com-primeiro-uso", "10", "Treinador"))
                .isInstanceOf(BusinessException.class);
        assertThat(user.getStatus()).isEqualTo(
                br.com.dnafutsal.backend.identity.domain.UserStatus.PENDING_EMAIL_VERIFICATION);
        assertThat(user.getRole()).isEqualTo(br.com.dnafutsal.backend.identity.domain.UserRole.USER);
        verifyNoInteractions(credentials, contexts, rosters);
    }

    @Test
    void invalidTeamCannotBeUsedEvenWithValidToken() {
        validInvite();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.applyForNewUser(user,
                "token-para-categoria-com-primeiro-uso", "999", "Treinador", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pertence");
        verifyNoInteractions(credentials, contexts, rosters);
    }

    @Test
    void expiredTokenDoesNotAllowApplications() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(links.inspect("token-para-categoria-com-primeiro-uso"))
                .thenReturn(new CoachCategoryLinkService.LinkView(linkId, editionId,
                        917L, 3L, 7L, "MALE", "EXPIRED", false, now.minusSeconds(1), now, null));
        assertThatThrownBy(() -> service.applyForNewUser(user,
                "token-para-categoria-com-primeiro-uso", "10", "Treinador", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("convite");
    }
    @Test
    void rejectsActiveAccountWhenThereAreNoGoldTeams() {
        validInvite();
        when(currentUser.userId()).thenReturn(user.getId());
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(gold.eligibleTeamIds(editionId, 917L, 3L, 7L))
                .thenReturn(Set.of());

        assertThatThrownBy(() -> service.applyCurrentUser(
                "token-para-categoria-com-primeiro-uso", "10", "Treinador", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chave Ouro");
        verifyNoInteractions(credentials, contexts, rosters);
    }

    @Test
    void existingActiveAccountIsAutomaticallyEnrolledAndGetsCoachRole() {
        validInvite();
        when(currentUser.userId()).thenReturn(user.getId());
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        var credential = new br.com.dnafutsal.backend.awards.domain.AwardCoachCredential(
                editionId, user.getId(), null);
        when(credentials.findByEditionIdAndUserId(editionId, user.getId()))
                .thenReturn(Optional.of(credential));
        when(contexts.findByCredentialIdAndActiveTrueOrderByCreatedAtAsc(credential.getId()))
                .thenReturn(List.of());
        var ownTeam = mock(br.com.dnafutsal.backend.awards.domain.AwardCandidate.class);
        when(ownTeam.getId()).thenReturn(UUID.randomUUID());
        when(rosters.coachTeamVote(editionId, 917L, 3L, 7L, "10")).thenReturn(ownTeam);
        when(gold.eligibleTeamIds(editionId, 917L, 3L, 7L))
                .thenReturn(Set.of("11"));

        var application = service.applyCurrentUser("token-para-categoria-com-primeiro-uso",
                "10", "Treinador", null);

        assertThat(application.status()).isEqualTo("APPROVED");
        assertThat(user.getRole()).isEqualTo(
                br.com.dnafutsal.backend.identity.domain.UserRole.COACH);
        verify(contexts).saveAndFlush(any(br.com.dnafutsal.backend.awards.domain.AwardCoachVoter.class));
        verify(users).saveAndFlush(user);
    }

    @Test
    void reactivatingOneContextDoesNotDeleteAnotherContextBallot() {
        UUID contextId = UUID.randomUUID();
        UUID otherContextId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        var credential = new br.com.dnafutsal.backend.awards.domain.AwardCoachCredential(
                editionId, user.getId(), null);
        var context = mock(br.com.dnafutsal.backend.awards.domain.AwardCoachVoter.class);
        when(context.getCredentialId()).thenReturn(credential.getId());
        when(context.getEditionId()).thenReturn(editionId);
        when(context.getUserId()).thenReturn(user.getId());
        when(context.isActive()).thenReturn(false);
        when(context.getEventId()).thenReturn(917L);
        when(context.getDivisionId()).thenReturn(3L);
        when(context.getCategoryId()).thenReturn(7L);
        when(context.getTeamId()).thenReturn("10");
        when(credentials.findByIdAndActiveTrue(credential.getId()))
                .thenReturn(Optional.of(credential));
        when(contexts.findById(contextId)).thenReturn(Optional.of(context));
        when(editions.findById(editionId)).thenReturn(Optional.of(new AwardEdition(
                "premio-dna-2026", "Prêmio DNA", 2026,
                AwardEditionStatus.OPEN, now.minusSeconds(60), now.plusSeconds(3600))));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(jdbc.query(contains("active=FALSE FOR UPDATE"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                eq(contextId), eq(credential.getId()))).thenReturn(List.of(contextId));
        when(jdbc.query(contains("status='REVOKED'"),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                eq(editionId), eq(user.getId()), eq(917L), eq(3L), eq(7L), eq("10")))
                .thenReturn(List.of(appId));
        // The reactivation service checks duplicate active contexts and existing
        // authorizations before updating this exact revoked category.
        when(jdbc.queryForObject(contains("WHERE edition_id=? AND user_id=? AND event_id=?"),
                eq(Boolean.class), eq(editionId), eq(user.getId()),
                eq(917L), eq(3L), eq(7L))).thenReturn(false);
        when(jdbc.queryForObject(contains("SELECT EXISTS(SELECT 1 FROM award_coach_category_applications"),
                eq(Boolean.class), eq(editionId), eq(user.getId()),
                eq(917L), eq(3L), eq(7L))).thenReturn(false);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM award_ballot_votes"),
                eq(Long.class), eq(contextId))).thenReturn(0L);
        // JdbcTemplate.update is also called for ballot cleanup, restore-snapshot cleanup
        // and audit writes. Only these two updates need non-zero mock results;
        // keep the remaining calls unstubbed rather than causing strict mismatches.
        lenient().when(jdbc.update(contains("UPDATE award_coach_category_applications"),
                any(java.sql.Timestamp.class), eq(adminId), eq(appId))).thenReturn(1);
        lenient().when(jdbc.update(contains("UPDATE award_coach_voters"),
                eq(false), eq(contextId), eq(credential.getId()))).thenReturn(1);
        when(jdbc.queryForObject(contains("SELECT EXISTS(SELECT 1 FROM award_coach_voters v"),
                eq(Boolean.class), eq(user.getId()))).thenReturn(true);
        when(currentUser.userId()).thenReturn(adminId);

        var result = service.reactivateContext(credential.getId(), contextId);

        assertThat(result.contextId()).isEqualTo(contextId);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.ballotsDeleted()).isZero();
        assertThat(result.votesDeleted()).isZero();
        assertThat(user.getRole()).isEqualTo(br.com.dnafutsal.backend.identity.domain.UserRole.COACH);
        verify(jdbc).update("DELETE FROM award_ballots WHERE coach_voter_id = ?", contextId);
        verify(jdbc, never()).update("DELETE FROM award_ballots WHERE coach_voter_id = ?", otherContextId);
    }

    @Test
    void cannotReactivateCategoryWhenEntireCoachCredentialIsInactive() {
        UUID credentialId = UUID.randomUUID();
        UUID contextId = UUID.randomUUID();
        assertThatThrownBy(() -> service.reactivateContext(credentialId, contextId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reabilite o treinador");
        verifyNoInteractions(jdbc);
    }

}

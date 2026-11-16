package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Category invitations grant access automatically after email verification.
 * Administrative revocation removes the category context AND its ballot atomically.
 */
@Service
public class CoachCategoryApplicationService {
    private final JdbcTemplate jdbc;
    private final CoachCategoryLinkService links;
    private final SportsCatalogService catalog;
    private final AwardGoldEligibilityService gold;
    private final AwardEditionRepository editions;
    private final AwardCoachCredentialRepository credentials;
    private final AwardCoachVoterRepository contexts;
    private final AwardLiveRosterService rosters;
    private final UserAccountRepository users;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public CoachCategoryApplicationService(JdbcTemplate jdbc, CoachCategoryLinkService links,
            SportsCatalogService catalog, AwardGoldEligibilityService gold,
            AwardEditionRepository editions, AwardCoachCredentialRepository credentials,
            AwardCoachVoterRepository contexts, AwardLiveRosterService rosters,
            UserAccountRepository users, CurrentUserService currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.links = links;
        this.catalog = catalog;
        this.gold = gold;
        this.editions = editions;
        this.credentials = credentials;
        this.contexts = contexts;
        this.rosters = rosters;
        this.users = users;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record ApplicationView(UUID id, UUID editionId, UUID userId, String coachName,
            String email, long eventId, long divisionId, long categoryId, String gender,
            String teamId, String teamName, String coachRole, String evidenceUrl,
            String status, String reviewReason, Instant createdAt, Instant reviewedAt) { }

    public record ExtraCategoryRequest(long eventId, long divisionId, long categoryId,
                                        AwardRegistrationGender gender, String teamId) { }

    public record RevocationResult(UUID editionId, UUID userId, int categoriesRevoked,
                                   int ballotsDeleted, int votesDeleted) { }

    private record ApplicationRow(UUID id, UUID editionId, UUID userId, UUID linkId,
            long eventId, long divisionId, long categoryId, String gender,
            String teamId, String teamName, String coachRole, String evidenceUrl,
            String status, String reviewReason, Instant createdAt, Instant reviewedAt) { }

    @Transactional(readOnly = true)
    public List<TeamView> teams(String token) {
        return catalog.teams(usableLink(token).eventId());
    }

    @Transactional
    public ApplicationView applyCurrentUser(String token, String teamId, String role, String evidenceUrl) {
        UserAccount user = users.findByIdForUpdate(currentUser.userId()).orElseThrow(() ->
                Errors.unauthorized("ACCOUNT_UNAVAILABLE", "Conta não encontrada."));
        requireActive(user);
        return apply(user, token, teamId, role, evidenceUrl, true);
    }

    /** During registration, email must be verified before voting becomes available. */
    @Transactional
    public ApplicationView applyForNewUser(UserAccount user, String token, String teamId,
                                            String role, String evidenceUrl) {
        UserAccount managed = users.findById(user.getId()).orElseThrow(() ->
                Errors.conflict("COACH_CATEGORY_USER_MISSING", "Conta recém-criada não encontrada."));
        managed.requireEmailVerification();
        users.flush();
        return apply(managed, token, teamId, role, evidenceUrl, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activateVerifiedUser(UUID userId) {
        // The email verification transaction has already committed. A temporary
        // FPFS outage cannot roll back the account's verified email.
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(() ->
                Errors.notFound("COACH_CATEGORY_USER_MISSING", "Conta não encontrada."));
        requireActive(user);
        activatePendingForUser(user);
    }

    private void activatePendingForUser(UserAccount user) {
        List<ApplicationRow> pending = jdbc.query("""
            SELECT id, link_id, edition_id, user_id, event_id, division_id, category_id,
                   gender, team_id, team_name, coach_role, evidence_url, status,
                   review_reason, created_at, reviewed_at
              FROM award_coach_category_applications
             WHERE user_id = ? AND status = 'PENDING'
             ORDER BY created_at, id FOR UPDATE
            """, (rs, row) -> map(rs), user.getId());
        boolean activated = false;
        for (ApplicationRow app : pending) {
            if (requireEdition(app.editionId()).isClosed()) {
                // A closed edition cannot create a new voting context.
                continue;
            }
            createVotingContext(user, app.editionId(), app.eventId(), app.divisionId(),
                    app.categoryId(), app.teamId());
            activated = true;
            jdbc.update("""
                UPDATE award_coach_category_applications
                   SET status = 'APPROVED', review_reason = 'AUTO_VERIFIED', reviewed_at = ?
                 WHERE id = ? AND status = 'PENDING'
                """, Timestamp.from(clock.instant()), app.id());
        }
        if (activated) {
            user.promoteToCoach();
            users.saveAndFlush(user);
        }
    }

    private ApplicationView apply(UserAccount user, String token, String teamId,
                                  String role, String evidenceUrl, boolean verified) {
        CoachCategoryLinkService.LinkView link = usableLink(token);
        String selectedTeam = teamId == null ? "" : teamId.trim();
        String selectedRole = role == null ? "" : role.trim();
        if (selectedTeam.isBlank() || selectedTeam.length() > 100
                || selectedRole.isBlank() || selectedRole.length() > 80) {
            throw Errors.badRequest("COACH_CATEGORY_DATA_INVALID", "Informe sua equipe e sua função técnica.");
        }
        String evidence = evidenceUrl == null || evidenceUrl.isBlank() ? null : evidenceUrl.trim();
        if (evidence != null && (evidence.length() > 500
                || !(evidence.startsWith("https://") || evidence.startsWith("http://")))) {
            throw Errors.badRequest("COACH_CATEGORY_EVIDENCE_INVALID", "Informe um endereço HTTP(S) válido para comprovação.");
        }
        TeamView team = catalog.teams(link.eventId()).stream()
                .filter(t -> selectedTeam.equals(t.id())).findFirst()
                .orElseThrow(() -> Errors.badRequest("COACH_CATEGORY_TEAM_INVALID",
                        "A equipe informada não pertence à categoria do convite."));
        if (requireEdition(link.editionId()).isClosed()) {
            throw Errors.conflict("COACH_CATEGORY_EDITION_CLOSED", "A edição foi encerrada.");
        }
        // Only one active context per user/category, even if the coach has another invitation.
        if (hasVotingContext(user.getId(), link.editionId(), link.eventId(),
                link.divisionId(), link.categoryId())) {
            throw Errors.conflict("COACH_CATEGORY_ALREADY_REQUESTED",
                    "Este treinador já possui acesso a esta categoria.");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String status = verified ? "APPROVED" : "PENDING";
        try {
            jdbc.update("""
                INSERT INTO award_coach_category_applications
                    (id, link_id, edition_id, user_id, event_id, division_id, category_id,
                     gender, team_id, team_name, coach_role, evidence_url,
                     status, review_reason, reviewed_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, link.linkId(), link.editionId(), user.getId(), link.eventId(),
                    link.divisionId(), link.categoryId(), link.gender(), selectedTeam, team.name(),
                    selectedRole, evidence, status, verified ? "AUTO_INVITE" : null,
                    verified ? Timestamp.from(now) : null, Timestamp.from(now));
        } catch (DataIntegrityViolationException ex) {
            throw Errors.conflict("COACH_CATEGORY_ALREADY_REQUESTED",
                    "Já existe uma inscrição ativa para esta categoria.");
        }
        if (verified) {
            createVotingContext(user, link.editionId(), link.eventId(), link.divisionId(),
                    link.categoryId(), selectedTeam);
            user.promoteToCoach();
            users.saveAndFlush(user);
        }
        return new ApplicationView(id, link.editionId(), user.getId(), user.getName(),
                user.getEmail(), link.eventId(), link.divisionId(), link.categoryId(), link.gender(),
                selectedTeam, team.name(), selectedRole, evidence, status,
                verified ? "AUTO_INVITE" : null, now, verified ? now : null);
    }

    private void createVotingContext(UserAccount user, UUID editionId, long eventId,
                                     long divisionId, long categoryId, String teamId) {
        // The coach can represent any team; only Gold teams can receive votes.
        if (gold.eligibleTeamIds(editionId, eventId, divisionId, categoryId).isEmpty()) {
            throw Errors.conflict("AWARD_GOLD_CLASSIFICATION_EMPTY",
                    "A categoria não possui equipes classificadas na Chave Ouro.");
        }
        AwardCoachCredential credential = credentials.findByEditionIdAndUserId(editionId, user.getId())
                .orElseGet(() -> credentials.saveAndFlush(
                        new AwardCoachCredential(editionId, user.getId(), null)));
        if (!credential.isActive()) {
            throw Errors.conflict("COACH_CATEGORY_CREDENTIAL_DISABLED", "O acesso deste treinador foi revogado.");
        }
        boolean duplicated = contexts.findByCredentialIdAndActiveTrueOrderByCreatedAtAsc(credential.getId())
                .stream().anyMatch(c -> c.getEventId() == eventId && c.getDivisionId() == divisionId
                        && c.getCategoryId() == categoryId);
        if (duplicated) {
            throw Errors.conflict("COACH_CATEGORY_CONTEXT_ALREADY_EXISTS",
                    "Este treinador já está credenciado nesta categoria.");
        }
        AwardCandidate ownTeam = rosters.coachTeamVote(editionId, eventId,
                divisionId, categoryId, teamId);
        contexts.saveAndFlush(new AwardCoachVoter(editionId, user.getId(), credential.getId(),
                ownTeam.getId(), eventId, divisionId, categoryId, teamId,
                AwardCoachVoterContextSource.SELF_SELECTED));
    }

    @Transactional
    public List<ApplicationView> mine() {
        UserAccount user = users.findByIdForUpdate(currentUser.userId()).orElseThrow(() ->
                Errors.unauthorized("ACCOUNT_UNAVAILABLE", "Conta não encontrada."));
        requireActive(user);
        // Recovers registrations completed under the previous approval-based implementation.
        activatePendingForUser(user);
        return views("WHERE a.user_id = ?", user.getId());
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> adminList(UUID editionId, String status) {
        requireEdition(editionId);
        if (status == null || status.isBlank()) return views("WHERE a.edition_id = ?", editionId);
        if (!List.of("PENDING", "APPROVED", "REJECTED", "REVOKED").contains(status)) {
            throw Errors.badRequest("COACH_CATEGORY_STATUS_INVALID", "Situação de credenciamento inválida.");
        }
        return views("WHERE a.edition_id = ? AND a.status = ?", editionId, status);
    }

    /** The old admin screen also creates a properly authorized category application. */
    @Transactional
    public ApplicationView adminAddCategoryFromLegacy(UUID credentialId,
                        br.com.dnafutsal.backend.awards.api.CreateCoachContextRequest request) {
        if (request == null || !credentialId.equals(request.credentialId())) {
            throw Errors.badRequest("COACH_CREDENTIAL_MISMATCH", "Credencial inválida.");
        }
        AwardCoachCredential credential = credentials.findByIdAndActiveTrue(credentialId)
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND", "Credencial não encontrada."));
        AwardEdition edition = requireEdition(credential.getEditionId());
        AwardRegistrationGender gender = request.gender();
        if (gender == null) {
            // Compatibility with older clients that do not send gender.
            List<AwardRegistrationGender> matches = java.util.Arrays.stream(AwardRegistrationGender.values())
                    .filter(value -> catalog.categories(edition.getSeason(), value.catalogTitle(),
                            request.divisionId()).stream().anyMatch(category ->
                            category.id() == request.categoryId() && category.eventId() == request.eventId()))
                    .toList();
            if (matches.size() != 1) {
                throw Errors.badRequest("COACH_CATEGORY_GENDER_REQUIRED",
                        "Selecione a modalidade Masculino ou Feminino para liberar esta categoria.");
            }
            gender = matches.get(0);
        }
        // adminAddCategory validates that eventId/categoryId really belong to gender.
        return adminAddCategory(credential.getEditionId(), credentialId,
                new ExtraCategoryRequest(request.eventId(), request.divisionId(),
                        request.categoryId(), gender, request.teamId()));
    }

    /** Adds an extra category directly, with no invitation and no manual approval. */
    @Transactional
    public ApplicationView adminAddCategory(UUID editionId, UUID credentialId,
                                            ExtraCategoryRequest request) {
        AwardEdition edition = requireEdition(editionId);
        if (edition.isClosed()) throw Errors.conflict("COACH_CATEGORY_EDITION_CLOSED", "Edição encerrada.");
        if (request == null || request.gender() == null || request.teamId() == null
                || request.teamId().isBlank() || request.eventId() <= 0
                || request.divisionId() <= 0 || request.categoryId() <= 0) {
            throw Errors.badRequest("COACH_CATEGORY_INVALID", "Informe categoria, modalidade e clube.");
        }
        AwardCoachCredential credential = credentials.findByIdAndActiveTrue(credentialId)
                .filter(c -> c.getEditionId().equals(editionId))
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND", "Credencial não encontrada."));
        UserAccount user = users.findByIdForUpdate(credential.getUserId()).orElseThrow(() ->
                Errors.notFound("COACH_CATEGORY_USER_MISSING", "Conta não encontrada."));
        requireActive(user);
        boolean categoryValid = catalog.categories(edition.getSeason(), request.gender().catalogTitle(),
                request.divisionId()).stream().anyMatch(c -> c.id() == request.categoryId()
                        && c.eventId() == request.eventId());
        if (!categoryValid) {
            throw Errors.badRequest("COACH_CATEGORY_CONTEXT_INVALID", "Categoria não pertence à modalidade selecionada.");
        }
        TeamView team = catalog.teams(request.eventId()).stream()
                .filter(t -> request.teamId().trim().equals(t.id())).findFirst()
                .orElseThrow(() -> Errors.badRequest("COACH_CATEGORY_TEAM_INVALID", "Equipe não pertence ao evento."));
        if (hasVotingContext(user.getId(), editionId, request.eventId(),
                request.divisionId(), request.categoryId())) {
            throw Errors.conflict("COACH_CATEGORY_ALREADY_REQUESTED", "Treinador já possui esta categoria.");
        }
        UUID id = UUID.randomUUID();
        UUID adminId = currentUser.userId();
        Instant now = clock.instant();
        createVotingContext(user, editionId, request.eventId(), request.divisionId(),
                request.categoryId(), team.id());
        try {
            jdbc.update("""
                INSERT INTO award_coach_category_applications
                    (id, link_id, edition_id, user_id, event_id, division_id, category_id,
                     gender, team_id, team_name, coach_role, status, review_reason,
                     reviewed_by_user_id, reviewed_at, created_at)
                VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, 'TREINADOR',
                        'APPROVED', 'ADMIN_EXTRA', ?, ?, ?)
                """, id, editionId, user.getId(), request.eventId(), request.divisionId(),
                    request.categoryId(), request.gender().name(), team.id(), team.name(),
                    adminId, Timestamp.from(now), Timestamp.from(now));
        } catch (DataIntegrityViolationException ex) {
            throw Errors.conflict("COACH_CATEGORY_ALREADY_REQUESTED", "Já existe inscrição nesta categoria.");
        }
        user.promoteToCoach();
        users.saveAndFlush(user);
        return new ApplicationView(id, editionId, user.getId(), user.getName(), user.getEmail(),
                request.eventId(), request.divisionId(), request.categoryId(), request.gender().name(),
                team.id(), team.name(), "TREINADOR", null, "APPROVED", "ADMIN_EXTRA", now, now);
    }

    /** Removes an individual category and deletes only ballots cast under that context. */
    @Transactional
    public RevocationResult revoke(UUID editionId, UUID applicationId, String reason) {
        UUID userId = jdbc.query("""
            SELECT user_id FROM award_coach_category_applications WHERE edition_id = ? AND id = ?
            """, (rs, row) -> rs.getObject("user_id", UUID.class), editionId, applicationId)
                .stream().findFirst().orElseThrow(() -> Errors.notFound(
                        "COACH_CATEGORY_APPLICATION_NOT_FOUND", "Credenciamento não encontrado."));
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(() ->
                Errors.notFound("COACH_CATEGORY_USER_MISSING", "Conta não encontrada."));
        ApplicationRow app = lock(editionId, applicationId);
        if (!List.of("PENDING", "APPROVED").contains(app.status())) {
            throw Errors.conflict("COACH_CATEGORY_ALREADY_REVOKED", "Credenciamento já está encerrado.");
        }
        Counters counts = revokeRow(app, reason, currentUser.userId());
        updateCoachRole(user);
        return new RevocationResult(editionId, userId, 1, counts.ballots(), counts.votes());
    }

    /** Removes every category for one coach in the edition, including any legacy contexts. */
    @Transactional
    public RevocationResult revokeCoach(UUID editionId, UUID credentialId, String reason) {
        requireEdition(editionId);
        AwardCoachCredential credential = credentials.findByIdAndActiveTrue(credentialId)
                .filter(c -> c.getEditionId().equals(editionId))
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND", "Credencial não encontrada."));
        UserAccount user = users.findByIdForUpdate(credential.getUserId()).orElseThrow(() ->
                Errors.notFound("COACH_CATEGORY_USER_MISSING", "Conta não encontrada."));
        // Snapshot only contexts that are active at coach deactivation. Restoring a coach
        // must not revive contexts that were revoked separately in the past.
        jdbc.update("""
            INSERT INTO award_coach_credential_restore_contexts
                (credential_id, context_id, application_id)
            SELECT v.credential_id, v.id,
                   (SELECT a.id FROM award_coach_category_applications a
                     WHERE a.edition_id=v.edition_id AND a.user_id=v.user_id
                       AND a.event_id=v.event_id AND a.division_id=v.division_id
                       AND a.category_id=v.category_id AND a.team_id=v.team_id
                       AND a.status='APPROVED'
                     ORDER BY a.created_at DESC, a.id DESC LIMIT 1)
              FROM award_coach_voters v
             WHERE v.credential_id=? AND v.active=TRUE
            ON CONFLICT (credential_id, context_id) DO NOTHING
            """, credentialId);
        List<ApplicationRow> apps = jdbc.query("""
            SELECT id, link_id, edition_id, user_id, event_id, division_id, category_id,
                   gender, team_id, team_name, coach_role, evidence_url, status,
                   review_reason, created_at, reviewed_at
              FROM award_coach_category_applications
             WHERE edition_id = ? AND user_id = ? AND status IN ('APPROVED','PENDING')
             ORDER BY id FOR UPDATE
            """, (rs, row) -> map(rs), editionId, credential.getUserId());
        int ballots = 0, votes = 0;
        for (ApplicationRow app : apps) {
            Counters result = revokeRow(app, reason, currentUser.userId());
            ballots += result.ballots();
            votes += result.votes();
        }
        // Older V1/V2 contexts do not have a category application row.
        List<UUID> remainingContexts = jdbc.query("""
            SELECT id FROM award_coach_voters
             WHERE credential_id = ?
             ORDER BY id FOR UPDATE
            """, (rs, row) -> rs.getObject("id", UUID.class), credentialId);
        for (UUID id : remainingContexts) {
            Counters result = removeBallot(id);
            ballots += result.ballots();
            votes += result.votes();
            jdbc.update("UPDATE award_coach_voters SET active = FALSE WHERE id = ?", id);
        }
        jdbc.update("UPDATE award_coach_credentials SET active = FALSE WHERE id = ?", credentialId);
        audit(editionId, user.getId(), credentialId, "COACH_REVOKED", reason,
                currentUser.userId(), ballots, votes);
        updateCoachRole(user);
        return new RevocationResult(editionId, user.getId(), apps.size(), ballots, votes);
    }

    /** Ensures legacy administrative actions obey the same vote-deletion rule. */
    @Transactional
    public RevocationResult revokeByCredential(UUID credentialId, String reason) {
        AwardCoachCredential credential = credentials.findByIdAndActiveTrue(credentialId)
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND", "Credencial não encontrada."));
        return revokeCoach(credential.getEditionId(), credentialId, reason);
    }

    /** Legacy context deactivation must delete its votes and revoke any linked application. */
    @Transactional
    public RevocationResult revokeByContext(UUID credentialId, UUID contextId, String reason) {
        AwardCoachCredential credential = credentials.findByIdAndActiveTrue(credentialId)
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND", "Credencial não encontrada."));
        AwardCoachVoter context = contexts.findByIdAndCredentialIdAndActiveTrue(contextId, credentialId)
                .orElseThrow(() -> Errors.notFound("COACH_CONTEXT_NOT_FOUND", "Contexto não encontrado."));
        UserAccount user = users.findByIdForUpdate(credential.getUserId()).orElseThrow(() ->
                Errors.notFound("COACH_CATEGORY_USER_MISSING", "Conta não encontrada."));
        List<ApplicationRow> matching = jdbc.query("""
            SELECT id, link_id, edition_id, user_id, event_id, division_id, category_id,
                   gender, team_id, team_name, coach_role, evidence_url, status,
                   review_reason, created_at, reviewed_at
              FROM award_coach_category_applications
             WHERE edition_id=? AND user_id=? AND event_id=? AND division_id=?
               AND category_id=? AND team_id=? AND status IN ('APPROVED','PENDING')
             ORDER BY id FOR UPDATE
            """, (rs, row) -> map(rs), credential.getEditionId(), user.getId(),
                context.getEventId(), context.getDivisionId(), context.getCategoryId(),
                context.getTeamId());
        int ballots = 0, votes = 0;
        if (matching.isEmpty()) {
            List<UUID> locked = jdbc.query("""
                SELECT id FROM award_coach_voters WHERE id=? FOR UPDATE
                """, (rs, row) -> rs.getObject("id", UUID.class), contextId);
            if (locked.isEmpty()) {
                throw Errors.notFound("COACH_CONTEXT_NOT_FOUND", "Contexto não encontrado.");
            }
            Counters deleted = removeBallot(contextId);
            ballots = deleted.ballots(); votes = deleted.votes();
            jdbc.update("UPDATE award_coach_voters SET active=FALSE WHERE id=?", contextId);
            audit(credential.getEditionId(), user.getId(), contextId, "CATEGORY_REVOKED",
                    reason, currentUser.userId(), ballots, votes);
        } else {
            for (ApplicationRow app : matching) {
                Counters deleted = revokeRow(app, reason, currentUser.userId());
                ballots += deleted.ballots(); votes += deleted.votes();
            }
        }
        updateCoachRole(user);
        return new RevocationResult(credential.getEditionId(), user.getId(),
                matching.size(), ballots, votes);
    }

    public record CredentialLifecycleResult(UUID credentialId, String status, int contextsRestored) { }

    private record RestorableContext(UUID contextId, UUID applicationId) { }

    /** Re-enable only contexts that were active immediately before this credential was disabled. */
    @Transactional
    public CredentialLifecycleResult reactivateCredential(UUID credentialId) {
        AwardCoachCredential credential = credentials.findById(credentialId)
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND", "Credenciamento não encontrado."));
        if (credential.isRemoved()) {
            throw Errors.conflict("COACH_CREDENTIAL_REMOVED", "Este treinador foi removido do júri técnico.");
        }
        if (credential.isActive()) {
            throw Errors.conflict("COACH_CREDENTIAL_ALREADY_ACTIVE", "O treinador já está habilitado.");
        }
        UserAccount user = users.findByIdForUpdate(credential.getUserId()).orElseThrow(() ->
                Errors.notFound("COACH_CATEGORY_USER_MISSING", "Conta não encontrada."));
        // Serialize administrative changes with ballot submission and other revocations.
        jdbc.query("SELECT id FROM award_coach_credentials WHERE id=? FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), credentialId);
        List<RestorableContext> restore = jdbc.query("""
                SELECT context_id, application_id
                  FROM award_coach_credential_restore_contexts
                 WHERE credential_id=? ORDER BY context_id
                """, (rs, row) -> new RestorableContext(
                        rs.getObject("context_id", UUID.class),
                        rs.getObject("application_id", UUID.class)), credentialId);
        // Legacy credentials disabled before V32 have no snapshot. Reactivate only
        // the credential; the administrator must assign a category again.
        List<UUID> lockedContexts = jdbc.query(
                "SELECT id FROM award_coach_voters WHERE credential_id=? ORDER BY id FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), credentialId);
        // Older deactivation flows could leave a ballot behind. Re-enabling must
        // never bring back a previous vote.
        for (UUID contextId : lockedContexts) removeBallot(contextId);
        int restored = 0;
        for (RestorableContext entry : restore) {
            if (entry.applicationId() != null) {
                int updated = jdbc.update("""
                    UPDATE award_coach_category_applications
                       SET status='APPROVED', review_reason='ADMIN_REACTIVATED',
                           reviewed_at=?, reviewed_by_user_id=?
                     WHERE id=? AND status='REVOKED'
                    """, Timestamp.from(clock.instant()), currentUser.userId(), entry.applicationId());
                if (updated != 1) {
                    throw Errors.conflict("COACH_REACTIVATION_CONTEXT_CHANGED",
                            "A categoria mudou desde a desativação. Reavalie os vínculos antes de reabilitar.");
                }
            }
            restored += jdbc.update("""
                    UPDATE award_coach_voters SET active=TRUE
                     WHERE id=? AND credential_id=? AND active=FALSE
                    """, entry.contextId(), credentialId);
        }
        if (restored != restore.size()) {
            throw Errors.conflict("COACH_REACTIVATION_CONTEXT_CHANGED",
                    "Os vínculos do treinador mudaram desde a desativação.");
        }
        credential.reactivate();
        credentials.saveAndFlush(credential);
        if (restored > 0) {
            user.promoteToCoach();
            users.saveAndFlush(user);
        }
        jdbc.update("DELETE FROM award_coach_credential_restore_contexts WHERE credential_id=?", credentialId);
        audit(credential.getEditionId(), user.getId(), credentialId, "COACH_REACTIVATED",
                "Credenciamento reabilitado pela administração", currentUser.userId(), 0, 0);
        return new CredentialLifecycleResult(credentialId,
                restored > 0 ? "ACTIVE" : "ACTIVE_NO_CONTEXTS", restored);
    }

    /** Remove from jury administration, not from the user's DNA Futsal account. */
    @Transactional
    public CredentialLifecycleResult removeCredentialFromJury(UUID credentialId) {
        AwardCoachCredential credential = credentials.findById(credentialId)
                .orElseThrow(() -> Errors.notFound("COACH_CREDENTIAL_NOT_FOUND", "Credenciamento não encontrado."));
        if (credential.isRemoved()) {
            throw Errors.conflict("COACH_CREDENTIAL_REMOVED", "Este treinador já foi removido.");
        }
        if (credential.isActive()) {
            throw Errors.conflict("COACH_CREDENTIAL_STILL_ACTIVE",
                    "Desative o treinador e exclua seus votos antes de removê-lo do júri.");
        }
        UserAccount user = users.findByIdForUpdate(credential.getUserId()).orElseThrow(() ->
                Errors.notFound("COACH_CATEGORY_USER_MISSING", "Conta não encontrada."));
        jdbc.query("SELECT id FROM award_coach_credentials WHERE id=? FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), credentialId);
        List<UUID> oldContexts = jdbc.query(
                "SELECT id FROM award_coach_voters WHERE credential_id=? ORDER BY id FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), credentialId);
        int ballotsDeleted = 0, votesDeleted = 0;
        for (UUID contextId : oldContexts) {
            Counters removed = removeBallot(contextId);
            ballotsDeleted += removed.ballots();
            votesDeleted += removed.votes();
        }
        jdbc.update("UPDATE award_coach_voters SET active=FALSE WHERE credential_id=?", credentialId);
        credential.remove(clock.instant());
        credentials.saveAndFlush(credential);
        jdbc.update("DELETE FROM award_coach_credential_restore_contexts WHERE credential_id=?", credentialId);
        audit(credential.getEditionId(), user.getId(), credentialId, "COACH_REMOVED",
                "Credenciamento removido do júri pela administração", currentUser.userId(), ballotsDeleted, votesDeleted);
        updateCoachRole(user);
        return new CredentialLifecycleResult(credentialId, "REMOVED", 0);
    }

    private record Counters(int ballots, int votes) { }

    private Counters revokeRow(ApplicationRow app, String reason, UUID adminId) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw Errors.badRequest("COACH_REVOCATION_REASON_REQUIRED", "Informe o motivo (até 500 caracteres).");
        }
        List<UUID> contextIds = jdbc.query("""
            SELECT id FROM award_coach_voters
             WHERE edition_id = ? AND user_id = ? AND event_id = ? AND division_id = ?
               AND category_id = ?
             ORDER BY id FOR UPDATE
            """, (rs, row) -> rs.getObject("id", UUID.class), app.editionId(), app.userId(),
                app.eventId(), app.divisionId(), app.categoryId());
        int ballots = 0, votes = 0;
        for (UUID contextId : contextIds) {
            Counters result = removeBallot(contextId);
            ballots += result.ballots();
            votes += result.votes();
            jdbc.update("UPDATE award_coach_voters SET active = FALSE WHERE id = ?", contextId);
        }
        jdbc.update("""
            UPDATE award_coach_category_applications SET status='REVOKED', review_reason=?,
                   reviewed_by_user_id=?, reviewed_at=? WHERE id=?
            """, reason.trim(), adminId, Timestamp.from(clock.instant()), app.id());
        audit(app.editionId(), app.userId(), app.id(), "CATEGORY_REVOKED", reason,
                adminId, ballots, votes);
        return new Counters(ballots, votes);
    }

    private Counters removeBallot(UUID contextId) {
        Long voteCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM award_ballot_votes bv
              JOIN award_ballots b ON b.id = bv.ballot_id WHERE b.coach_voter_id = ?
            """, Long.class, contextId);
        int votes = Math.toIntExact(voteCount == null ? 0L : voteCount);
        int ballots = jdbc.update("DELETE FROM award_ballots WHERE coach_voter_id = ?", contextId);
        // FK award_ballot_votes.ballot_id ON DELETE CASCADE removes all votes.
        return new Counters(ballots, votes);
    }

    private void audit(UUID editionId, UUID userId, UUID targetId, String action,
                       String reason, UUID adminId, int ballots, int votes) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw Errors.badRequest("COACH_REVOCATION_REASON_REQUIRED", "Informe motivo com até 500 caracteres.");
        }
        jdbc.update("""
            INSERT INTO award_coach_revocation_audit
                (id, edition_id, user_id, target_id, action, reason, performed_by_user_id,
                 ballots_deleted, votes_deleted, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), editionId, userId, targetId, action,
                reason.trim(), adminId, ballots, votes, Timestamp.from(clock.instant()));
    }

    private void updateCoachRole(UserAccount user) {
        // Role follows a real active voting context, including legacy contexts
        // in another edition; stale approved applications alone are not access.
        Boolean otherActive = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM award_coach_voters v
                JOIN award_coach_credentials c ON c.id = v.credential_id
                WHERE v.user_id = ? AND v.active = TRUE AND c.active = TRUE
                  AND c.removed_at IS NULL)
            """, Boolean.class, user.getId());
        if (Boolean.TRUE.equals(otherActive)) user.promoteToCoach();
        else user.demoteFromCoach();
        users.saveAndFlush(user);
    }

    private boolean hasVotingContext(UUID userId, UUID editionId, long eventId,
                                     long divisionId, long categoryId) {
        Boolean exists = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM award_coach_voters
                          WHERE user_id=? AND edition_id=? AND event_id=?
                            AND division_id=? AND category_id=? AND active=TRUE)
            """, Boolean.class, userId, editionId, eventId, divisionId, categoryId);
        return Boolean.TRUE.equals(exists);
    }

    private ApplicationRow lock(UUID editionId, UUID applicationId) {
        return jdbc.query("""
            SELECT id, link_id, edition_id, user_id, event_id, division_id, category_id,
                   gender, team_id, team_name, coach_role, evidence_url, status,
                   review_reason, created_at, reviewed_at
              FROM award_coach_category_applications
             WHERE edition_id = ? AND id = ? FOR UPDATE
            """, (rs, row) -> map(rs), editionId, applicationId).stream().findFirst()
                .orElseThrow(() -> Errors.notFound("COACH_CATEGORY_APPLICATION_NOT_FOUND",
                        "Credenciamento não encontrado."));
    }

    private List<ApplicationView> views(String clause, Object... values) {
        String sql = """
            SELECT a.id, a.link_id, a.edition_id, a.user_id, a.event_id, a.division_id, a.category_id,
                   a.gender, a.team_id, a.team_name, a.coach_role, a.evidence_url, a.status,
                   a.review_reason, a.created_at, a.reviewed_at, u.name AS user_name, u.email AS user_email
              FROM award_coach_category_applications a
              JOIN user_accounts u ON u.id = a.user_id
            """ + clause + " ORDER BY a.created_at DESC, a.id";
        return jdbc.query(sql, (rs, row) -> {
            ApplicationRow a = map(rs);
            return new ApplicationView(a.id(), a.editionId(), a.userId(),
                    rs.getString("user_name"), rs.getString("user_email"), a.eventId(),
                    a.divisionId(), a.categoryId(), a.gender(), a.teamId(), a.teamName(),
                    a.coachRole(), a.evidenceUrl(), a.status(), a.reviewReason(),
                    a.createdAt(), a.reviewedAt());
        }, values);
    }

    private ApplicationRow map(ResultSet rs) throws SQLException {
        Timestamp reviewed = rs.getTimestamp("reviewed_at");
        return new ApplicationRow(rs.getObject("id", UUID.class), rs.getObject("edition_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getObject("link_id", UUID.class),
                rs.getLong("event_id"), rs.getLong("division_id"), rs.getLong("category_id"),
                rs.getString("gender"), rs.getString("team_id"), rs.getString("team_name"),
                rs.getString("coach_role"), rs.getString("evidence_url"), rs.getString("status"),
                rs.getString("review_reason"), rs.getTimestamp("created_at").toInstant(),
                reviewed == null ? null : reviewed.toInstant());
    }

    private CoachCategoryLinkService.LinkView usableLink(String token) {
        CoachCategoryLinkService.LinkView link = links.inspect(token);
        if (!link.available()) throw Errors.conflict("COACH_CATEGORY_LINK_UNAVAILABLE",
                "Este convite expirou, foi revogado ou a edição foi encerrada.");
        return link;
    }

    private AwardEdition requireEdition(UUID id) {
        return editions.findById(id).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
    }

    private void requireActive(UserAccount user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw Errors.conflict("COACH_CATEGORY_EMAIL_UNVERIFIED",
                    "Confirme seu e-mail antes de acessar o credenciamento.");
        }
    }
}

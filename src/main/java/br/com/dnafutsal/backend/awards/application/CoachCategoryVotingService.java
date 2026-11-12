package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.CoachBallotResponse;
import br.com.dnafutsal.backend.awards.api.CoachVotingCandidateResponse;
import br.com.dnafutsal.backend.awards.api.CoachVotingContextResponse;
import br.com.dnafutsal.backend.awards.api.SubmitCoachBallotV2Request;
import br.com.dnafutsal.backend.awards.domain.CoachVotingState;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phase 4: only administratively APPROVED category applications can use the new voting flow.
 * The existing V2 engine remains responsible for ballot uniqueness, voting windows,
 * candidate context checks and the frozen Chave Ouro eligibility rules.
 */
@Service
public class CoachCategoryVotingService {

    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUser;
    private final CoachVotingV2Service voting;
    private final Clock clock;

    public CoachCategoryVotingService(JdbcTemplate jdbc, CurrentUserService currentUser,
                                      CoachVotingV2Service voting, Clock clock) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.voting = voting;
        this.clock = clock;
    }

    public record ContextSummary(UUID contextId, UUID editionId, String editionName, int season,
                                 long eventId, long divisionId, long categoryId, String gender,
                                 String teamId, String teamName, CoachVotingState state,
                                 boolean submitted, Instant votingOpensAt, Instant votingClosesAt) { }

    @Transactional(readOnly = true)
    public List<ContextSummary> contexts() {
        UUID userId = currentUser.userId();
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT v.id AS context_id, e.id AS edition_id, e.name AS edition_name,
                       e.season, v.event_id, v.division_id, v.category_id, a.gender,
                       v.team_id, a.team_name, e.status AS edition_status,
                       e.voting_opens_at, e.voting_closes_at,
                       (b.id IS NOT NULL) AS submitted
                  FROM award_coach_category_applications a
                  JOIN award_coach_credentials c
                    ON c.edition_id = a.edition_id AND c.user_id = a.user_id AND c.active = TRUE
                  JOIN award_coach_voters v
                    ON v.credential_id = c.id AND v.edition_id = a.edition_id
                   AND v.user_id = a.user_id AND v.event_id = a.event_id
                   AND v.division_id = a.division_id AND v.category_id = a.category_id
                   AND v.team_id = a.team_id AND v.active = TRUE
                  JOIN award_editions e ON e.id = a.edition_id
                  JOIN user_accounts u ON u.id = a.user_id AND u.role IN ('COACH', 'ADMIN')
                  LEFT JOIN award_ballots b
                    ON b.edition_id = e.id AND b.coach_voter_id = v.id
                 WHERE a.user_id = ? AND a.status = 'APPROVED'
                 ORDER BY e.season DESC, a.created_at ASC
                """, (rs, rowNum) -> {
            boolean submitted = rs.getBoolean("submitted");
            Instant opensAt = instant(rs.getTimestamp("voting_opens_at"));
            Instant closesAt = instant(rs.getTimestamp("voting_closes_at"));
            CoachVotingState state = state(rs.getString("edition_status"),
                    opensAt, closesAt, submitted, now);
            return new ContextSummary(
                    rs.getObject("context_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("edition_name"), rs.getInt("season"),
                    rs.getLong("event_id"), rs.getLong("division_id"),
                    rs.getLong("category_id"), rs.getString("gender"),
                    rs.getString("team_id"), rs.getString("team_name"),
                    state, submitted, opensAt, closesAt);
        }, userId);
    }

    @Transactional(readOnly = true)
    public CoachVotingContextResponse context(UUID contextId) {
        requireApproved(contextId);
        return voting.context(contextId);
    }

    @Transactional
    public List<CoachVotingCandidateResponse> candidates(UUID contextId, UUID voteCategoryId,
                                                           String teamId) {
        requireApproved(contextId);
        return voting.candidates(contextId, voteCategoryId, teamId);
    }

    @Transactional(readOnly = true)
    public CoachBallotResponse ballot(UUID contextId) {
        requireApproved(contextId);
        return voting.ballot(contextId);
    }

    @Transactional
    public CoachBallotResponse submit(SubmitCoachBallotV2Request request) {
        // Same row lock is used by administrative revocation, preventing a vote from
        // being inserted after the revocation cleanup has committed.
        List<UUID> locked = jdbc.query("""
            SELECT id FROM award_coach_voters WHERE id = ? FOR UPDATE
            """, (rs, row) -> rs.getObject("id", UUID.class), request.contextId());
        if (locked.isEmpty()) {
            throw Errors.forbidden("COACH_CATEGORY_ACCESS_REQUIRED", "Categoria não disponível.");
        }
        requireApproved(request.contextId());
        return voting.submit(request);
    }

    private void requireApproved(UUID contextId) {
        UUID userId = currentUser.userId();
        if (contextId == null) {
            throw Errors.forbidden("COACH_CATEGORY_APPROVAL_REQUIRED",
                    "Selecione uma categoria aprovada para votar.");
        }
        Boolean approved = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM award_coach_voters v
                      JOIN award_coach_credentials c
                        ON c.id = v.credential_id AND c.edition_id = v.edition_id
                       AND c.user_id = v.user_id AND c.active = TRUE
                      JOIN user_accounts u ON u.id = v.user_id AND u.role IN ('COACH','ADMIN')
                      JOIN award_coach_category_applications a
                        ON a.edition_id = v.edition_id AND a.user_id = v.user_id
                       AND a.event_id = v.event_id AND a.division_id = v.division_id
                       AND a.category_id = v.category_id AND a.team_id = v.team_id
                       AND a.status = 'APPROVED'
                     WHERE v.id = ? AND v.user_id = ? AND v.active = TRUE
                )
                """, Boolean.class, contextId, userId);
        if (!Boolean.TRUE.equals(approved)) {
            throw Errors.forbidden("COACH_CATEGORY_APPROVAL_REQUIRED",
                    "Esta categoria não foi aprovada para sua conta ou seu vínculo está inativo.");
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    static CoachVotingState state(String editionStatus, Instant opensAt,
                                  Instant closesAt, boolean submitted, Instant now) {
        if (submitted) return CoachVotingState.SUBMITTED;
        if ("DRAFT".equals(editionStatus)) return CoachVotingState.DRAFT;
        if ("CLOSED".equals(editionStatus)) return CoachVotingState.CLOSED;
        if (!"OPEN".equals(editionStatus)) return CoachVotingState.CLOSED;
        if (opensAt != null && now.isBefore(opensAt)) return CoachVotingState.SCHEDULED;
        if (closesAt != null && !now.isBefore(closesAt)) return CoachVotingState.CLOSED;
        return CoachVotingState.OPEN;
    }
}

package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.api.*;
import br.com.dnafutsal.backend.awards.domain.*;
import br.com.dnafutsal.backend.awards.infrastructure.*;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class AwardRegistrationReopeningService {
    private final JdbcTemplate jdbc;
    private final AwardEditionRepository editions;
    private final AwardPreVoteEditionLockRepository locks;
    private final AwardPreVoteFinalistRepository finalists;
    private final SportsCatalogService catalog;
    private final Clock clock;

    public AwardRegistrationReopeningService(JdbcTemplate jdbc, AwardEditionRepository editions,
            AwardPreVoteEditionLockRepository locks, AwardPreVoteFinalistRepository finalists,
            SportsCatalogService catalog, Clock clock) {
        this.jdbc = jdbc;
        this.editions = editions;
        this.locks = locks;
        this.finalists = finalists;
        this.catalog = catalog;
        this.clock = clock;
    }

    private AwardRegistrationReopeningResponse map(ResultSet rs, int ignored) throws SQLException {
        Instant now = clock.instant();
        Instant opens = rs.getTimestamp("opens_at").toInstant();
        Instant closes = rs.getTimestamp("closes_at").toInstant();
        java.sql.Timestamp stopped = rs.getTimestamp("closed_at");
        Instant closed = stopped == null ? null : stopped.toInstant();
        String status = closed != null || !now.isBefore(closes) ? "CLOSED"
                : now.isBefore(opens) ? "SCHEDULED" : "OPEN";
        return new AwardRegistrationReopeningResponse(
                rs.getObject("id", UUID.class), rs.getObject("edition_id", UUID.class),
                rs.getLong("event_id"), rs.getLong("division_id"), rs.getLong("category_id"),
                AwardRegistrationGender.valueOf(rs.getString("gender")),
                AwardRegistrationContestCategory.valueOf(rs.getString("contest_category")),
                opens, closes, closed, status);
    }

    @Transactional(readOnly = true)
    public List<AwardRegistrationReopeningResponse> list(UUID editionId) {
        if (!editions.existsById(editionId))
            throw Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada.");
        return jdbc.query("SELECT * FROM award_registration_reopenings WHERE edition_id = ? "
                + "ORDER BY event_id, division_id, category_id, gender, contest_category", this::map, editionId);
    }

    @Transactional(readOnly = true)
    public List<AwardRegistrationReopeningResponse> active(UUID editionId) {
        return list(editionId).stream().filter(row -> "OPEN".equals(row.status())).toList();
    }

    @Transactional(readOnly = true)
    public List<AwardRegistrationReopeningEligibilityResponse> eligibility(UUID editionId,
            long eventId, long divisionId, long categoryId, AwardRegistrationGender gender) {
        AwardEdition edition = editions.findById(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada."));
        validateContext(edition, eventId, divisionId, categoryId, gender);
        List<AwardRegistrationReopeningResponse> configured = list(editionId);
        return Arrays.stream(AwardRegistrationContestCategory.values()).map(contest ->
                new AwardRegistrationReopeningEligibilityResponse(contest, contest.label(),
                        hasFinalists(editionId, eventId, divisionId, categoryId, gender, contest),
                        configured.stream().filter(row -> row.eventId() == eventId
                                && row.divisionId() == divisionId && row.categoryId() == categoryId
                                && row.gender() == gender && row.contestCategory() == contest)
                                .map(AwardRegistrationReopeningResponse::status).findFirst().orElse("NONE")))
                .toList();
    }

    private void validateContext(AwardEdition edition, long eventId, long divisionId,
            long categoryId, AwardRegistrationGender gender) {
        if (gender == null || catalog.divisions(edition.getSeason(), gender.catalogTitle()).stream()
                .noneMatch(row -> row.id() == divisionId)
                || catalog.categories(edition.getSeason(), gender.catalogTitle(), divisionId).stream()
                .noneMatch(row -> row.id() == categoryId && row.eventId() == eventId)) {
            throw Errors.badRequest("AWARD_REOPENING_CONTEXT_INVALID", "Divisão, campeonato ou categoria inválidos.");
        }
    }

    private boolean hasFinalists(UUID editionId, long eventId, long divisionId, long categoryId,
            AwardRegistrationGender gender, AwardRegistrationContestCategory contest) {
        return finalists.existsByEditionIdAndEventIdAndDivisionIdAndCategoryIdAndGenderAndContestCategory(
                editionId, eventId, divisionId, categoryId, gender, contest);
    }

    @Transactional
    public AwardRegistrationReopeningResponse open(UUID editionId, AwardRegistrationReopeningRequest request) {
        AwardEdition edition = locks.lockEdition(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada."));
        if (edition.isRegistrationsOpen())
            throw Errors.conflict("AWARD_REGISTRATIONS_ALREADY_OPEN", "Encerre antes o período geral de inscrições.");
        Instant now = clock.instant();
        if (request.opensAt() == null || request.closesAt() == null
                || !request.closesAt().isAfter(now) || !request.closesAt().isAfter(request.opensAt()))
            throw Errors.badRequest("AWARD_REOPENING_WINDOW_INVALID", "Informe um período futuro válido.");
        validateContext(edition, request.eventId(), request.divisionId(), request.categoryId(), request.gender());
        if (request.contestCategory() == null)
            throw Errors.badRequest("AWARD_REOPENING_CATEGORY_INVALID", "Informe a modalidade do prêmio.");
        if (hasFinalists(editionId, request.eventId(), request.divisionId(), request.categoryId(),
                request.gender(), request.contestCategory()))
            throw Errors.conflict("AWARD_PRE_VOTE_ALREADY_FINALIZED", "Esta modalidade já possui finalistas.");
        var existing = list(editionId).stream().filter(row -> row.eventId() == request.eventId()
                && row.divisionId() == request.divisionId() && row.categoryId() == request.categoryId()
                && row.gender() == request.gender() && row.contestCategory() == request.contestCategory())
                .findFirst();
        if (existing.isPresent() && !"CLOSED".equals(existing.get().status()))
            throw Errors.conflict("AWARD_REOPENING_ALREADY_ACTIVE", "Esta modalidade já está reaberta ou agendada.");
        jdbc.update("""
                INSERT INTO award_registration_reopenings
                  (id, edition_id, event_id, division_id, category_id, gender, contest_category, opens_at, closes_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (edition_id, event_id, division_id, category_id, gender, contest_category)
                DO UPDATE SET opens_at = EXCLUDED.opens_at, closes_at = EXCLUDED.closes_at,
                              closed_at = NULL, updated_at = CURRENT_TIMESTAMP
                """, UUID.randomUUID(), editionId, request.eventId(), request.divisionId(), request.categoryId(),
                request.gender().name(), request.contestCategory().name(),
                java.sql.Timestamp.from(request.opensAt()), java.sql.Timestamp.from(request.closesAt()));
        return list(editionId).stream().filter(row -> row.eventId() == request.eventId()
                && row.divisionId() == request.divisionId() && row.categoryId() == request.categoryId()
                && row.gender() == request.gender() && row.contestCategory() == request.contestCategory())
                .findFirst().orElseThrow();
    }

    // As permissões escolhidas pelo administrador são gravadas como uma única transação.
    // Se uma disputa tiver finalistas, nenhuma das outras escolhas é aplicada.
    @Transactional
    public List<AwardRegistrationReopeningResponse> openBatch(UUID editionId,
            List<AwardRegistrationReopeningRequest> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 20)
            throw Errors.badRequest("AWARD_REOPENING_BATCH_INVALID", "Selecione de 1 a 20 modalidades.");
        List<AwardRegistrationReopeningResponse> results = new java.util.ArrayList<>();
        for (AwardRegistrationReopeningRequest request : requests) {
            if (request == null)
                throw Errors.badRequest("AWARD_REOPENING_BATCH_INVALID", "Modalidade inválida.");
            results.add(open(editionId, request));
        }
        return List.copyOf(results);
    }

    @Transactional
    public AwardRegistrationReopeningResponse close(UUID editionId, UUID reopeningId) {
        locks.lockEdition(editionId).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição não encontrada."));
        int changed = jdbc.update("UPDATE award_registration_reopenings SET closed_at = COALESCE(closed_at, ?), "
                + "updated_at = CURRENT_TIMESTAMP WHERE edition_id = ? AND id = ?",
                java.sql.Timestamp.from(clock.instant()), editionId, reopeningId);
        if (changed == 0) throw Errors.notFound("AWARD_REOPENING_NOT_FOUND", "Reabertura não encontrada.");
        return list(editionId).stream().filter(row -> row.id().equals(reopeningId)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public boolean blocksFinalization(UUID editionId, AwardPreVoteContextRequest context) {
        return list(editionId).stream().anyMatch(row -> !"CLOSED".equals(row.status())
                && row.eventId() == context.eventId()
                && row.divisionId() == context.divisionId()
                && row.categoryId() == context.categoryId()
                && row.gender() == context.gender()
                && row.contestCategory() == context.contestCategory());
    }

    @Transactional(readOnly = true)
    public boolean allows(UUID editionId, long eventId, long divisionId, long categoryId,
            AwardRegistrationGender gender, AwardRegistrationContestCategory contest) {
        Instant now = clock.instant();
        Boolean enabled = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM award_registration_reopenings r
                    WHERE r.edition_id = ? AND r.event_id = ? AND r.division_id = ?
                      AND r.category_id = ? AND r.gender = ? AND r.contest_category = ?
                      AND r.closed_at IS NULL AND r.opens_at <= ? AND r.closes_at > ?
                      AND NOT EXISTS (
                          SELECT 1 FROM award_pre_vote_finalists f
                          WHERE f.edition_id = r.edition_id AND f.event_id = r.event_id
                            AND f.division_id = r.division_id AND f.category_id = r.category_id
                            AND f.gender = r.gender AND f.contest_category = r.contest_category
                      )
                )
                """, Boolean.class, editionId, eventId, divisionId, categoryId,
                gender.name(), contest.name(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return Boolean.TRUE.equals(enabled);
    }
}

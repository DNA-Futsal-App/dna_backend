package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.awards.domain.AwardEdition;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import br.com.dnafutsal.backend.awards.infrastructure.AwardEditionRepository;
import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.common.TokenSupport;
import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Category-scoped invitations only; registration/claim is intentionally phase 3. */
@Service
public class CoachCategoryLinkService {
    private final JdbcTemplate jdbc;
    private final AwardEditionRepository editions;
    private final SportsCatalogService catalog;
    private final AwardGoldEligibilityService gold;
    private final TokenSupport tokens;
    private final CurrentUserService currentUser;
    private final AppProperties app;
    private final Clock clock;

    public CoachCategoryLinkService(JdbcTemplate jdbc, AwardEditionRepository editions,
                                    SportsCatalogService catalog, AwardGoldEligibilityService gold,
                                    TokenSupport tokens, CurrentUserService currentUser,
                                    AppProperties app, Clock clock) {
        this.jdbc = jdbc;
        this.editions = editions;
        this.catalog = catalog;
        this.gold = gold;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.app = app;
        this.clock = clock;
    }

    public record CreateRequest(long eventId, long divisionId, long categoryId,
                                AwardRegistrationGender gender, Instant expiresAt) { }

    public record LinkView(UUID linkId, UUID editionId, long eventId, long divisionId, long categoryId,
                           String gender, String status, boolean available, Instant expiresAt,
                           Instant createdAt, Instant revokedAt) { }

    public record CreatedLink(LinkView link, String accessUrl) { }

    @Transactional(readOnly = true)
    public List<LinkView> list(UUID editionId) {
        edition(editionId);
        return jdbc.query("""
                SELECT id, edition_id, event_id, division_id, category_id, gender, status,
                       expires_at, created_at, revoked_at
                FROM award_coach_category_links WHERE edition_id = ?
                ORDER BY created_at DESC, id
                """, (rs, row) -> toView(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("edition_id"),
                rs.getLong("event_id"), rs.getLong("division_id"), rs.getLong("category_id"),
                rs.getString("gender"), rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()),
                editionId);
    }

    @Transactional
    public CreatedLink create(UUID editionId, CreateRequest request) {
        AwardEdition edition = edition(editionId);
        if (edition.isClosed()) {
            throw Errors.conflict("COACH_CATEGORY_EDITION_CLOSED", "Edição encerrada não aceita novos convites.");
        }
        if (request == null || request.gender() == null || request.eventId() <= 0
                || request.divisionId() <= 0 || request.categoryId() <= 0) {
            throw Errors.badRequest("COACH_CATEGORY_INVALID", "Informe divisão, categoria, evento e modalidade válidos.");
        }
        Instant now = clock.instant();
        Instant expires = request.expiresAt();
        if (expires == null || !expires.isAfter(now) || expires.isAfter(now.plus(90, ChronoUnit.DAYS))) {
            throw Errors.badRequest("COACH_CATEGORY_EXPIRY_INVALID", "O link deve expirar entre agora e 90 dias.");
        }
        // Do not trust a client-supplied event/category/gender combination.
        List<CatalogCategoryView> categories = catalog.categories(
                edition.getSeason(), request.gender().catalogTitle(), request.divisionId());
        boolean valid = categories.stream().anyMatch(c -> c.id() == request.categoryId()
                && c.eventId() == request.eventId());
        if (!valid) {
            throw Errors.badRequest("COACH_CATEGORY_CONTEXT_INVALID",
                    "A categoria e o evento não pertencem à modalidade/divisão informada.");
        }
        // Phase-1 frozen Gold roster is a prerequisite to issuing an invitation.
        AwardGoldEligibilityService.Report prepared = gold.prepared(
                editionId, request.eventId(), request.divisionId(), request.categoryId());
        if (prepared == null || !prepared.prepared() || prepared.teams().isEmpty()) {
            throw Errors.conflict("AWARD_GOLD_NOT_PREPARED",
                    "Prepare a relação oficial da Chave Ouro antes de gerar o convite.");
        }

        String raw = tokens.generate();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO award_coach_category_links
                        (id, edition_id, event_id, division_id, category_id, gender, token_hash,
                         status, expires_at, created_by_user_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                    """, id, editionId, request.eventId(), request.divisionId(), request.categoryId(),
                    request.gender().name(), tokens.hash(raw), Timestamp.from(expires),
                    currentUser.userId(), Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw Errors.conflict("COACH_CATEGORY_LINK_ALREADY_ACTIVE",
                    "Já existe um link ativo para esta categoria. Revogue-o antes de gerar outro.");
        }
        LinkView view = toView(id, editionId, request.eventId(), request.divisionId(),
                request.categoryId(), request.gender().name(), "ACTIVE", expires, now, null);
        String base = app.frontendBaseUrl().replaceAll("/+$", "");
        return new CreatedLink(view, base + "/premio-dna/tecnicos/convite/" + raw);
    }

    @Transactional
    public LinkView revoke(UUID editionId, UUID linkId) {
        edition(editionId);
        int updated = jdbc.update("""
                UPDATE award_coach_category_links SET status = 'REVOKED', revoked_at = ?
                WHERE edition_id = ? AND id = ? AND status = 'ACTIVE'
                """, Timestamp.from(clock.instant()), editionId, linkId);
        if (updated != 1) {
            throw Errors.notFound("COACH_CATEGORY_LINK_NOT_ACTIVE", "Link não encontrado ou já revogado.");
        }
        return list(editionId).stream().filter(x -> x.linkId().equals(linkId)).findFirst()
                .orElseThrow(() -> Errors.notFound("COACH_CATEGORY_LINK_NOT_FOUND", "Link não encontrado."));
    }

    @Transactional(readOnly = true)
    public LinkView inspect(String rawToken) {
        if (rawToken == null || rawToken.length() < 20 || rawToken.length() > 200) {
            throw Errors.notFound("COACH_CATEGORY_LINK_NOT_FOUND", "Convite não encontrado.");
        }
        List<LinkView> links = jdbc.query("""
                SELECT id, edition_id, event_id, division_id, category_id, gender, status,
                       expires_at, created_at, revoked_at
                FROM award_coach_category_links WHERE token_hash = ?
                """, (rs, row) -> toView(
                (UUID) rs.getObject("id"), (UUID) rs.getObject("edition_id"),
                rs.getLong("event_id"), rs.getLong("division_id"), rs.getLong("category_id"),
                rs.getString("gender"), rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()),
                tokens.hash(rawToken.trim()));
        return links.stream().findFirst().orElseThrow(() -> Errors.notFound(
                "COACH_CATEGORY_LINK_NOT_FOUND", "Convite não encontrado."));
    }

    private LinkView toView(UUID id, UUID editionId, long eventId, long divisionId, long categoryId,
                            String gender, String status, Instant expiresAt, Instant createdAt,
                            Instant revokedAt) {
        boolean available = "ACTIVE".equals(status) && clock.instant().isBefore(expiresAt)
                && !edition(editionId).isClosed();
        String effectiveStatus = "REVOKED".equals(status) ? "REVOKED"
                : !clock.instant().isBefore(expiresAt) ? "EXPIRED"
                : edition(editionId).isClosed() ? "EDITION_CLOSED" : "AVAILABLE";
        return new LinkView(id, editionId, eventId, divisionId, categoryId, gender,
                effectiveStatus, available, expiresAt, createdAt, revokedAt);
    }

    private AwardEdition edition(UUID id) {
        return editions.findById(id).orElseThrow(() ->
                Errors.notFound("AWARD_EDITION_NOT_FOUND", "Edição do prêmio não encontrada."));
    }
}

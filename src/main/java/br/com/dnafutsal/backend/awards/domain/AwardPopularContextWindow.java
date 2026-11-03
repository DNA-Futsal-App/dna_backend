package br.com.dnafutsal.backend.awards.domain;

import br.com.dnafutsal.backend.awards.api.AwardPopularContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_popular_context_windows")
public class AwardPopularContextWindow {
    @Id
    private UUID id;
    @Column(name = "edition_id", nullable = false)
    private UUID editionId;
    @Column(name = "event_id", nullable = false)
    private long eventId;
    @Column(name = "division_id", nullable = false)
    private long divisionId;
    @Column(name = "category_id", nullable = false)
    private long categoryId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardRegistrationGender gender;
    @Enumerated(EnumType.STRING)
    @Column(name = "contest_category", nullable = false, length = 40)
    private AwardRegistrationContestCategory contestCategory;
    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;
    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;
    @Column(name = "manually_closed_at")
    private Instant manuallyClosedAt;
    @Version
    private long version;

    protected AwardPopularContextWindow() { }

    public AwardPopularContextWindow(UUID editionId, AwardPopularContext context,
                                     Instant opensAt, Instant closesAt) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.eventId = context.eventId();
        this.divisionId = context.divisionId();
        this.categoryId = context.categoryId();
        this.gender = context.gender();
        this.contestCategory = context.contestCategory();
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("Intervalo de votação inválido.");
        }
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public AwardPopularContext context() {
        return new AwardPopularContext(eventId, divisionId, categoryId, gender, contestCategory);
    }
    public boolean isOpen(Instant now) {
        return manuallyClosedAt == null && !now.isBefore(opensAt) && now.isBefore(closesAt);
    }
    public String statusAt(Instant now) {
        if (manuallyClosedAt != null || !now.isBefore(closesAt)) return "CLOSED";
        return now.isBefore(opensAt) ? "SCHEDULED" : "OPEN";
    }
    public void close(Instant now) {
        if (manuallyClosedAt == null) manuallyClosedAt = now;
    }
    public UUID getEditionId() { return editionId; }
    public UUID getId() { return id; }
    public Instant getOpensAt() { return opensAt; }
    public Instant getClosesAt() { return closesAt; }
    public Instant getManuallyClosedAt() { return manuallyClosedAt; }
}

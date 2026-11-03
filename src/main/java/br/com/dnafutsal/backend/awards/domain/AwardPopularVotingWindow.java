package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_popular_voting_windows")
public class AwardPopularVotingWindow {
    @Id
    @Column(name = "edition_id")
    private UUID editionId;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "manually_closed_at")
    private Instant manuallyClosedAt;

    @Version
    private long version;

    protected AwardPopularVotingWindow() { }

    public AwardPopularVotingWindow(UUID editionId, Instant opensAt, Instant closesAt) {
        this.editionId = editionId;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public boolean isOpen(Instant now) {
        return manuallyClosedAt == null && !now.isBefore(opensAt) && now.isBefore(closesAt);
    }

    public String statusAt(Instant now) {
        if (manuallyClosedAt != null || !now.isBefore(closesAt)) return "CLOSED";
        if (now.isBefore(opensAt)) return "SCHEDULED";
        return "OPEN";
    }

    public void close(Instant now) {
        if (manuallyClosedAt == null) manuallyClosedAt = now;
    }

    public UUID getEditionId() { return editionId; }
    public Instant getOpensAt() { return opensAt; }
    public Instant getClosesAt() { return closesAt; }
    public Instant getManuallyClosedAt() { return manuallyClosedAt; }
}

package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_editions")
public class AwardEdition {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private int season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardEditionStatus status;

    @Column(name = "voting_opens_at")
    private Instant votingOpensAt;

    @Column(name = "voting_closes_at")
    private Instant votingClosesAt;

    @Column(name = "registrations_open", nullable = false)
    private boolean registrationsOpen;

    @Column(name = "registrations_opened_at")
    private Instant registrationsOpenedAt;

    @Column(name = "registrations_closed_at")
    private Instant registrationsClosedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardEdition() {
    }

    public AwardEdition(
            String slug,
            String name,
            int season,
            AwardEditionStatus status,
            Instant votingOpensAt,
            Instant votingClosesAt
    ) {
        this.id = UUID.randomUUID();
        this.slug = slug;
        this.name = name;
        this.season = season;
        this.status = status;
        this.votingOpensAt = votingOpensAt;
        this.votingClosesAt = votingClosesAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isClosed() {
        return status == AwardEditionStatus.CLOSED;
    }

    public boolean isVotingOpenAt(
            Instant now
    ) {
        if (status != AwardEditionStatus.OPEN) {
            return false;
        }

        if (votingOpensAt != null
                && now.isBefore(votingOpensAt)) {
            return false;
        }

        return votingClosesAt == null
                || now.isBefore(votingClosesAt);
    }

    public void openVoting(
            Instant opensAt,
            Instant closesAt
    ) {
        this.status = AwardEditionStatus.OPEN;
        this.votingOpensAt = opensAt;
        this.votingClosesAt = closesAt;
    }

    public void closeVoting(
            Instant closedAt
    ) {
        this.status = AwardEditionStatus.CLOSED;

        if (votingClosesAt == null
                || votingClosesAt.isAfter(closedAt)) {
            votingClosesAt = closedAt;
        }
    }

    public void resetVoting() {
        this.status = AwardEditionStatus.DRAFT;
        this.votingOpensAt = null;
        this.votingClosesAt = null;
    }

    public boolean isRegistrationsOpen() {
        return registrationsOpen;
    }

    public void openRegistrations(
            Instant openedAt
    ) {
        this.registrationsOpen = true;
        this.registrationsOpenedAt = openedAt;
        this.registrationsClosedAt = null;
    }

    public void closeRegistrations(
            Instant closedAt
    ) {
        this.registrationsOpen = false;
        this.registrationsClosedAt = closedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public int getSeason() {
        return season;
    }

    public AwardEditionStatus getStatus() {
        return status;
    }

    public Instant getVotingOpensAt() {
        return votingOpensAt;
    }

    public Instant getVotingClosesAt() {
        return votingClosesAt;
    }

    public Instant getRegistrationsOpenedAt() {
        return registrationsOpenedAt;
    }

    public Instant getRegistrationsClosedAt() {
        return registrationsClosedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

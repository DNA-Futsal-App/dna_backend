package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_ballots")
public class AwardBallot {

    @Id
    private UUID id;

    @Column(name = "edition_id", nullable = false)
    private UUID editionId;

    @Column(name = "coach_voter_id", nullable = false)
    private UUID coachVoterId;

    @Column(name = "voter_user_id", nullable = false)
    private UUID voterUserId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardBallot() {
    }

    public AwardBallot(
            UUID editionId,
            UUID coachVoterId,
            UUID voterUserId,
            Instant submittedAt
    ) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.coachVoterId = coachVoterId;
        this.voterUserId = voterUserId;
        this.submittedAt = submittedAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEditionId() {
        return editionId;
    }

    public UUID getCoachVoterId() {
        return coachVoterId;
    }

    public UUID getVoterUserId() {
        return voterUserId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

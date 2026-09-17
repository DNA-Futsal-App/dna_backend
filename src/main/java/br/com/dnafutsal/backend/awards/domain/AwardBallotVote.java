package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_ballot_votes")
public class AwardBallotVote {

    @Id
    private UUID id;

    @Column(name = "ballot_id", nullable = false)
    private UUID ballotId;

    @Column(name = "award_vote_category_id", nullable = false)
    private UUID awardVoteCategoryId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardBallotVote() {
    }

    public AwardBallotVote(
            UUID ballotId,
            UUID awardVoteCategoryId,
            UUID candidateId
    ) {
        this.id = UUID.randomUUID();
        this.ballotId = ballotId;
        this.awardVoteCategoryId = awardVoteCategoryId;
        this.candidateId = candidateId;
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

    public UUID getBallotId() {
        return ballotId;
    }

    public UUID getAwardVoteCategoryId() {
        return awardVoteCategoryId;
    }

    public UUID getCandidateId() {
        return candidateId;
    }
}

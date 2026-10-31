package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_pre_vote_scores")
public class AwardPreVoteScore {
    @Id
    private UUID id;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "approval_at", nullable = false)
    private Instant approvalAt;

    @Column(name = "score", nullable = false)
    private short score;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AwardPreVoteScore() {}

    public AwardPreVoteScore(UUID entryId, UUID adminUserId, Instant approvalAt, int score) {
        this.id = UUID.randomUUID();
        this.entryId = entryId;
        this.adminUserId = adminUserId;
        this.approvalAt = approvalAt;
        this.score = (short) score;
    }

    public void changeScore(int score) {
        this.score = (short) score;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getEntryId() { return entryId; }
    public UUID getAdminUserId() { return adminUserId; }
    public int getScore() { return score; }
    public Instant getApprovalAt() { return approvalAt; }
}

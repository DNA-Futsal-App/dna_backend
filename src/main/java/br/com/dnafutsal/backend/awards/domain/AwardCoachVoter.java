package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_coach_voters")
public class AwardCoachVoter {

    @Id
    private UUID id;

    @Column(name = "edition_id", nullable = false)
    private UUID editionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "invite_id", nullable = false)
    private UUID inviteId;

    @Column(name = "self_coach_candidate_id", nullable = false)
    private UUID selfCoachCandidateId;

    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "division_id", nullable = false)
    private long divisionId;

    @Column(name = "category_id", nullable = false)
    private long categoryId;

    @Column(name = "team_id", nullable = false, length = 100)
    private String teamId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardCoachVoter() {
    }

    public AwardCoachVoter(
            UUID editionId,
            UUID userId,
            UUID inviteId,
            UUID selfCoachCandidateId,
            long eventId,
            long divisionId,
            long categoryId,
            String teamId
    ) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.userId = userId;
        this.inviteId = inviteId;
        this.selfCoachCandidateId = selfCoachCandidateId;
        this.eventId = eventId;
        this.divisionId = divisionId;
        this.categoryId = categoryId;
        this.teamId = teamId;
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

    public UUID getUserId() {
        return userId;
    }

    public UUID getInviteId() {
        return inviteId;
    }

    public UUID getSelfCoachCandidateId() {
        return selfCoachCandidateId;
    }

    public long getEventId() {
        return eventId;
    }

    public long getDivisionId() {
        return divisionId;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getTeamId() {
        return teamId;
    }
}

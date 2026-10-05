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
@Table(name = "award_coach_voters")
public class AwardCoachVoter {

    @Id
    private UUID id;

    @Column(name = "edition_id", nullable = false)
    private UUID editionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "invite_id")
    private UUID inviteId;

    @Column(name = "credential_id")
    private UUID credentialId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "context_source", nullable = false, length = 20)
    private AwardCoachVoterContextSource contextSource;

    @Column(nullable = false)
    private boolean active;

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
        this.contextSource = AwardCoachVoterContextSource.MIGRATED;
        this.active = true;
    }

    public AwardCoachVoter(
            UUID editionId,
            UUID userId,
            UUID credentialId,
            UUID selfCoachCandidateId,
            long eventId,
            long divisionId,
            long categoryId,
            String teamId,
            AwardCoachVoterContextSource contextSource
    ) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.userId = userId;
        this.credentialId = credentialId;
        this.selfCoachCandidateId = selfCoachCandidateId;
        this.eventId = eventId;
        this.divisionId = divisionId;
        this.categoryId = categoryId;
        this.teamId = teamId;
        this.contextSource = contextSource;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (contextSource == null) {
            contextSource = AwardCoachVoterContextSource.MIGRATED;
        }
    }

    public void deactivate() { active = false; }

    public UUID getId() { return id; }
    public UUID getEditionId() { return editionId; }
    public UUID getUserId() { return userId; }
    public UUID getInviteId() { return inviteId; }
    public UUID getCredentialId() { return credentialId; }
    public UUID getSelfCoachCandidateId() { return selfCoachCandidateId; }
    public long getEventId() { return eventId; }
    public long getDivisionId() { return divisionId; }
    public long getCategoryId() { return categoryId; }
    public String getTeamId() { return teamId; }
    public AwardCoachVoterContextSource getContextSource() { return contextSource; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}

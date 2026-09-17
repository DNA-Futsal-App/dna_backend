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
@Table(name = "award_candidates")
public class AwardCandidate {

    @Id
    private UUID id;

    @Column(name = "edition_id", nullable = false)
    private UUID editionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "candidate_type", nullable = false, length = 20)
    private AwardCandidateType candidateType;

    @Column(name = "external_person_id", length = 120)
    private String externalPersonId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "position_code", length = 50)
    private String positionCode;

    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "division_id", nullable = false)
    private long divisionId;

    @Column(name = "category_id", nullable = false)
    private long categoryId;

    @Column(name = "team_id", nullable = false, length = 100)
    private String teamId;

    @Column(name = "team_name", nullable = false, length = 150)
    private String teamName;

    @Column(name = "team_logo_url", length = 500)
    private String teamLogoUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardCandidate() {
    }

    public AwardCandidate(
            UUID editionId,
            AwardCandidateType candidateType,
            String externalPersonId,
            String name,
            String positionCode,
            long eventId,
            long divisionId,
            long categoryId,
            String teamId,
            String teamName,
            String teamLogoUrl,
            String imageUrl
    ) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.candidateType = candidateType;
        this.externalPersonId = externalPersonId;
        this.name = name;
        this.positionCode = positionCode;
        this.eventId = eventId;
        this.divisionId = divisionId;
        this.categoryId = categoryId;
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamLogoUrl = teamLogoUrl;
        this.imageUrl = imageUrl;
        this.active = true;
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

    public AwardCandidateType getCandidateType() {
        return candidateType;
    }

    public String getExternalPersonId() {
        return externalPersonId;
    }

    public String getName() {
        return name;
    }

    public String getPositionCode() {
        return positionCode;
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

    public String getTeamName() {
        return teamName;
    }

    public String getTeamLogoUrl() {
        return teamLogoUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

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
@Table(name = "award_vote_categories")
public class AwardVoteCategory {

    @Id
    private UUID id;

    @Column(name = "edition_id", nullable = false)
    private UUID editionId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AwardCandidateType targetType;

    @Column(name = "position_code", length = 50)
    private String positionCode;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardVoteCategory() {
    }

    public AwardVoteCategory(
            UUID editionId,
            String code,
            String label,
            AwardCandidateType targetType,
            String positionCode,
            int displayOrder,
            boolean required
    ) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.code = code;
        this.label = label;
        this.targetType = targetType;
        this.positionCode = positionCode;
        this.displayOrder = displayOrder;
        this.required = required;
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

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public AwardCandidateType getTargetType() {
        return targetType;
    }

    public String getPositionCode() {
        return positionCode;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isRequired() {
        return required;
    }
}

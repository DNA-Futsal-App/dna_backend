package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_popular_votes")
public class AwardPopularVote {
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
    @Column(name = "gender", nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private AwardRegistrationGender gender;
    @Column(name = "contest_category", nullable = false, length = 40)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private AwardRegistrationContestCategory contestCategory;
    @Column(name = "entry_id", nullable = false)
    private UUID entryId;
    // SET NULL preserva a apuração quando uma conta é excluída.
    @Column(name = "voter_user_id")
    private UUID voterUserId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardPopularVote() { }

    public AwardPopularVote(UUID editionId, long eventId, long divisionId, long categoryId,
                            AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory,
                            UUID entryId, UUID voterUserId) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.eventId = eventId;
        this.divisionId = divisionId;
        this.categoryId = categoryId;
        this.gender = gender;
        this.contestCategory = contestCategory;
        this.entryId = entryId;
        this.voterUserId = voterUserId;
    }

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public UUID getEntryId() { return entryId; }
    public UUID getVoterUserId() { return voterUserId; }
}

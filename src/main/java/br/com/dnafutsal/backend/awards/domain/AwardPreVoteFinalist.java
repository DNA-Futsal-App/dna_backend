package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_pre_vote_finalists")
public class AwardPreVoteFinalist {
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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardRegistrationGender gender;
    @Enumerated(EnumType.STRING)
    @Column(name = "contest_category", nullable = false, length = 40)
    private AwardRegistrationContestCategory contestCategory;
    @Column(name = "entry_id", nullable = false, unique = true)
    private UUID entryId;
    @Column(nullable = false)
    private short rank;
    @Column(name = "average_score", nullable = false, precision = 8, scale = 4)
    private BigDecimal averageScore;
    @Column(name = "score_count", nullable = false)
    private int scoreCount;
    @Column(name = "finalized_at", nullable = false)
    private Instant finalizedAt;
    @Column(name = "finalized_by_user_id", nullable = false)
    private UUID finalizedByUserId;

    protected AwardPreVoteFinalist() {}

    public AwardPreVoteFinalist(UUID editionId, long eventId, long divisionId, long categoryId,
                               AwardRegistrationGender gender, AwardRegistrationContestCategory contestCategory,
                               UUID entryId, int rank, BigDecimal averageScore, int scoreCount,
                               Instant finalizedAt, UUID finalizedByUserId) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.eventId = eventId;
        this.divisionId = divisionId;
        this.categoryId = categoryId;
        this.gender = gender;
        this.contestCategory = contestCategory;
        this.entryId = entryId;
        this.rank = (short) rank;
        this.averageScore = averageScore;
        this.scoreCount = scoreCount;
        this.finalizedAt = finalizedAt;
        this.finalizedByUserId = finalizedByUserId;
    }

    public UUID getEntryId() { return entryId; }
    public short getRank() { return rank; }
}

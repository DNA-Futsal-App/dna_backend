package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_registration_entries")
public class AwardRegistrationEntry {

    @Id
    private UUID id;

    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contest_category", nullable = false, length = 40)
    private AwardRegistrationContestCategory contestCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private AwardRegistrationMediaSource sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_status", nullable = false, length = 20)
    private AwardRegistrationMediaStatus mediaStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private AwardRegistrationReviewStatus reviewStatus;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    @Column(name = "external_url", length = 2000)
    private String externalUrl;

    @Column(name = "object_name", length = 700)
    private String objectName;

    @Column(name = "display_filename", length = 700)
    private String displayFilename;

    @Column(name = "pending_object_name", length = 700)
    private String pendingObjectName;

    @Column(name = "pending_par_id", length = 500)
    private String pendingParId;

    @Column(name = "duration_ms")
    private Long durationMs;

    private Integer width;
    private Integer height;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AwardRegistrationEntry() {
    }

    public AwardRegistrationEntry(
            UUID registrationId,
            AwardRegistrationContestCategory contestCategory,
            AwardRegistrationMediaSource sourceType,
            String externalUrl
    ) {
        this.id = UUID.randomUUID();
        this.registrationId = registrationId;
        this.contestCategory = contestCategory;
        this.sourceType = sourceType;
        this.externalUrl = externalUrl;
        this.mediaStatus =
                sourceType == AwardRegistrationMediaSource.LINK
                        ? AwardRegistrationMediaStatus.READY
                        : AwardRegistrationMediaStatus.PENDING;
        this.reviewStatus = AwardRegistrationReviewStatus.PENDING_REVIEW;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();

        if (id == null) {
            id = UUID.randomUUID();
        }

        if (mediaStatus == null) {
            mediaStatus = AwardRegistrationMediaStatus.PENDING;
        }

        if (reviewStatus == null) {
            reviewStatus = AwardRegistrationReviewStatus.PENDING_REVIEW;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void beginUpload(
            String pendingObjectName,
            String pendingParId
    ) {
        this.pendingObjectName = pendingObjectName;
        this.pendingParId = pendingParId;
        this.mediaStatus = AwardRegistrationMediaStatus.PENDING;
    }

    public void beginProcessing() {
        if (sourceType != AwardRegistrationMediaSource.UPLOAD) {
            throw new IllegalStateException(
                    "Only UPLOAD entries can be processed."
            );
        }

        if (pendingObjectName == null
                || pendingObjectName.isBlank()) {
            throw new IllegalStateException(
                    "Pending object is required before processing."
            );
        }

        this.mediaStatus =
                AwardRegistrationMediaStatus.PROCESSING;
    }

    public void failProcessing() {
        this.pendingObjectName = null;
        this.pendingParId = null;
        this.mediaStatus =
                AwardRegistrationMediaStatus.FAILED;
    }

    public void completeUpload(
            String objectName,
            String displayFilename,
            long durationMs,
            int width,
            int height,
            long fileSizeBytes
    ) {
        this.objectName = objectName;
        this.displayFilename = displayFilename;
        this.durationMs = durationMs;
        this.width = width;
        this.height = height;
        this.fileSizeBytes = fileSizeBytes;
        this.pendingObjectName = null;
        this.pendingParId = null;
        this.mediaStatus = AwardRegistrationMediaStatus.READY;
    }

    public void cancelPendingUpload() {
        this.pendingObjectName = null;
        this.pendingParId = null;

        if (objectName == null) {
            this.mediaStatus = AwardRegistrationMediaStatus.PENDING;
        } else {
            this.mediaStatus = AwardRegistrationMediaStatus.READY;
        }
    }

    public void updateExternalUrl(String externalUrl) {
        if (sourceType != AwardRegistrationMediaSource.LINK) {
            throw new IllegalStateException(
                    "Only LINK entries can update externalUrl."
            );
        }

        this.externalUrl = externalUrl;
        this.mediaStatus = AwardRegistrationMediaStatus.READY;
    }

    public void approveReview(
            UUID reviewerUserId,
            Instant reviewedAt
    ) {
        requirePendingReview();

        if (mediaStatus != AwardRegistrationMediaStatus.READY) {
            throw new IllegalStateException(
                    "Only READY entries can be reviewed."
            );
        }

        this.reviewStatus = AwardRegistrationReviewStatus.APPROVED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = reviewedAt;
        this.reviewReason = null;
    }

    public void rejectReview(
            UUID reviewerUserId,
            Instant reviewedAt,
            String reason
    ) {
        requirePendingReview();

        if (mediaStatus != AwardRegistrationMediaStatus.READY) {
            throw new IllegalStateException(
                    "Only READY entries can be reviewed."
            );
        }

        String normalizedReason =
                reason == null
                        ? ""
                        : reason.trim();

        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException(
                    "Review reason is required when rejecting an entry."
            );
        }

        this.reviewStatus = AwardRegistrationReviewStatus.REJECTED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = reviewedAt;
        this.reviewReason = normalizedReason;
    }

    private void requirePendingReview() {
        if (reviewStatus != AwardRegistrationReviewStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "This entry already has a review decision."
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRegistrationId() {
        return registrationId;
    }

    public AwardRegistrationContestCategory getContestCategory() {
        return contestCategory;
    }

    public AwardRegistrationMediaSource getSourceType() {
        return sourceType;
    }

    public AwardRegistrationMediaStatus getMediaStatus() {
        return mediaStatus;
    }

    public AwardRegistrationReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public UUID getReviewedByUserId() {
        return reviewedByUserId;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getDisplayFilename() {
        return displayFilename;
    }

    public String getPendingObjectName() {
        return pendingObjectName;
    }

    public String getPendingParId() {
        return pendingParId;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }
}

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
@Table(name = "award_coach_invites")
public class AwardCoachInvite {

    @Id
    private UUID id;

    @Column(name = "edition_id", nullable = false)
    private UUID editionId;

    @Column(name = "coach_candidate_id", nullable = false)
    private UUID coachCandidateId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardCoachInviteStatus status;

    @Column(name = "claimed_by_user_id")
    private UUID claimedByUserId;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "reserved_by_user_id")
    private UUID reservedByUserId;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AwardCoachInvite() {
    }

    public AwardCoachInvite(
            UUID editionId,
            UUID coachCandidateId,
            String tokenHash,
            Instant expiresAt
    ) {
        this.id = UUID.randomUUID();
        this.editionId = editionId;
        this.coachCandidateId = coachCandidateId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.status = AwardCoachInviteStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsableAt(Instant now) {
        return status == AwardCoachInviteStatus.PENDING && !isExpiredAt(now);
    }

    public void reserve(
            UUID userId,
            Instant now,
            Instant reservationExpiresAt
    ) {
        this.reservedByUserId = userId;
        this.reservedAt = now;
        this.reservationExpiresAt = reservationExpiresAt;
    }

    public boolean hasActiveReservationAt(
            Instant now
    ) {
        return reservedByUserId != null
                && reservationExpiresAt != null
                && now.isBefore(
                        reservationExpiresAt
                );
    }

    public boolean hasExpiredReservationAt(
            Instant now
    ) {
        return reservedByUserId != null
                && (
                reservationExpiresAt == null
                        || !now.isBefore(
                        reservationExpiresAt
                )
        );
    }

    public void clearReservation() {
        this.reservedByUserId = null;
        this.reservedAt = null;
        this.reservationExpiresAt = null;
    }

    public void claim(UUID userId, Instant now) {
        this.status = AwardCoachInviteStatus.CLAIMED;
        this.claimedByUserId = userId;
        this.claimedAt = now;
        clearReservation();
    }

    public void revoke() {
        if (status == AwardCoachInviteStatus.PENDING) {
            status = AwardCoachInviteStatus.REVOKED;
            clearReservation();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEditionId() {
        return editionId;
    }

    public UUID getCoachCandidateId() {
        return coachCandidateId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public AwardCoachInviteStatus getStatus() {
        return status;
    }

    public UUID getClaimedByUserId() {
        return claimedByUserId;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public UUID getReservedByUserId() {
        return reservedByUserId;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

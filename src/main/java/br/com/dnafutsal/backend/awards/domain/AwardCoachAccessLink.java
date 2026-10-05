package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "award_coach_access_links")
public class AwardCoachAccessLink {
    @Id private UUID id;
    @Column(name="edition_id", nullable=false) private UUID editionId;
    @Column(name="token_hash", nullable=false, length=64) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AwardCoachAccessLinkStatus status;
    @Column(name="created_by_user_id", nullable=false) private UUID createdByUserId;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="revoked_at") private Instant revokedAt;
    protected AwardCoachAccessLink() {}
    public AwardCoachAccessLink(UUID editionId,String tokenHash,UUID createdByUserId){
        this.id=UUID.randomUUID(); this.editionId=editionId; this.tokenHash=tokenHash; this.createdByUserId=createdByUserId; this.status=AwardCoachAccessLinkStatus.ACTIVE;
    }
    @PrePersist void onCreate(){ if(createdAt==null) createdAt=Instant.now(); }
    public void revoke(Instant now){ status=AwardCoachAccessLinkStatus.REVOKED; revokedAt=now; }
    public UUID getId(){return id;} public UUID getEditionId(){return editionId;} public String getTokenHash(){return tokenHash;}
    public AwardCoachAccessLinkStatus getStatus(){return status;} public UUID getCreatedByUserId(){return createdByUserId;}
    public Instant getCreatedAt(){return createdAt;} public Instant getRevokedAt(){return revokedAt;}
}

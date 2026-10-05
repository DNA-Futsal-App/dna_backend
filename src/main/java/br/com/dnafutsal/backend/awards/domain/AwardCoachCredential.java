package br.com.dnafutsal.backend.awards.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="award_coach_credentials")
public class AwardCoachCredential {
    @Id private UUID id;
    @Column(name="edition_id",nullable=false) private UUID editionId;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="access_link_id") private UUID accessLinkId;
    @Column(nullable=false) private boolean active;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected AwardCoachCredential(){}
    public AwardCoachCredential(UUID editionId,UUID userId,UUID accessLinkId){this.id=UUID.randomUUID();this.editionId=editionId;this.userId=userId;this.accessLinkId=accessLinkId;this.active=true;}
    @PrePersist void onCreate(){if(createdAt==null)createdAt=Instant.now();}
    public void deactivate(){active=false;}
    public UUID getId(){return id;} public UUID getEditionId(){return editionId;} public UUID getUserId(){return userId;} public UUID getAccessLinkId(){return accessLinkId;} public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;}
}

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
@Table(name = "award_registrations")
public class AwardRegistration {

    @Id
    private UUID id;

    @Column(name = "registration_number", nullable = false, unique = true)
    private long registrationNumber;

    @Column(name = "edition_id", nullable = false)
    private UUID editionId;

    @Column(name = "representative_user_id", nullable = false)
    private UUID representativeUserId;

    @Column(name = "representative_cpf_encrypted", nullable = false, length = 512)
    private String representativeCpfEncrypted;

    @Column(name = "athlete_name", nullable = false, length = 150)
    private String athleteName;

    @Column(name = "athlete_instagram", nullable = false, length = 64)
    private String athleteInstagram;

    @Column(name = "athlete_instagram_normalized", nullable = false, length = 64)
    private String athleteInstagramNormalized;

    @Column(name = "event_id", nullable = false)
    private long eventId;

    @Column(name = "division_id", nullable = false)
    private long divisionId;

    @Column(name = "division_name", nullable = false, length = 120)
    private String divisionName;

    @Column(name = "category_id", nullable = false)
    private long categoryId;

    @Column(name = "category_name", nullable = false, length = 120)
    private String categoryName;

    @Column(name = "team_id", nullable = false, length = 100)
    private String teamId;

    @Column(name = "team_name", nullable = false, length = 150)
    private String teamName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AwardRegistrationStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected AwardRegistration() {
    }

    public AwardRegistration(
            long registrationNumber,
            UUID editionId,
            UUID representativeUserId,
            String representativeCpfEncrypted,
            String athleteName,
            String athleteInstagram,
            String athleteInstagramNormalized,
            long eventId,
            long divisionId,
            String divisionName,
            long categoryId,
            String categoryName,
            String teamId,
            String teamName
    ) {
        this.id = UUID.randomUUID();
        this.registrationNumber = registrationNumber;
        this.editionId = editionId;
        this.representativeUserId = representativeUserId;
        this.representativeCpfEncrypted = representativeCpfEncrypted;
        this.athleteName = athleteName;
        this.athleteInstagram = athleteInstagram;
        this.athleteInstagramNormalized = athleteInstagramNormalized;
        this.eventId = eventId;
        this.divisionId = divisionId;
        this.divisionName = divisionName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.teamId = teamId;
        this.teamName = teamName;
        this.status = AwardRegistrationStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();

        if (id == null) {
            id = UUID.randomUUID();
        }

        if (status == null) {
            status = AwardRegistrationStatus.DRAFT;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void submit(Instant now) {
        if (status == AwardRegistrationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cancelled registration cannot be submitted."
            );
        }

        status = AwardRegistrationStatus.SUBMITTED;
        submittedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public long getRegistrationNumber() {
        return registrationNumber;
    }

    public UUID getEditionId() {
        return editionId;
    }

    public UUID getRepresentativeUserId() {
        return representativeUserId;
    }

    public String getRepresentativeCpfEncrypted() {
        return representativeCpfEncrypted;
    }

    public String getAthleteName() {
        return athleteName;
    }

    public String getAthleteInstagram() {
        return athleteInstagram;
    }

    public String getAthleteInstagramNormalized() {
        return athleteInstagramNormalized;
    }

    public long getEventId() {
        return eventId;
    }

    public long getDivisionId() {
        return divisionId;
    }

    public String getDivisionName() {
        return divisionName;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public AwardRegistrationStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void cancel(){
        this.status = AwardRegistrationStatus.CANCELLED;
    }

    public void reopenForEditing() {
        this.status = AwardRegistrationStatus.DRAFT;
        this.submittedAt = null;
    };
}

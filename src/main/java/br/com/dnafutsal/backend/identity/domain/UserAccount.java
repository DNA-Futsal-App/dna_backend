package br.com.dnafutsal.backend.identity.domain;

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
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 16)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "child_instagram", length = 64)
    private String childInstagram;

    @Column(name = "followed_category_id", length = 100)
    private String followedCategoryId;

    @Column(name = "followed_division_id", length = 100)
    private String followedDivisionId;

    @Column(name = "followed_team_id", length = 100)
    private String followedTeamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    public UserAccount(String name, String email, String phone, String passwordHash,
                       String childInstagram, String followedCategoryId,
                       String followedDivisionId, String followedTeamId) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.childInstagram = childInstagram;
        this.followedCategoryId = followedCategoryId;
        this.followedDivisionId = followedDivisionId;
        this.followedTeamId = followedTeamId;
        this.status = UserStatus.PENDING_EMAIL_VERIFICATION;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void activate(Instant now) {
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = now;
    }

    public void updateProfile(String name, String email, String phone, String childInstagram,
                              String categoryId, String divisionId, String teamId) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.childInstagram = childInstagram;
        this.followedCategoryId = categoryId;
        this.followedDivisionId = divisionId;
        this.followedTeamId = teamId;
    }

    public void requireEmailVerification() {
        this.status = UserStatus.PENDING_EMAIL_VERIFICATION;
        this.emailVerifiedAt = null;
        this.tokenVersion++;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.tokenVersion++;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getChildInstagram() {
        return childInstagram;
    }

    public String getFollowedCategoryId() {
        return followedCategoryId;
    }

    public String getFollowedDivisionId() {
        return followedDivisionId;
    }

    public String getFollowedTeamId() {
        return followedTeamId;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

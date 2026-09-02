package br.com.dnafutsal.backend.sports.notification.domain;

import br.com.dnafutsal.backend.sports.domain.MatchView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sports_change_events")
public class SportsChangeEvent {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 50
    )
    private SportsChangeType type;

    @Column(
            name = "event_id",
            nullable = false
    )
    private long eventId;

    @Column(
            name = "match_id",
            nullable = false,
            length = 180
    )
    private String matchId;

    @Column(
            name = "home_team_id",
            length = 100
    )
    private String homeTeamId;

    @Column(
            name = "away_team_id",
            length = 100
    )
    private String awayTeamId;

    @Column(name = "previous_scheduled_date")
    private LocalDate previousScheduledDate;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "previous_scheduled_at")
    private Instant previousScheduledAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "previous_home_score")
    private Integer previousHomeScore;

    @Column(name = "previous_away_score")
    private Integer previousAwayScore;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(
            name = "occurred_at",
            nullable = false
    )
    private Instant occurredAt;

    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected SportsChangeEvent() {
    }

    private SportsChangeEvent(
            SportsChangeType type,
            SportsMatchState previous,
            MatchView current,
            Instant now
    ) {
        this.id = UUID.randomUUID();

        this.type = type;

        this.eventId =
                current.eventId();

        this.matchId =
                current.id();

        this.homeTeamId =
                current.homeTeam() == null
                        ? null
                        : current.homeTeam().id();

        this.awayTeamId =
                current.awayTeam() == null
                        ? null
                        : current.awayTeam().id();

        if (previous != null) {
            this.previousScheduledDate =
                    previous.getScheduledDate();

            this.previousScheduledAt =
                    previous.getScheduledAt();

            this.previousHomeScore =
                    previous.getHomeScore();

            this.previousAwayScore =
                    previous.getAwayScore();
        }

        this.scheduledDate =
                current.scheduledDate();

        this.scheduledAt =
                current.scheduledAt();

        this.homeScore =
                current.homeScore();

        this.awayScore =
                current.awayScore();

        this.occurredAt = now;
        this.createdAt = now;
    }

    public static SportsChangeEvent of(
            SportsChangeType type,
            SportsMatchState previous,
            MatchView current,
            Instant now
    ) {
        return new SportsChangeEvent(
                type,
                previous,
                current,
                now
        );
    }

    public void processed(
            Instant now
    ) {
        this.processedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public SportsChangeType getType() {
        return type;
    }

    public long getEventId() {
        return eventId;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getHomeTeamId() {
        return homeTeamId;
    }

    public String getAwayTeamId() {
        return awayTeamId;
    }

    public LocalDate getPreviousScheduledDate() {
        return previousScheduledDate;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public Instant getPreviousScheduledAt() {
        return previousScheduledAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Integer getPreviousHomeScore() {
        return previousHomeScore;
    }

    public Integer getPreviousAwayScore() {
        return previousAwayScore;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }
}
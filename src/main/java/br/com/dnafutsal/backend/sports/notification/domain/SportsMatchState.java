package br.com.dnafutsal.backend.sports.notification.domain;

import br.com.dnafutsal.backend.sports.domain.MatchStatus;
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
@Table(name = "sports_match_state")
public class SportsMatchState {

    @Id
    private UUID id;

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

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 40
    )
    private MatchStatus status;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(
            name = "first_seen_at",
            nullable = false
    )
    private Instant firstSeenAt;

    @Column(
            name = "last_seen_at",
            nullable = false
    )
    private Instant lastSeenAt;

    protected SportsMatchState() {
    }

    public SportsMatchState(
            long eventId,
            MatchView match,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.matchId = match.id();

        applyMatch(match);

        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    public void refresh(
            MatchView match,
            Instant now
    ) {
        applyMatch(match);
        this.lastSeenAt = now;
    }

    private void applyMatch(
            MatchView match
    ) {
        this.homeTeamId =
                match.homeTeam() == null
                        ? null
                        : match.homeTeam().id();

        this.awayTeamId =
                match.awayTeam() == null
                        ? null
                        : match.awayTeam().id();

        this.scheduledDate =
                match.scheduledDate();

        this.scheduledAt =
                match.scheduledAt();

        this.status =
                match.status();

        this.homeScore =
                match.homeScore();

        this.awayScore =
                match.awayScore();
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

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }
}
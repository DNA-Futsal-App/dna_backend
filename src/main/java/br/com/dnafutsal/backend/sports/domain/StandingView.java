package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record StandingView(
        int position,
        TeamView team,
        int played,
        int wins,
        int draws,
        int losses,
        int goalsFor,
        int goalsAgainst,
        int goalDifference,
        int points
) implements Serializable {
}

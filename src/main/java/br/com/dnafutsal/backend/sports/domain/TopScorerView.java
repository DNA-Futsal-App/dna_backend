package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record TopScorerView(
        int position,
        String athleteId,
        String athleteName,
        TeamView team,
        int goals,
        int matches
) implements Serializable {
}

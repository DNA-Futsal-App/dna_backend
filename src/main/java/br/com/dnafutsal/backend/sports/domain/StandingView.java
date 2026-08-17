package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record StandingView(
        String phase,
        String group,
        Integer position,
        TeamView team,
        Integer played,
        Integer wins,
        Integer draws,
        Integer losses,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer goalDifference,
        Integer points,
        Double average,
        Double goalsForAverage,
        Double goalsAgainstAverage,
        Double technicalIndex
) implements Serializable {
}

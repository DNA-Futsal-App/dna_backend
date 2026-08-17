package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record TopScorerView(
        int position,
        String phase,
        String athleteName,
        String athleteImageUrl,
        TeamView team,
        Integer goals,
        boolean personalDataSuppressed
) implements Serializable {
}

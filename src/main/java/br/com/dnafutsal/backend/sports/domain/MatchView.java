package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;
import java.time.Instant;

public record MatchView(
        String id,
        String competitionName,
        String categoryId,
        String categoryName,
        String divisionId,
        String divisionName,
        String round,
        TeamView homeTeam,
        TeamView awayTeam,
        Integer homeScore,
        Integer awayScore,
        Instant scheduledAt,
        String status,
        String venue
) implements Serializable {
}

package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

public record MatchView(
        String id,
        long eventId,
        String competitionName,
        int season,
        String category,
        String division,
        String phase,
        TeamView homeTeam,
        TeamView awayTeam,
        Integer homeScore,
        Integer awayScore,

        LocalDate scheduledDate,
        Instant scheduledAt,

        MatchStatus status,

        boolean walkover,
        String venue,
        String matchSheetUrl
) implements Serializable {
}
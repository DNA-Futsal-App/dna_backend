package br.com.dnafutsal.backend.sports.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

record ScraperEvent(
        long eventId,
        String title,
        int season,
        String category,
        String division,
        String sourceUrl
) {
}

record ScraperStanding(
        String phase,
        String group,
        Integer position,
        String team,
        String logoUrl,
        Integer points,
        Integer games,
        Integer wins,
        Integer draws,
        Integer losses,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer goalDifference,
        Double average,
        Double goalsForAverage,
        Double goalsAgainstAverage,
        Double technicalIndex
) {
}

record ScraperGame(
        Long gameId,
        String phase,
        LocalDate date,
        LocalTime time,
        String venue,
        String homeTeam,
        String homeLogoUrl,
        Integer homeScore,
        String awayTeam,
        String awayLogoUrl,
        Integer awayScore,
        boolean walkover,
        String matchSheetUrl
) {
}

record ScraperTeam(
        long teamId,
        String name,
        String logoUrl,
        String sourceUrl
) {
}

record ScraperScorer(
        String phase,
        String player,
        String playerImageUrl,
        String team,
        String teamLogoUrl,
        Integer goals,
        boolean personalDataSuppressed
) {
}

record ScraperSnapshot(
        ScraperEvent event,
        List<ScraperStanding> standings,
        List<ScraperGame> games,
        List<ScraperTeam> teams,
        List<ScraperScorer> scorers,
        Instant collectedAt
) {
}

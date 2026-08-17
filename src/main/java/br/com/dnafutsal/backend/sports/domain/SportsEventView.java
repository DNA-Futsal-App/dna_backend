package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record SportsEventView(
        long eventId,
        String title,
        int season,
        String category,
        String division,
        String sourceUrl
) implements Serializable {
}

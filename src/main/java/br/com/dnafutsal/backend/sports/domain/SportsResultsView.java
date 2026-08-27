package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record SportsResultsView(
        String division,
        String category,
        Object data
) implements Serializable {
}

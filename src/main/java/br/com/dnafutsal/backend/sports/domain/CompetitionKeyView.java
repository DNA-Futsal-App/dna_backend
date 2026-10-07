package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record CompetitionKeyView(
        String teamId,
        String teamName,
        CompetitionKey key
) implements Serializable {
}

package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record SportsFilter(String categoryId, String divisionId, String teamId) implements Serializable {

    public String cacheKey() {
        return safe(categoryId) + ":" + safe(divisionId) + ":" + safe(teamId);
    }

    private String safe(String value) {
        return value == null ? "all" : value.replace(":", "_");
    }
}

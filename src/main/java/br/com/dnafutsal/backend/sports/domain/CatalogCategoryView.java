package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record CatalogCategoryView(
        long id,
        String name,
        long eventId
) implements Serializable {
}
package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record CatalogItemView(
        long id,
        String name
) implements Serializable {
}
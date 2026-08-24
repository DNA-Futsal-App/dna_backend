package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record CatalogItemView(
        String id,
        String name
) implements Serializable {
}
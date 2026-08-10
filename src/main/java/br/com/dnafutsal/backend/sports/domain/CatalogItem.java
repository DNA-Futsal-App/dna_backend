package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record CatalogItem(String id, String name, String logoUrl) implements Serializable {
}

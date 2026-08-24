package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;

public record SportsCatalogOption(
        String id,
        String name
) implements Serializable {

    public SportsCatalogOption {
        id = clean(id);
        name = clean(name);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }
}

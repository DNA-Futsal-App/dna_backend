package br.com.dnafutsal.backend.sports.domain;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record SportsEventSearch(
        int season,
        String title,
        String division,
        String category
) implements Serializable {

    public SportsEventSearch {
        title = trimToNull(title);
        division = trimToNull(division);
        category = trimToNull(category);
    }

    public String cacheKey() {
        return season + ":" + encode(title) + ":" + encode(division) + ":" + encode(category);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String encode(String value) {
        return value == null
                ? "~"
                : Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}

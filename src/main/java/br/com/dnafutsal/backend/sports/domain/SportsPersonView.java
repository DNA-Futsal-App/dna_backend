package br.com.dnafutsal.backend.sports.domain;

public record SportsPersonView(
        String name,
        String secondaryName,
        String role,
        String imageUrl
) {
}

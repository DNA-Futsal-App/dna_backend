package br.com.dnafutsal.backend.news.domain;

import java.time.Instant;

public record NewsArticle(
        String id,
        String slug,
        String title,
        String summary,
        String content,
        String coverImageUrl,
        String authorName,
        Instant publishedAt
) {
}

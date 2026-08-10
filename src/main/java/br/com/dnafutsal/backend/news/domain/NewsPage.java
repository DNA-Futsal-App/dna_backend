package br.com.dnafutsal.backend.news.domain;

import java.util.List;

public record NewsPage(
        List<NewsArticle> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}

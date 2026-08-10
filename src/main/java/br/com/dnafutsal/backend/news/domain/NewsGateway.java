package br.com.dnafutsal.backend.news.domain;

public interface NewsGateway {
    NewsPage published(int page, int size);

    NewsArticle findBySlug(String slug);
}

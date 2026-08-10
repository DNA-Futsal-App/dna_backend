package br.com.dnafutsal.backend.news.application;

import br.com.dnafutsal.backend.news.domain.NewsArticle;
import br.com.dnafutsal.backend.news.domain.NewsGateway;
import br.com.dnafutsal.backend.news.domain.NewsPage;
import org.springframework.stereotype.Service;

@Service
public class NewsService {

    private final NewsGateway gateway;

    public NewsService(NewsGateway gateway) {
        this.gateway = gateway;
    }

    public NewsPage published(int page, int size) {
        return gateway.published(page, size);
    }

    public NewsArticle findBySlug(String slug) {
        return gateway.findBySlug(slug);
    }
}

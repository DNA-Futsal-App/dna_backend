package br.com.dnafutsal.backend.news.infrastructure;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.IntegrationProperties;
import br.com.dnafutsal.backend.news.domain.NewsArticle;
import br.com.dnafutsal.backend.news.domain.NewsGateway;
import br.com.dnafutsal.backend.news.domain.NewsPage;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpNewsGateway implements NewsGateway {

    private final RestClient client;

    public HttpNewsGateway(RestClient.Builder builder, IntegrationProperties properties) {
        RestClient.Builder configured = builder.baseUrl(properties.news().baseUrl());
        if (properties.news().apiKey() != null && !properties.news().apiKey().isBlank()) {
            configured.defaultHeader("X-Api-Key", properties.news().apiKey());
        }
        this.client = configured.build();
    }

    @Override
    public NewsPage published(int page, int size) {
        try {
            NewsPage response = client.get()
                    .uri(uri -> uri.path("/api/v1/public/news")
                            .queryParam("page", page).queryParam("size", size)
                            .queryParam("sort", "publishedAt,desc").build())
                    .retrieve().body(NewsPage.class);
            if (response == null) {
                throw Errors.dependencyUnavailable("NEWS_UNAVAILABLE", "As notícias estão indisponíveis.");
            }
            return response;
        } catch (br.com.dnafutsal.backend.common.BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw Errors.dependencyUnavailable("NEWS_UNAVAILABLE",
                    "As notícias estão temporariamente indisponíveis.");
        }
    }

    @Override
    public NewsArticle findBySlug(String slug) {
        try {
            NewsArticle response = client.get()
                    .uri("/api/v1/public/news/{slug}", slug)
                    .retrieve().body(NewsArticle.class);
            if (response == null) {
                throw Errors.notFound("NEWS_NOT_FOUND", "Notícia não encontrada.");
            }
            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw Errors.notFound("NEWS_NOT_FOUND", "Notícia não encontrada.");
        } catch (br.com.dnafutsal.backend.common.BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw Errors.dependencyUnavailable("NEWS_UNAVAILABLE",
                    "As notícias estão temporariamente indisponíveis.");
        }
    }
}

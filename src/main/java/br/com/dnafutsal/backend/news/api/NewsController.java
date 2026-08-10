package br.com.dnafutsal.backend.news.api;

import br.com.dnafutsal.backend.news.application.NewsService;
import br.com.dnafutsal.backend.news.domain.NewsArticle;
import br.com.dnafutsal.backend.news.domain.NewsPage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService news;

    public NewsController(NewsService news) {
        this.news = news;
    }

    @GetMapping
    NewsPage published(@RequestParam(defaultValue = "0") @Min(0) int page,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return news.published(page, size);
    }

    @GetMapping("/{slug}")
    NewsArticle bySlug(@PathVariable @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug) {
        return news.findBySlug(slug);
    }
}

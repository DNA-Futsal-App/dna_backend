package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.SportsCatalogOption;
import br.com.dnafutsal.backend.sports.domain.SportsResultsView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1")
public class SportsScraperCatalogController {

    private final SportsCatalogService catalog;

    public SportsScraperCatalogController(SportsCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/divisions")
    List<SportsCatalogOption> divisions() {
        return catalog.divisions();
    }

    @GetMapping("/categories")
    List<SportsCatalogOption> categories(
            @RequestParam @NotBlank @Size(max = 100) String division
    ) {
        return catalog.categories(division);
    }

    @GetMapping
    SportsResultsView results(
            @RequestParam @NotBlank @Size(max = 100) String division,
            @RequestParam @NotBlank @Size(max = 100) String category
    ) {
        return catalog.results(division, category);
    }
}

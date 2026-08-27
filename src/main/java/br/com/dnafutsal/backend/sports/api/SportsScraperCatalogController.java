package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    List<CatalogItemView> divisions(
            @RequestParam @NotBlank Integer season
    ) {
        return catalog.divisions(season);
    }

    @GetMapping("/categories")
    List<CatalogCategoryView> categories(
            @RequestParam
            @Min(2016)
            @Max(2100)
            int season,
            @RequestParam @NotBlank @Size(max = 100) Integer division
    ) {
        return catalog.categories(season,division);
    }

}

package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogCategoryView;
import br.com.dnafutsal.backend.sports.domain.CatalogItemView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping(
        "/api/v1/public/catalog"
)
public class SportsPublicCatalogController {

    private final SportsCatalogService catalog;

    public SportsPublicCatalogController(
            SportsCatalogService catalog
    ) {
        this.catalog = catalog;
    }

    @GetMapping("/divisions")
    List<CatalogItemView> divisions(
            @RequestParam
            @Min(2016)
            @Max(2100)
            int season
    ) {
        return catalog.divisions(
                season
        );
    }

    @GetMapping("/categories")
    List<CatalogCategoryView> categories(
            @RequestParam
            @Min(2016)
            @Max(2100)
            int season,

            @RequestParam
            @Positive
            long divisionId
    ) {
        return catalog.categories(
                season,
                divisionId
        );
    }

    @GetMapping("/teams")
    List<TeamView> teams(
            @RequestParam
            @Positive
            long eventId
    ) {
        return catalog.teams(eventId);
    }
}
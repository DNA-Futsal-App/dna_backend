package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.CatalogItem;
import br.com.dnafutsal.backend.sports.domain.TeamView;
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
@RequestMapping("/api/v1/public/catalog")
public class SportsCatalogController {

    private final SportsCatalogService catalog;

    public SportsCatalogController(SportsCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    List<CatalogItem> categories() {
        return catalog.categories();
    }

    @GetMapping("/divisions")
    List<CatalogItem> divisions(@RequestParam @NotBlank @Size(max = 100) String categoryId) {
        return catalog.divisions(categoryId);
    }

    @GetMapping("/teams")
    List<TeamView> teams(@RequestParam @NotBlank @Size(max = 100) String categoryId,
                         @RequestParam @NotBlank @Size(max = 100) String divisionId) {
        return catalog.teams(categoryId, divisionId);
    }
}

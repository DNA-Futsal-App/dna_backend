package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.sports.application.SportsCatalogService;
import br.com.dnafutsal.backend.sports.domain.SportsEventSearch;
import br.com.dnafutsal.backend.sports.domain.SportsEventView;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/public/events")
public class SportsCatalogController {

    private final SportsCatalogService catalog;

    public SportsCatalogController(SportsCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<SportsEventView> search(
            @RequestParam @Min(2016) @Max(2100) int season,
            @RequestParam(required = false) @Size(max = 150) String title,
            @RequestParam(required = false) @Size(max = 100) String division,
            @RequestParam(required = false) @Size(max = 100) String category
    ) {
        return catalog.search(new SportsEventSearch(season, title, division, category));
    }

    @GetMapping("/{eventId}")
    SportsEventView event(@PathVariable @Positive long eventId) {
        return catalog.event(eventId);
    }

    @GetMapping("/{eventId}/teams")
    List<TeamView> teams(@PathVariable @Positive long eventId) {
        return catalog.teams(eventId);
    }
}

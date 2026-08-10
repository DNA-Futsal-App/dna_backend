package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.sports.application.SportsFilterResolver;
import br.com.dnafutsal.backend.sports.application.SportsQueryService;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
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
public class SportsController {

    private final CurrentUserService currentUser;
    private final SportsFilterResolver filters;
    private final SportsQueryService sports;

    public SportsController(CurrentUserService currentUser, SportsFilterResolver filters,
                            SportsQueryService sports) {
        this.currentUser = currentUser;
        this.filters = filters;
        this.sports = sports;
    }

    @GetMapping("/matches/played")
    List<MatchView> played(@RequestParam(required = false) @Size(max = 100) String categoryId,
                           @RequestParam(required = false) @Size(max = 100) String divisionId,
                           @RequestParam(required = false) @Size(max = 100) String teamId) {
        return sports.playedMatches(resolve(categoryId, divisionId, teamId));
    }

    @GetMapping("/matches/upcoming")
    List<MatchView> upcoming(@RequestParam(required = false) @Size(max = 100) String categoryId,
                             @RequestParam(required = false) @Size(max = 100) String divisionId,
                             @RequestParam(required = false) @Size(max = 100) String teamId) {
        return sports.upcomingMatches(resolve(categoryId, divisionId, teamId));
    }

    @GetMapping("/standings")
    List<StandingView> standings(@RequestParam(required = false) @Size(max = 100) String categoryId,
                                 @RequestParam(required = false) @Size(max = 100) String divisionId,
                                 @RequestParam(required = false) @Size(max = 100) String teamId) {
        return sports.standings(resolve(categoryId, divisionId, teamId));
    }

    @GetMapping("/top-scorers")
    List<TopScorerView> topScorers(@RequestParam(required = false) @Size(max = 100) String categoryId,
                                   @RequestParam(required = false) @Size(max = 100) String divisionId,
                                   @RequestParam(required = false) @Size(max = 100) String teamId) {
        return sports.topScorers(resolve(categoryId, divisionId, teamId));
    }

    private SportsFilter resolve(String categoryId, String divisionId, String teamId) {
        return filters.resolve(currentUser.userId(), categoryId, divisionId, teamId);
    }
}

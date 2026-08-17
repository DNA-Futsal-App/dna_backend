package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.sports.application.SportsFilterResolver;
import br.com.dnafutsal.backend.sports.application.SportsQueryService;
import br.com.dnafutsal.backend.sports.domain.MatchView;
import br.com.dnafutsal.backend.sports.domain.SportsFilter;
import br.com.dnafutsal.backend.sports.domain.StandingView;
import br.com.dnafutsal.backend.sports.domain.TopScorerView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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
    List<MatchView> played(
            @RequestParam(required = false) @Positive Long eventId,
            @RequestParam(required = false) @Size(max = 100) String teamId,
            @RequestParam(required = false) @Size(max = 150) String phase,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return sports.playedMatches(resolve(eventId, teamId), phase, from, to);
    }

    @GetMapping("/matches/upcoming")
    List<MatchView> upcoming(
            @RequestParam(required = false) @Positive Long eventId,
            @RequestParam(required = false) @Size(max = 100) String teamId,
            @RequestParam(required = false) @Size(max = 150) String phase,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return sports.upcomingMatches(resolve(eventId, teamId), phase, from, to);
    }

    @GetMapping("/standings")
    List<StandingView> standings(
            @RequestParam(required = false) @Positive Long eventId,
            @RequestParam(required = false) @Size(max = 100) String teamId,
            @RequestParam(required = false) @Size(max = 150) String phase,
            @RequestParam(required = false) @Size(max = 150) String group
    ) {
        return sports.standings(resolve(eventId, teamId), phase, group);
    }

    @GetMapping("/top-scorers")
    List<TopScorerView> topScorers(
            @RequestParam(required = false) @Positive Long eventId,
            @RequestParam(required = false) @Size(max = 100) String teamId,
            @RequestParam(required = false) @Size(max = 150) String phase,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        return sports.topScorers(resolve(eventId, teamId), phase, limit);
    }

    private SportsFilter resolve(Long eventId, String teamId) {
        return filters.resolve(currentUser.userId(), eventId, teamId);
    }
}

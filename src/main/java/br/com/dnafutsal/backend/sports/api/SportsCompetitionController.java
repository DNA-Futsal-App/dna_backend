package br.com.dnafutsal.backend.sports.api;

import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.sports.application.SportsCompetitionService;
import br.com.dnafutsal.backend.sports.application.SportsFilterResolver;
import br.com.dnafutsal.backend.sports.domain.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Distinct stage-aware endpoints: existing routes remain backward compatible. */
@Validated
@RestController
@RequestMapping("/api/v1/sports")
public class SportsCompetitionController {
    private final SportsCompetitionService service;
    private final SportsFilterResolver filters;
    private final CurrentUserService currentUser;

    public SportsCompetitionController(SportsCompetitionService service,
                                       SportsFilterResolver filters, CurrentUserService currentUser) {
        this.service = service;
        this.filters = filters;
        this.currentUser = currentUser;
    }

    @GetMapping("/structure")
    public CompetitionStructureView structure(@RequestParam @Positive long eventId) {
        return service.structure(eventId);
    }

    @GetMapping("/matches")
    public MatchCalendarView matches(@RequestParam @Positive long eventId,
                                    @RequestParam String stage,
                                    @RequestParam(required = false) @Size(max = 150) String phase,
                                    @RequestParam(required = false) @Size(max = 100) String teamId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(resolve(eventId, teamId), stage, phase, from, to);
    }

    @GetMapping("/matches/played")
    public List<MatchView> played(@RequestParam @Positive long eventId, @RequestParam String stage,
                                  @RequestParam(required = false) String phase,
                                  @RequestParam(required = false) String teamId) {
        return service.played(resolve(eventId, teamId), stage, phase, null, null);
    }

    @GetMapping("/matches/upcoming")
    public List<MatchView> upcoming(@RequestParam @Positive long eventId, @RequestParam String stage,
                                    @RequestParam(required = false) String phase,
                                    @RequestParam(required = false) String teamId) {
        return service.upcoming(resolve(eventId, teamId), stage, phase, null, null);
    }

    @GetMapping("/matches/pending")
    public List<MatchView> pending(@RequestParam @Positive long eventId, @RequestParam String stage,
                                   @RequestParam(required = false) String phase,
                                   @RequestParam(required = false) String teamId) {
        return service.pending(resolve(eventId, teamId), stage, phase, null, null);
    }

    @GetMapping("/standings")
    public List<StandingView> standings(@RequestParam @Positive long eventId, @RequestParam String stage,
                                       @RequestParam(required = false) String phase,
                                       @RequestParam(required = false) String group,
                                       @RequestParam(required = false) String teamId) {
        return service.standings(resolve(eventId, teamId), stage, phase, group);
    }

    @GetMapping("/top-scorers")
    public List<TopScorerView> scorers(@RequestParam @Positive long eventId, @RequestParam String stage,
                                      @RequestParam(required = false) String phase,
                                      @RequestParam(required = false) String teamId,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(500) int limit) {
        return service.scorers(resolve(eventId, teamId), stage, phase, limit);
    }

    private SportsFilter resolve(long eventId, String teamId) {
        return filters.resolveCompetition(currentUser.userId(), eventId, teamId);
    }
}

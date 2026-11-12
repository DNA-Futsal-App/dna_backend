package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachCategoryLinkService;
import jakarta.validation.constraints.Size;
import br.com.dnafutsal.backend.awards.application.CoachCategoryApplicationService;
import br.com.dnafutsal.backend.sports.domain.TeamView;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/public/awards/coach-category-links")
public class PublicCoachCategoryLinkController {
    private final CoachCategoryLinkService service;
    private final CoachCategoryApplicationService applications;

    public PublicCoachCategoryLinkController(CoachCategoryLinkService service,
                                             CoachCategoryApplicationService applications) {
        this.service = service;
        this.applications = applications;
    }

    @GetMapping("/{token}")
    public CoachCategoryLinkService.LinkView inspect(@PathVariable @Size(min = 20, max = 200) String token) {
        return service.inspect(token);
    }
    @GetMapping("/{token}/teams")
    public List<TeamView> teams(@PathVariable @Size(min = 20, max = 200) String token) {
        return applications.teams(token);
    }
}

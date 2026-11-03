package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardPopularVotingService;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/awards/editions/{editionId}/popular-voting")
public class AdminAwardPopularVotingController {
    private final AwardPopularVotingService service;
    public AdminAwardPopularVotingController(AwardPopularVotingService service) { this.service = service; }

    @GetMapping("/contexts")
    public List<AwardPopularContextResponse> contexts(@PathVariable UUID editionId) {
        return service.adminContexts(editionId);
    }

    @GetMapping("/readiness")
    public AwardPopularReadinessResponse readiness(@PathVariable UUID editionId,
            @RequestParam @Min(1) long eventId, @RequestParam @Min(1) long divisionId,
            @RequestParam @Min(1) long categoryId, @RequestParam AwardRegistrationGender gender,
            @RequestParam AwardRegistrationContestCategory contestCategory) {
        return service.readiness(editionId,
                new AwardPopularContext(eventId, divisionId, categoryId, gender, contestCategory));
    }

    @GetMapping("/window")
    public AwardPopularContextWindowResponse window(@PathVariable UUID editionId,
            @RequestParam @Min(1) long eventId, @RequestParam @Min(1) long divisionId,
            @RequestParam @Min(1) long categoryId, @RequestParam AwardRegistrationGender gender,
            @RequestParam AwardRegistrationContestCategory contestCategory) {
        return service.adminWindow(editionId,
                new AwardPopularContext(eventId, divisionId, categoryId, gender, contestCategory));
    }

    @PostMapping("/window")
    public AwardPopularContextWindowResponse schedule(@PathVariable UUID editionId,
            @Valid @RequestBody AwardPopularContextWindowRequest request) {
        return service.schedule(editionId, request);
    }

    @PostMapping("/close")
    public AwardPopularContextWindowResponse close(@PathVariable UUID editionId,
            @Valid @RequestBody AwardPopularContext context) {
        return service.close(editionId, context);
    }
}

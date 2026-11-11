package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardGoldEligibilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/awards/editions/{editionId}/gold-eligibility")
public class AdminAwardGoldEligibilityController {
    private final AwardGoldEligibilityService eligibility;

    public AdminAwardGoldEligibilityController(AwardGoldEligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    @GetMapping("/preview")
    public AwardGoldEligibilityService.Report preview(@PathVariable UUID editionId,
                                                       @RequestParam @Positive long eventId,
                                                       @RequestParam @Positive long divisionId,
                                                       @RequestParam @Positive long categoryId,
                                                       @RequestParam(defaultValue = "PAULISTA") String stage) {
        return eligibility.preview(editionId, eventId, divisionId, categoryId, stage);
    }

    @GetMapping
    public AwardGoldEligibilityService.Report prepared(@PathVariable UUID editionId,
                                                        @RequestParam @Positive long eventId,
                                                        @RequestParam @Positive long divisionId,
                                                        @RequestParam @Positive long categoryId) {
        return eligibility.prepared(editionId, eventId, divisionId, categoryId);
    }

    @PostMapping
    public AwardGoldEligibilityService.Report prepare(@PathVariable UUID editionId,
                                                       @Valid @RequestBody PrepareRequest request) {
        return eligibility.prepare(editionId, request.eventId(), request.divisionId(),
                request.categoryId(), request.stage());
    }

    public record PrepareRequest(@Positive long eventId, @Positive long divisionId,
                                 @Positive long categoryId, @NotBlank String stage) { }
}

package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachCategoryApplicationService;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/awards/editions/{editionId}/coach-category-applications")
public class AdminCoachCategoryApplicationController {
    private final CoachCategoryApplicationService service;

    public AdminCoachCategoryApplicationController(CoachCategoryApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<CoachCategoryApplicationService.ApplicationView> list(
            @PathVariable UUID editionId, @RequestParam(required = false) String status) {
        return service.adminList(editionId, status);
    }

    public record RevokeRequest(@NotBlank @Size(max = 500) String reason) { }

    @PostMapping("/{applicationId}/revoke")
    public CoachCategoryApplicationService.RevocationResult revoke(
            @PathVariable UUID editionId, @PathVariable UUID applicationId,
            @Valid @RequestBody RevokeRequest request) {
        return service.revoke(editionId, applicationId, request.reason());
    }

    @PostMapping("/credentials/{credentialId}/revoke")
    public CoachCategoryApplicationService.RevocationResult revokeCoach(
            @PathVariable UUID editionId, @PathVariable UUID credentialId,
            @Valid @RequestBody RevokeRequest request) {
        return service.revokeCoach(editionId, credentialId, request.reason());
    }

    public record ExtraCategoryRequest(@Positive long eventId, @Positive long divisionId,
                                       @Positive long categoryId,
                                       @NotNull AwardRegistrationGender gender,
                                       @NotBlank @Size(max = 100) String teamId) { }

    @PostMapping("/credentials/{credentialId}/categories")
    public CoachCategoryApplicationService.ApplicationView addCategory(
            @PathVariable UUID editionId, @PathVariable UUID credentialId,
            @Valid @RequestBody ExtraCategoryRequest request) {
        return service.adminAddCategory(editionId, credentialId,
                new CoachCategoryApplicationService.ExtraCategoryRequest(request.eventId(),
                        request.divisionId(), request.categoryId(), request.gender(), request.teamId()));
    }
}

package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachSharedAccessService;
import br.com.dnafutsal.backend.awards.application.CoachCategoryApplicationService;
import br.com.dnafutsal.backend.common.Errors;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/awards")
public class AdminCoachAccessController {
    private final CoachSharedAccessService access;
    private final CoachCategoryApplicationService categoryApplications;

    public AdminCoachAccessController(CoachSharedAccessService access,
                                      CoachCategoryApplicationService categoryApplications) {
        this.access = access;
        this.categoryApplications = categoryApplications;
    }

    @GetMapping("/editions/{editionId}/coach-access-link")
    CoachAccessLinkInfoResponse link(@PathVariable UUID editionId) {
        return access.linkStatus(editionId);
    }

    @PostMapping("/editions/{editionId}/coach-access-link")
    ResponseEntity<CreateCoachAccessLinkResponse> create(@PathVariable UUID editionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(access.createLink(editionId));
    }

    @PostMapping("/editions/{editionId}/coach-access-link/revoke")
    CoachAccessLinkInfoResponse revoke(@PathVariable UUID editionId) {
        return access.revokeLink(editionId);
    }

    @GetMapping("/editions/{editionId}/coach-voters")
    List<CoachCredentialResponse> voters(@PathVariable UUID editionId) {
        return access.adminVoters(editionId);
    }

    @PostMapping("/coach-voters/{credentialId}/contexts")
    CoachCredentialResponse add(@PathVariable UUID credentialId, @Valid @RequestBody CreateCoachContextRequest request) {
        var created = categoryApplications.adminAddCategoryFromLegacy(credentialId, request);
        return access.adminVoters(created.editionId()).stream()
                .filter(coach -> coach.credentialId().equals(credentialId))
                .findFirst().orElseThrow(() -> Errors.notFound(
                        "COACH_CREDENTIAL_NOT_FOUND", "Credencial não encontrada."));
    }

    public record DeactivateRequest(String reason) { }

    @PostMapping("/coach-voters/{credentialId}/contexts/{contextId}/deactivate")
    CoachCategoryApplicationService.RevocationResult deactivateContext(
            @PathVariable UUID credentialId, @PathVariable UUID contextId,
            @RequestBody(required = false) DeactivateRequest request) {
        return categoryApplications.revokeByContext(credentialId, contextId,
                reason(request));
    }

    @PostMapping("/coach-voters/{credentialId}/deactivate")
    CoachCategoryApplicationService.RevocationResult deactivate(
            @PathVariable UUID credentialId,
            @RequestBody(required = false) DeactivateRequest request) {
        return categoryApplications.revokeByCredential(credentialId, reason(request));
    }

    private String reason(DeactivateRequest request) {
        return request == null || request.reason() == null || request.reason().isBlank()
                ? "Revogação administrativa pelo painel legado"
                : request.reason().trim();
    }
}

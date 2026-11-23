package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AdminCoachBallotsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** /api/v1/admin/** is restricted to SCOPE_ADMIN in SecurityConfiguration. */
@RestController
@RequestMapping("/api/v1/admin/awards/editions/{editionId}/coach-voters")
public class AdminCoachBallotsController {
    private final AdminCoachBallotsService service;

    public AdminCoachBallotsController(AdminCoachBallotsService service) {
        this.service = service;
    }

    @GetMapping("/{credentialId}/ballots")
    public ResponseEntity<AdminCoachBallotsResponse> ballots(@PathVariable UUID editionId,
                                                              @PathVariable UUID credentialId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.findByCredential(editionId, credentialId));
    }
}

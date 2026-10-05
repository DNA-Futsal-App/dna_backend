package br.com.dnafutsal.backend.awards.api;
import br.com.dnafutsal.backend.awards.application.CoachSharedAccessService;
import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/admin/awards")
public class AdminCoachAccessController {
 private final CoachSharedAccessService access; public AdminCoachAccessController(CoachSharedAccessService access){this.access=access;}
 @GetMapping("/editions/{editionId}/coach-access-link") CoachAccessLinkInfoResponse link(@PathVariable UUID editionId){return access.linkStatus(editionId);}
 @PostMapping("/editions/{editionId}/coach-access-link") ResponseEntity<CreateCoachAccessLinkResponse> create(@PathVariable UUID editionId){return ResponseEntity.status(HttpStatus.CREATED).body(access.createLink(editionId));}
 @PostMapping("/editions/{editionId}/coach-access-link/revoke") CoachAccessLinkInfoResponse revoke(@PathVariable UUID editionId){return access.revokeLink(editionId);}
 @GetMapping("/editions/{editionId}/coach-voters") List<CoachCredentialResponse> voters(@PathVariable UUID editionId){return access.adminVoters(editionId);}
 @PostMapping("/coach-voters/{credentialId}/contexts") CoachCredentialResponse add(@PathVariable UUID credentialId,@Valid @RequestBody CreateCoachContextRequest request){return access.adminAddContext(credentialId,request);}
 @PostMapping("/coach-voters/{credentialId}/contexts/{contextId}/deactivate") CoachCredentialResponse deactivateContext(@PathVariable UUID credentialId,@PathVariable UUID contextId){return access.deactivateContext(credentialId,contextId);}
 @PostMapping("/coach-voters/{credentialId}/deactivate") CoachCredentialResponse deactivate(@PathVariable UUID credentialId){return access.deactivateCredential(credentialId);}
}

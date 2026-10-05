package br.com.dnafutsal.backend.awards.api;
import br.com.dnafutsal.backend.awards.application.CoachSharedAccessService;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/awards/coach-access")
public class CoachAccessController {
 private final CoachSharedAccessService access; public CoachAccessController(CoachSharedAccessService access){this.access=access;}
 @PostMapping("/claim") CoachCredentialResponse claim(@Valid @RequestBody ClaimRequest request){return access.claimCurrentUser(request.token());}
 @GetMapping("/me") CoachCredentialResponse me(){return access.currentCredential();}
 @PostMapping("/contexts") CoachCredentialResponse create(@Valid @RequestBody CreateCoachContextRequest request){return access.addSelfContext(request);}
 public record ClaimRequest(@NotBlank @Size(min=20,max=200) String token){}
}

package br.com.dnafutsal.backend.awards.api;
import br.com.dnafutsal.backend.awards.application.CoachSharedAccessService;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/v1/public/awards/coach-access")
public class PublicCoachAccessController {
 private final CoachSharedAccessService access; public PublicCoachAccessController(CoachSharedAccessService access){this.access=access;}
 @GetMapping("/{token}") CoachAccessLinkInfoResponse inspect(@PathVariable @Size(min=20,max=200) String token){return access.inspect(token);}
}

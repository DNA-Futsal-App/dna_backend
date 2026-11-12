package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.CoachCategoryApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/awards/coach-category-applications")
public class CoachCategoryApplicationController {
    private final CoachCategoryApplicationService service;

    public CoachCategoryApplicationController(CoachCategoryApplicationService service) {
        this.service = service;
    }

    public record Request(@NotBlank @Size(min = 20, max = 200) String token,
                          @NotBlank @Size(max = 100) String teamId,
                          @NotBlank @Size(max = 80) String role,
                          @Size(max = 500) String evidenceUrl) { }

    @PostMapping
    public ResponseEntity<CoachCategoryApplicationService.ApplicationView> apply(
            @Valid @RequestBody Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.applyCurrentUser(request.token(), request.teamId(),
                        request.role(), request.evidenceUrl()));
    }

    @GetMapping("/me")
    public List<CoachCategoryApplicationService.ApplicationView> mine() {
        return service.mine();
    }
}

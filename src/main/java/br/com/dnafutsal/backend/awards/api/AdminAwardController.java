package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardCandidateAdminService;
import br.com.dnafutsal.backend.awards.application.AwardCandidateImportService;
import br.com.dnafutsal.backend.awards.application.AwardEditionAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/awards")
public class AdminAwardController {

    private final AwardEditionAdminService editions;
    private final AwardCandidateImportService importer;
    private final AwardCandidateAdminService candidates;

    public AdminAwardController(
            AwardEditionAdminService editions,
            AwardCandidateImportService importer,
            AwardCandidateAdminService candidates
    ) {
        this.editions = editions;
        this.importer = importer;
        this.candidates = candidates;
    }

    @GetMapping("/editions")
    List<AwardEditionAdminResponse> editions() {
        return editions.editions();
    }

    @GetMapping("/editions/{editionId}/vote-categories")
    List<AwardVoteCategoryResponse> voteCategories(
            @PathVariable UUID editionId
    ) {
        return editions.voteCategories(
                editionId
        );
    }

    @PostMapping("/candidates/import-team")
    ImportAwardTeamResponse importTeam(
            @Valid
            @RequestBody
            ImportAwardTeamRequest request
    ) {
        return importer.importTeam(
                request
        );
    }

    @GetMapping("/editions/{editionId}/candidates")
    List<AwardCandidateResponse> candidates(
            @PathVariable UUID editionId
    ) {
        return candidates.list(
                editionId
        );
    }

    @PatchMapping("/candidates/{candidateId}/position")
    AwardCandidateResponse assignPosition(
            @PathVariable UUID candidateId,
            @Valid
            @RequestBody
            UpdateAwardCandidatePositionRequest request
    ) {
        return candidates.assignPosition(
                candidateId,
                request.position()
        );
    }
}

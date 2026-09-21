package br.com.dnafutsal.backend.awards.api;

import br.com.dnafutsal.backend.awards.application.AwardAdminDashboardService;
import br.com.dnafutsal.backend.awards.application.AwardCandidateAdminService;
import br.com.dnafutsal.backend.awards.application.AwardCandidateImportService;
import br.com.dnafutsal.backend.awards.application.AwardCoachSyncService;
import br.com.dnafutsal.backend.awards.application.AwardEditionAdminService;
import br.com.dnafutsal.backend.awards.application.AwardResultsService;
import br.com.dnafutsal.backend.awards.application.AwardVotingResetService;
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
    private final AwardCoachSyncService coachSync;
    private final AwardCandidateAdminService candidates;
    private final AwardAdminDashboardService dashboard;
    private final AwardResultsService results;
    private final AwardVotingResetService votingReset;

    public AdminAwardController(
            AwardEditionAdminService editions,
            AwardCandidateImportService importer,
            AwardCoachSyncService coachSync,
            AwardCandidateAdminService candidates,
            AwardAdminDashboardService dashboard,
            AwardResultsService results,
            AwardVotingResetService votingReset
    ) {
        this.editions = editions;
        this.importer = importer;
        this.coachSync = coachSync;
        this.candidates = candidates;
        this.dashboard = dashboard;
        this.results = results;
        this.votingReset = votingReset;
    }

    @GetMapping("/editions")
    List<AwardEditionAdminResponse> editions() {
        return editions.editions();
    }

    @GetMapping("/editions/{editionId}/overview")
    AwardAdminOverviewResponse overview(
            @PathVariable UUID editionId
    ) {
        return dashboard.overview(editionId);
    }

    @GetMapping("/editions/{editionId}/coaches")
    List<AwardAdminCoachResponse> coaches(
            @PathVariable UUID editionId
    ) {
        return dashboard.coaches(editionId);
    }


    @GetMapping("/editions/{editionId}/audit")
    AwardAdminAuditResponse audit(
            @PathVariable UUID editionId
    ) {
        return results.audit(editionId);
    }

    @GetMapping("/editions/{editionId}/results")
    AwardAdminResultsResponse results(
            @PathVariable UUID editionId
    ) {
        return results.results(editionId);
    }

    @GetMapping("/editions/{editionId}/vote-categories")
    List<AwardVoteCategoryResponse> voteCategories(
            @PathVariable UUID editionId
    ) {
        return editions.voteCategories(
                editionId
        );
    }

    @PostMapping("/editions/{editionId}/open")
    AwardEditionAdminResponse open(
            @PathVariable UUID editionId,
            @Valid
            @RequestBody
            OpenAwardEditionRequest request
    ) {
        return editions.open(
                editionId,
                request
        );
    }

    @PostMapping("/editions/{editionId}/close")
    AwardEditionAdminResponse close(
            @PathVariable UUID editionId
    ) {
        return editions.close(
                editionId
        );
    }

    @PostMapping("/editions/{editionId}/reset-voting")
    ResetAwardVotingResponse resetVoting(
            @PathVariable UUID editionId
    ) {
        return votingReset.reset(
                editionId
        );
    }

    @PostMapping("/editions/{editionId}/coaches/sync")
    SyncAwardCoachesResponse syncCoaches(
            @PathVariable UUID editionId,
            @Valid
            @RequestBody
            SyncAwardCoachesRequest request
    ) {
        return coachSync.sync(
                editionId,
                request
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

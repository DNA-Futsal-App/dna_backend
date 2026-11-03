package br.com.dnafutsal.backend.awards.api;
import java.util.List;
public record AwardPopularReadinessResponse(AwardPopularContext context,
        int approvedVideos, long pendingReview, int activeAdministrators,
        boolean finalistsConfirmed, boolean registrationsOpen, boolean ready,
        List<String> blockers, List<AwardMissingAdminResponse> missingAdmins) { }

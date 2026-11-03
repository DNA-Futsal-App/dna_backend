package br.com.dnafutsal.backend.awards.api;
import java.util.List;
import java.util.UUID;
public record AwardMissingAdminResponse(UUID adminId, String adminName, String adminEmail,
                                        List<AwardMissingVideoResponse> missingVideos) { }

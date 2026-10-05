package br.com.dnafutsal.backend.awards.api;
import java.time.Instant; import java.util.UUID;
public record CoachAccessLinkInfoResponse(UUID linkId,boolean available,String status,UUID editionId,String editionSlug,String editionName,int season,Instant createdAt,Instant revokedAt){}

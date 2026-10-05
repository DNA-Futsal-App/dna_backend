package br.com.dnafutsal.backend.awards.api;
import java.time.Instant; import java.util.UUID;
public record CreateCoachAccessLinkResponse(UUID linkId,String accessUrl,Instant createdAt){}

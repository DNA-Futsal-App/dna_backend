package br.com.dnafutsal.backend.awards.api;
import java.time.Instant; import java.util.UUID;
public record CoachVoterContextResponse(UUID id,long eventId,long divisionId,long categoryId,String teamId,String teamName,String source,boolean active,boolean submitted,Instant createdAt){}

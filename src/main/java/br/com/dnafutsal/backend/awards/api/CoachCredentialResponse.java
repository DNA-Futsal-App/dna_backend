package br.com.dnafutsal.backend.awards.api;
import java.time.Instant; import java.util.*;
public record CoachCredentialResponse(UUID credentialId,UUID editionId,String editionSlug,String editionName,int season,UUID userId,String coachName,String email,boolean active,Instant createdAt,List<CoachVoterContextResponse> contexts){}

package br.com.dnafutsal.backend.awards.api;
import java.util.UUID;
public record AwardMissingVideoResponse(UUID entryId, String athleteName, long registrationNumber) { }

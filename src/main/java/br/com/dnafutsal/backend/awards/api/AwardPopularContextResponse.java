package br.com.dnafutsal.backend.awards.api;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationContestCategory;
import br.com.dnafutsal.backend.awards.domain.AwardRegistrationGender;
import java.time.Instant;

public record AwardPopularContextResponse(long eventId, long divisionId, String divisionName,
        long categoryId, String categoryName, AwardRegistrationGender gender,
        AwardRegistrationContestCategory contestCategory, String contestCategoryLabel,
        String status, Instant opensAt, Instant closesAt) { }

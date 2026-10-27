package br.com.dnafutsal.backend.identity.api;

import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String childInstagram,
        Long eventId,
        String categoryId,
        String divisionId,
        String teamId,
        boolean emailVerified,
        Instant createdAt,
        SportsCompetitionCode competitionCode
) implements Serializable {
    public UserProfileResponse(UUID id, String name, String email, String phone,
                               String childInstagram, Long eventId, String categoryId,
                               String divisionId, String teamId, boolean emailVerified,
                               Instant createdAt) {
        this(id, name, email, phone, childInstagram, eventId, categoryId,
                divisionId, teamId, emailVerified, createdAt, null);
    }

    public static UserProfileResponse from(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getChildInstagram(), user.getFollowedEventId(), user.getFollowedCategoryId(), user.getFollowedDivisionId(),
                user.getFollowedTeamId(), user.getEmailVerifiedAt() != null, user.getCreatedAt(),
                user.getFollowedCompetitionCode());
    }
}

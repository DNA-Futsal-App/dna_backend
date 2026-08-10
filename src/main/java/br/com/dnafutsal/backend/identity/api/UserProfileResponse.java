package br.com.dnafutsal.backend.identity.api;

import br.com.dnafutsal.backend.identity.domain.UserAccount;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String childInstagram,
        String categoryId,
        String divisionId,
        String teamId,
        boolean emailVerified,
        Instant createdAt
) implements Serializable {
    public static UserProfileResponse from(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getChildInstagram(), user.getFollowedCategoryId(), user.getFollowedDivisionId(),
                user.getFollowedTeamId(), user.getEmailVerifiedAt() != null, user.getCreatedAt());
    }
}

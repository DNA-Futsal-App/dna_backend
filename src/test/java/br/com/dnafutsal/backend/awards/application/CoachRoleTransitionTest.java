package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoachRoleTransitionTest {

    @Test
    void promotionAndRevocationAreIdempotentAndInvalidateOldTokens() {
        UserAccount user = new UserAccount("Técnico", "coach@example.com", "11999999999",
                "hash", null, null, null, null, null);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        long originalVersion = user.getTokenVersion();

        user.promoteToCoach();
        assertThat(user.getRole()).isEqualTo(UserRole.COACH);
        assertThat(user.getTokenVersion()).isEqualTo(originalVersion + 1);
        user.promoteToCoach();
        assertThat(user.getTokenVersion()).isEqualTo(originalVersion + 1);

        user.demoteFromCoach();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getTokenVersion()).isEqualTo(originalVersion + 2);
        user.demoteFromCoach();
        assertThat(user.getTokenVersion()).isEqualTo(originalVersion + 2);
    }
}

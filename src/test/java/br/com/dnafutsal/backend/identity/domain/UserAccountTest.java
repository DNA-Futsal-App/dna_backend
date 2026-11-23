package br.com.dnafutsal.backend.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTest {
    @Test
    void inviteActivatesPendingAccountWithoutVerifyingEmail() {
        UserAccount user = newUser();
        user.requireEmailVerification();
        long tokenVersion = user.getTokenVersion();

        user.activateViaCoachInvite();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getTokenVersion()).isEqualTo(tokenVersion + 1);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = "PENDING_EMAIL_VERIFICATION",
            mode = EnumSource.Mode.EXCLUDE)
    void inviteRejectsOtherStatusesWithoutChangingAccount(UserStatus status) {
        UserAccount user = newUser();
        Instant verifiedAt = Instant.parse("2026-01-01T00:00:00Z");
        user.activate(verifiedAt);
        ReflectionTestUtils.setField(user, "status", status);
        long tokenVersion = user.getTokenVersion();

        assertThatThrownBy(user::activateViaCoachInvite)
                .isInstanceOf(IllegalStateException.class);

        assertThat(user.getStatus()).isEqualTo(status);
        assertThat(user.getTokenVersion()).isEqualTo(tokenVersion);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void persistenceInitializesDefaultRoleAndMatchingTimestamps() {
        UserAccount user = new UserAccount();
        Instant before = Instant.now();

        user.onCreate();

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(ReflectionTestUtils.getField(user, "updatedAt"))
                .isEqualTo(user.getCreatedAt());
    }

    @Test
    void persistencePreservesCoachRole() {
        UserAccount user = newUser();
        user.promoteToCoach();

        user.onCreate();

        assertThat(user.getRole()).isEqualTo(UserRole.COACH);
    }

    private UserAccount newUser() {
        return new UserAccount("Pessoa", "teste@example.com", "+5511999999999",
                "hash", null, null, null, null, null);
    }
}

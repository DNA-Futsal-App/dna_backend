package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoachPendingLinkPolicyTest {
    private final Instant now = Instant.parse("2026-11-27T03:49:18Z");

    @Test
    void revokedLinkRequiresExplicitAdminConfirmationEvenWhenExpired() {
        assertThatThrownBy(() -> CoachCategoryApplicationService.validatePendingLink(
                "REVOKED", now.minusSeconds(1), now, true, true, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("COACH_PENDING_LINK_REVOKED"));
        assertThatThrownBy(() -> CoachCategoryApplicationService.validatePendingLink(
                "REVOKED", now.plusSeconds(60), now, false, true, true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("COACH_PENDING_LINK_REVOKED"));
        assertThatCode(() -> CoachCategoryApplicationService.validatePendingLink(
                "REVOKED", now.minusSeconds(1), now, true, false, true))
                .doesNotThrowAnyException();
    }

    @Test
    void activeExpiredLinkStillRequiresItsOwnConfirmation() {
        assertThatThrownBy(() -> CoachCategoryApplicationService.validatePendingLink(
                "ACTIVE", now, now, true, false, true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("COACH_PENDING_LINK_EXPIRED"));
        assertThatCode(() -> CoachCategoryApplicationService.validatePendingLink(
                "ACTIVE", now, now, true, true, false)).doesNotThrowAnyException();
        assertThatCode(() -> CoachCategoryApplicationService.validatePendingLink(
                "ACTIVE", now.plusSeconds(1), now, false, false, false)).doesNotThrowAnyException();
    }

    @Test
    void unknownLinkStatusFailsClosed() {
        assertThatThrownBy(() -> CoachCategoryApplicationService.validatePendingLink(
                "UNKNOWN", now.plusSeconds(1), now, true, true, true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("COACH_PENDING_LINK_INVALID"));
    }
}

package br.com.dnafutsal.backend.identity.api;

import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.application.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPasswordResetControllerTest {
    @Test
    void returnsLinkToAdministratorWithoutTriggeringEmailFlow() {
        PasswordResetService service = mock(PasswordResetService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        var adminId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();
        var expiresAt = Instant.parse("2026-11-06T21:30:00Z");
        var link = "https://dna.test/redefinir-senha?token=example";
        when(currentUser.userId()).thenReturn(adminId);
        when(service.generateForAdministrator("atleta@exemplo.com"))
                .thenReturn(new PasswordResetService.AdminResetLink(link, expiresAt, subjectId));
        var controller = new AdminPasswordResetController(service, currentUser);

        var response = controller.generate(new AdminPasswordResetRequest("atleta@exemplo.com"));

        verify(service).generateForAdministrator("atleta@exemplo.com");
        verify(currentUser).userId();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(link, response.getBody().link());
        assertEquals(expiresAt, response.getBody().expiresAt());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
    }

    @Test
    void exposesPermissionProbeWithoutCreatingLink() {
        var response = new AdminPasswordResetController(mock(PasswordResetService.class),
                mock(CurrentUserService.class)).checkAccess();
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}

package br.com.dnafutsal.backend.identity.api;

import br.com.dnafutsal.backend.identity.application.CurrentUserService;
import br.com.dnafutsal.backend.identity.application.PasswordResetService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A URL /api/v1/admin/** exige SCOPE_ADMIN em SecurityConfiguration. */
@RestController
@RequestMapping("/api/v1/admin/password-reset")
public class AdminPasswordResetController {
    private static final Logger log = LoggerFactory.getLogger(AdminPasswordResetController.class);
    private final PasswordResetService passwordReset;
    private final CurrentUserService currentUser;

    public AdminPasswordResetController(PasswordResetService passwordReset, CurrentUserService currentUser) {
        this.passwordReset = passwordReset;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<Void> checkAccess() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping
    public ResponseEntity<AdminPasswordResetLinkResponse> generate(
            @Valid @RequestBody AdminPasswordResetRequest request) {
        var adminId = currentUser.userId();
        var result = passwordReset.generateForAdministrator(request.email());
        // Sem e-mail, sem token e sem URL nos logs de auditoria.
        log.info("Admin password reset link generated: adminId={} targetUserId={}", adminId, result.userId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(new AdminPasswordResetLinkResponse(result.link(), result.expiresAt()));
    }
}

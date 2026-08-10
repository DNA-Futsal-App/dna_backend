package br.com.dnafutsal.backend.identity.api;

import br.com.dnafutsal.backend.identity.application.AuthenticationService;
import br.com.dnafutsal.backend.identity.application.ClientContext;
import br.com.dnafutsal.backend.identity.application.PasswordResetService;
import br.com.dnafutsal.backend.identity.application.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final MessageResponse RESET_ACCEPTED = new MessageResponse(
            "Se os dados estiverem cadastrados e o limite diário não tiver sido atingido, enviaremos as instruções por e-mail.");

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final PasswordResetService passwordReset;

    public AuthController(RegistrationService registration, AuthenticationService authentication,
                          PasswordResetService passwordReset) {
        this.registration = registration;
        this.authentication = authentication;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        registration.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Cadastro recebido. Confirme o e-mail para entrar no aplicativo."));
    }

    @GetMapping("/email-verification/confirm")
    MessageResponse confirmEmail(
            @RequestParam @NotBlank @Size(max = 200) String token) {
        registration.verifyEmail(token);
        return new MessageResponse("E-mail confirmado. Você já pode entrar no aplicativo.");
    }

    @PostMapping("/email-verification/resend")
    ResponseEntity<MessageResponse> resendEmailVerification(@Valid @RequestBody PasswordResetRequest request) {
        registration.resendVerification(request.login());
        return ResponseEntity.accepted().body(new MessageResponse(
                "Se a conta estiver pendente e dentro do limite diário, enviaremos um novo link por e-mail."));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authentication.login(request, clientContext(servletRequest));
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request,
                         HttpServletRequest servletRequest) {
        return authentication.refresh(request.refreshToken(), clientContext(servletRequest));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authentication.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    ResponseEntity<MessageResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordReset.request(request.login());
        return ResponseEntity.accepted().body(RESET_ACCEPTED);
    }

    @PostMapping("/password-reset/confirm")
    MessageResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordReset.confirm(request.token(), request.newPassword());
        return new MessageResponse("Senha alterada. Entre novamente em todos os seus dispositivos.");
    }

    private ClientContext clientContext(HttpServletRequest request) {
        return new ClientContext(request.getHeader("User-Agent"), request.getRemoteAddr());
    }
}

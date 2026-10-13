package br.com.dnafutsal.backend.identity.api;

import br.com.dnafutsal.backend.config.AppProperties;
import br.com.dnafutsal.backend.config.SecurityConfiguration;
import br.com.dnafutsal.backend.config.SecurityProperties;
import br.com.dnafutsal.backend.identity.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@WebMvcTest(AuthController.class)
@Import(SecurityConfiguration.class)
@org.springframework.boot.autoconfigure.ImportAutoConfiguration({
        org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class,
        org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration.class
})
@EnableConfigurationProperties({AppProperties.class, SecurityProperties.class})
class AccountDeletionWebTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    RegistrationService registration;
    @MockitoBean
    AuthenticationService authentication;
    @MockitoBean
    PasswordResetService passwordReset;
    @MockitoBean
    AccountDeletionService deletion;
    @MockitoBean
    CurrentUserService currentUser;
    @MockitoBean
    UserSecurityStateService securityStates;
    @MockitoBean
    org.springframework.cache.CacheManager cacheManager;
    @Autowired
    org.springframework.security.oauth2.jwt.JwtEncoder encoder;

    @Test
    void realJwtIsRejectedImmediatelyAfterSchedulingAndAfterCancellation() throws Exception {
        UUID userId = UUID.randomUUID();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("dna-futsal").subject(userId.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).claim("ver", 0L).build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader
                .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        String token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        when(currentUser.userId()).thenReturn(userId);
        when(securityStates.find(userId)).thenReturn(new UserSecurityState(0,
                br.com.dnafutsal.backend.identity.domain.UserStatus.ACTIVE));
        mvc.perform(post("/api/v1/auth/account-deletion").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"currentPassword\":\"password\"}"))
                .andExpect(status().isAccepted());
        when(securityStates.find(userId)).thenReturn(new UserSecurityState(1,
                br.com.dnafutsal.backend.identity.domain.UserStatus.PENDING_DELETION));
        mvc.perform(post("/api/v1/auth/account-deletion").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"currentPassword\":\"password\"}"))
                .andExpect(status().isUnauthorized());
        when(securityStates.find(userId)).thenReturn(new UserSecurityState(2,
                br.com.dnafutsal.backend.identity.domain.UserStatus.ACTIVE));
        mvc.perform(post("/api/v1/auth/account-deletion").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"currentPassword\":\"password\"}"))
                .andExpect(status().isUnauthorized());
        verify(deletion, times(1)).request(userId, "password");
    }

    @Test
    void deletionIsProtectedDespitePublicAuthPrefix() throws Exception {
        mvc.perform(post("/api/v1/auth/account-deletion").contentType("application/json")
                        .content("{\"currentPassword\":\"password\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(deletion);
    }

    @Test
    void acceptsAuthenticatedRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUser.userId()).thenReturn(userId);
        when(deletion.request(eq(userId), any())).thenReturn(new AccountDeletionResponse(
                "PENDING_DELETION", Instant.parse("2026-10-12T00:00:00Z"),
                Instant.parse("2026-10-22T00:00:00Z"), "Agendada"));
        mvc.perform(post("/api/v1/auth/account-deletion").with(jwt()).contentType("application/json")
                        .content("{\"currentPassword\":\"password\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.scheduledFor").value("2026-10-22T00:00:00Z"));
        verify(deletion).request(userId, "password");
    }

    @Test
    void rejectsMissingPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/account-deletion").with(jwt()).contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(deletion);
    }

    @Test
    void cancellationAcceptsCredentialsWithoutRevokedJwt() throws Exception {
        mvc.perform(post("/api/v1/auth/account-deletion/cancel").contentType("application/json")
                        .content("{\"login\":\"person@example.com\",\"currentPassword\":\"password\"}"))
                .andExpect(status().isNoContent());
        verify(deletion).cancel("person@example.com", "password");
    }
}

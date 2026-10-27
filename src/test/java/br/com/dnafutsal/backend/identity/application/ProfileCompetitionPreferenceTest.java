package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.identity.api.UpdateProfileRequest;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.RefreshSessionRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import br.com.dnafutsal.backend.sports.application.SportsPreferenceValidator;
import br.com.dnafutsal.backend.sports.domain.SportsCompetitionCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProfileCompetitionPreferenceTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final IdentityNormalizer normalizer = mock(IdentityNormalizer.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final SportsPreferenceValidator validator = mock(SportsPreferenceValidator.class);
    private final RegistrationService registration = mock(RegistrationService.class);

    @Test
    void oldClientEditingPersonalInformationPreservesExistingFemalePreference() {
        UserAccount user = new UserAccount("Pessoa", "teste@example.com", "+5511999999999",
                "hash", null, 926L, "20", "10", "11", SportsCompetitionCode.PAULISTA_FEMININO);
        user.activate(Instant.now());
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(passwords.matches("senha", "hash")).thenReturn(true);
        when(normalizer.email("teste@example.com")).thenReturn("teste@example.com");
        when(normalizer.phone("+5511999999999")).thenReturn("+5511999999999");
        when(normalizer.optionalId("20")).thenReturn("20");
        when(normalizer.optionalId("10")).thenReturn("10");
        when(normalizer.optionalId("11")).thenReturn("11");
        var service = new ProfileService(users, sessions, passwords, normalizer,
                registration, Clock.systemUTC(), validator);

        var request = new UpdateProfileRequest("Pessoa Atualizada", "teste@example.com",
                "+5511999999999", null, 926L, "20", "10", "11", "senha");
        var response = service.update(user.getId(), request);

        assertThat(response.competitionCode()).isEqualTo(SportsCompetitionCode.PAULISTA_FEMININO);
        assertThat(user.getName()).isEqualTo("Pessoa Atualizada");
        verifyNoInteractions(validator); // An unchanged sports selection never needs a FPFS request.
    }
}

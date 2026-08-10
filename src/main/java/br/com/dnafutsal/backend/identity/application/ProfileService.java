package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.identity.api.UpdateProfileRequest;
import br.com.dnafutsal.backend.identity.api.UserProfileResponse;
import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.infrastructure.RefreshSessionRepository;
import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ProfileService {

    private final UserAccountRepository users;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final IdentityNormalizer normalizer;
    private final RegistrationService registrationService;
    private final Clock clock;

    public ProfileService(UserAccountRepository users, RefreshSessionRepository refreshSessions,
                          PasswordEncoder passwordEncoder, IdentityNormalizer normalizer,
                          RegistrationService registrationService, Clock clock) {
        this.users = users;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
        this.normalizer = normalizer;
        this.registrationService = registrationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "user-profile", key = "#userId", sync = true)
    public UserProfileResponse get(UUID userId) {
        return UserProfileResponse.from(requireUser(userId));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "user-profile", key = "#userId"),
            @CacheEvict(cacheNames = "user-security", key = "#userId")
    })
    public UserProfileResponse update(UUID userId, UpdateProfileRequest request) {
        UserAccount user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw Errors.unauthorized("INVALID_CURRENT_PASSWORD", "A senha atual está incorreta.");
        }
        String email = normalizer.email(request.email());
        String phone = normalizer.phone(request.phone());
        if (users.existsByEmailAndIdNot(email, userId)) {
            throw Errors.conflict("EMAIL_ALREADY_REGISTERED", "Este e-mail já está cadastrado.");
        }
        if (users.existsByPhoneAndIdNot(phone, userId)) {
            throw Errors.conflict("PHONE_ALREADY_REGISTERED", "Este telefone já está cadastrado.");
        }
        boolean emailChanged = !user.getEmail().equals(email);
        user.updateProfile(request.name().trim(), email, phone, normalizer.instagram(request.childInstagram()),
                normalizer.optionalId(request.categoryId()), normalizer.optionalId(request.divisionId()),
                normalizer.optionalId(request.teamId()));
        if (emailChanged) {
            user.requireEmailVerification();
            refreshSessions.revokeAllByUserId(userId, clock.instant());
            registrationService.enqueueVerification(user);
        }
        return UserProfileResponse.from(user);
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> Errors.notFound("USER_NOT_FOUND", "Usuário não encontrado."));
    }
}

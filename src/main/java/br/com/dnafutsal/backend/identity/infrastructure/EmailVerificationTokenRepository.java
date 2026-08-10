package br.com.dnafutsal.backend.identity.infrastructure;

import br.com.dnafutsal.backend.identity.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant start);
}

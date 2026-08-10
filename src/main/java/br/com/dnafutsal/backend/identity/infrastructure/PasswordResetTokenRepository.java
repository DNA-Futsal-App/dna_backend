package br.com.dnafutsal.backend.identity.infrastructure;

import br.com.dnafutsal.backend.identity.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant start);

    @Modifying
    @Query("update PasswordResetToken p set p.usedAt = :now where p.userId = :userId and p.usedAt is null")
    int invalidateAllByUserId(UUID userId, Instant now);
}

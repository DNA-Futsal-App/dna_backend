package br.com.dnafutsal.backend.identity.infrastructure;

import br.com.dnafutsal.backend.identity.domain.UserAccount;
import br.com.dnafutsal.backend.identity.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    @Query("""
                    select distinct user.followedEventId
                    from UserAccount user
                    where user.status = :status
                    and user.followedEventId is not null
            """)
    List<Long> findDistinctFollowedEventIdsByStatus(@Param("status") UserStatus status);
}

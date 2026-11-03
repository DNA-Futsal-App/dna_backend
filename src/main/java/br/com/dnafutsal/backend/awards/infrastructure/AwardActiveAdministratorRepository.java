package br.com.dnafutsal.backend.awards.infrastructure;

import br.com.dnafutsal.backend.identity.domain.*;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.UUID;

public interface AwardActiveAdministratorRepository extends Repository<UserAccount, UUID> {
    List<UserAccount> findByRoleAndStatusOrderByNameAsc(UserRole role, UserStatus status);
}

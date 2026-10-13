package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.identity.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserSecurityStateService {

    private final UserAccountRepository repository;

    public UserSecurityStateService(UserAccountRepository repository) {
        this.repository = repository;
    }

    public UserSecurityState find(UUID userId) {
        return repository.findById(userId)
                .map(user -> new UserSecurityState(user.getTokenVersion(), user.getStatus()))
                .orElse(null);
    }
}

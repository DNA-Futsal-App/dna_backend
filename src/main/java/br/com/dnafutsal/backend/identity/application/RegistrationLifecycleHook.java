package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.identity.api.RegisterRequest;
import br.com.dnafutsal.backend.identity.domain.UserAccount;

public interface RegistrationLifecycleHook {

    default void afterRegistration(
            UserAccount user,
            RegisterRequest request
    ) {
    }

    default void afterEmailVerified(
            UserAccount user
    ) {
    }
}

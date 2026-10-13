package br.com.dnafutsal.backend.identity.domain;

public enum UserStatus {
    PENDING_EMAIL_VERIFICATION,
    ACTIVE,
    LOCKED,
    PENDING_DELETION,
    DELETING,
    DELETED
}

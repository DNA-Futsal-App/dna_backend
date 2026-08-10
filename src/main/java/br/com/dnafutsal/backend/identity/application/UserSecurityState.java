package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.identity.domain.UserStatus;

import java.io.Serializable;

public record UserSecurityState(long tokenVersion, UserStatus status) implements Serializable {
}

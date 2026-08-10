package br.com.dnafutsal.backend.identity.api;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken,
        UserProfileResponse user
) {
}

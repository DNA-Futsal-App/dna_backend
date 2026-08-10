package br.com.dnafutsal.backend.identity.application;

import br.com.dnafutsal.backend.common.PhoneNormalizer;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class IdentityNormalizer {

    private final PhoneNormalizer phoneNormalizer;

    public IdentityNormalizer(PhoneNormalizer phoneNormalizer) {
        this.phoneNormalizer = phoneNormalizer;
    }

    public String email(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String phone(String phone) {
        return phoneNormalizer.normalize(phone);
    }

    public String instagram(String instagram) {
        if (instagram == null || instagram.isBlank()) {
            return null;
        }
        String trimmed = instagram.trim();
        return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
    }

    public String optionalId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

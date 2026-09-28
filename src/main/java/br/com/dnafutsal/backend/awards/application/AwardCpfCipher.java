package br.com.dnafutsal.backend.awards.application;

import br.com.dnafutsal.backend.common.Errors;
import br.com.dnafutsal.backend.config.AwardRegistrationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Component
public class AwardCpfCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AwardRegistrationProperties properties;
    private final SecureRandom random;

    private volatile SecretKeySpec cachedKey;

    public AwardCpfCipher(
            AwardRegistrationProperties properties
    ) {
        this.properties = properties;
        this.random = new SecureRandom();
    }

    public String encrypt(
            String cpf
    ) {
        byte[] iv =
                new byte[IV_LENGTH];

        random.nextBytes(
                iv
        );

        try {
            Cipher cipher =
                    Cipher.getInstance(
                            "AES/GCM/NoPadding"
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key(),
                    new GCMParameterSpec(
                            TAG_LENGTH_BITS,
                            iv
                    )
            );

            byte[] encrypted =
                    cipher.doFinal(
                            cpf.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            byte[] payload =
                    ByteBuffer.allocate(
                                    iv.length
                                            + encrypted.length
                            )
                            .put(iv)
                            .put(encrypted)
                            .array();

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            payload
                    );
        } catch (GeneralSecurityException exception) {
            throw Errors.dependencyUnavailable(
                    "AWARD_CPF_ENCRYPTION_FAILED",
                    "Não foi possível proteger o CPF do representante.",
                    exception,
                    Map.of()
            );
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec current =
                cachedKey;

        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cachedKey != null) {
                return cachedKey;
            }

            String encoded =
                    properties.cpfEncryptionKeyBase64();

            if (encoded == null
                    || encoded.isBlank()) {
                throw Errors.dependencyUnavailable(
                        "AWARD_CPF_ENCRYPTION_NOT_CONFIGURED",
                        "A chave de proteção do CPF não foi configurada."
                );
            }

            final byte[] decoded;

            try {
                decoded =
                        Base64.getDecoder()
                                .decode(
                                        encoded.trim()
                                );
            } catch (IllegalArgumentException exception) {
                throw Errors.dependencyUnavailable(
                        "AWARD_CPF_ENCRYPTION_NOT_CONFIGURED",
                        "A chave de proteção do CPF está em formato inválido.",
                        exception,
                        Map.of()
                );
            }

            if (decoded.length != 32) {
                throw Errors.dependencyUnavailable(
                        "AWARD_CPF_ENCRYPTION_NOT_CONFIGURED",
                        "A chave de proteção do CPF deve possuir 32 bytes."
                );
            }

            cachedKey =
                    new SecretKeySpec(
                            decoded,
                            "AES"
                    );

            return cachedKey;
        }
    }
}
